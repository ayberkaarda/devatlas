//! Live interoperability tests against a real, running server.
//!
//! Every other test in this crate proves the engine against `wiremock`, a fake
//! that can only ever agree with itself about what an HTTP response looks
//! like. These tests point the real `Engine` at the real server this project
//! ships, so that a disagreement between the two sides of the protocol -- a
//! translation field the client reads under the wrong name, a `Range`
//! response the real servlet container shapes differently than a mock, a
//! status code the client does not expect -- surfaces here instead of after a
//! release.
//!
//! Every test is `#[ignore]`d: they need a server reachable on the network and
//! are not part of the fast local suite. Run them with:
//!
//! ```text
//! BYTELORE_API_BASE_URL=http://localhost:18080/api/v1 \
//!     cargo test --lib live_ -- --ignored --test-threads=1 --nocapture
//! ```
//!
//! They assume the server is seeded with the `angular-path` track described in
//! the sync protocol's worked examples: two modules, four `LESSON` entities and
//! one `MIND_MAP`, with the `signals-and-reactivity` lesson carrying `tr` and
//! `fr` translations.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use crate::db::Db;
use crate::engine::{Engine, EngineConfig};
use crate::events::testing::RecordingSink;
use crate::events::EventSink;
use crate::http::{ContentClient, ManifestFetch, PackageRequest, API_BASE_URL_ENV};
use crate::manifest::TrackManifest;
use crate::model::QueueState;
use crate::queue::{self, QueueRow};
use crate::replica;
use crate::store;

/// Reads the live server's base URL, or panics with the exact command that
/// brings one up.
///
/// A silent fallback to the crate's default local port would surface as a
/// confusing `NETWORK_UNAVAILABLE` deep inside the engine -- indistinguishable
/// from a genuinely offline server -- instead of the actual problem, which is
/// that nothing was started.
fn required_base_url() -> String {
    std::env::var(API_BASE_URL_ENV)
        .ok()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| {
            panic!(
                "\n\n{API_BASE_URL_ENV} is not set, so there is no live server to test against.\n\
                 Start one first:\n\n  \
                 cd server && ./mvnw -q spring-boot:test-run \\\n    \
                 -Dspring-boot.run.main-class=dev.bytelore.server.TestServerApplication \\\n    \
                 -Dspring-boot.run.profiles=test \\\n    \
                 -Dspring-boot.run.arguments=--server.port=18080\n\n\
                 then re-run with:\n\n  \
                 BYTELORE_API_BASE_URL=http://localhost:18080/api/v1 \\\n    \
                 cargo test --lib live_ -- --ignored --test-threads=1 --nocapture\n"
            )
        })
}

/// A store on a real file, with a real `partials/` directory beside it --
/// deliberately not `open_in_memory()`. The WAL file and the partial-file
/// lifecycle on an actual filesystem are part of what these tests exercise.
struct LiveHarness {
    _dir: tempfile::TempDir,
    partials: PathBuf,
    db: Arc<Db>,
    client: ContentClient,
    engine: Arc<Engine>,
    sink: Arc<RecordingSink>,
}

impl LiveHarness {
    fn new() -> Self {
        let base_url = required_base_url();
        let dir = tempfile::tempdir().expect("temp dir");
        let partials = dir.path().join("partials");
        std::fs::create_dir_all(&partials).expect("create partials dir");

        let connection =
            store::open(&dir.path().join("bytelore-live.db")).expect("open a real sqlite store");
        let db = Arc::new(Db::new(connection));
        let client = ContentClient::new(base_url);
        let sink = Arc::new(RecordingSink::default());
        let engine = Engine::new(
            Arc::clone(&db),
            client.clone(),
            partials.clone(),
            Arc::clone(&sink) as Arc<dyn EventSink>,
            EngineConfig {
                max_attempts: 3,
                base_backoff: Duration::from_millis(100),
                concurrency: 3,
                progress_interval: Duration::from_millis(100),
                max_offline_recoveries: 1,
            },
        );

        Self {
            _dir: dir,
            partials,
            db,
            client,
            engine,
            sink,
        }
    }
}

/// The one published track's id, from a fresh (uncached) catalog fetch.
async fn fetch_track_id(client: &ContentClient) -> String {
    match client.fetch_catalog(None).await.expect("fetch catalog") {
        ManifestFetch::Fetched { value, .. } => value
            .tracks
            .first()
            .unwrap_or_else(|| {
                panic!("the live server's catalog carries no track; is the seed data loaded?")
            })
            .track_id
            .clone(),
        ManifestFetch::NotModified => {
            panic!("a fresh request carrying no If-None-Match cannot be answered 304")
        }
    }
}

/// The full track manifest, from a fresh (uncached) fetch.
async fn fetch_manifest(client: &ContentClient) -> TrackManifest {
    let track_id = fetch_track_id(client).await;
    match client
        .fetch_track_manifest(&track_id, None)
        .await
        .expect("fetch track manifest")
    {
        ManifestFetch::Fetched { value, .. } => value,
        ManifestFetch::NotModified => {
            panic!("a fresh request carrying no If-None-Match cannot be answered 304")
        }
    }
}

/// One diagnostic line per row, so a failure names exactly what happened
/// instead of just that something did not reach `DONE`.
fn describe_rows(rows: &[&QueueRow]) -> String {
    rows.iter()
        .map(|row| {
            format!(
                "  entity_id={} type={:?} state={:?} attempt={} error_code={:?}",
                row.entity_id, row.entity_type, row.state, row.attempt, row.error_code
            )
        })
        .collect::<Vec<_>>()
        .join("\n")
}

/// The tail of the recorded progress events, for the same reason: a batch
/// failure is undiagnosable from the final row alone once several entities
/// have been transferred concurrently.
fn describe_recent_events(sink: &RecordingSink, limit: usize) -> String {
    let events = sink.progress.lock().expect("progress lock");
    let start = events.len().saturating_sub(limit);
    events[start..]
        .iter()
        .map(|event| {
            format!(
                "  entity_id={} state={:?} attempt={} error_code={:?}",
                event.entity_id, event.state, event.attempt, event.error_code
            )
        })
        .collect::<Vec<_>>()
        .join("\n")
}

/// The full track, all five entities, end to end against the real server.
///
/// This is the test the translation bug described at the top of the module
/// would have caught: `content_translations` rows are asserted to actually
/// carry a body, not merely to exist.
#[tokio::test]
#[ignore]
async fn live_full_track_download_lands_in_the_replica() {
    let harness = LiveHarness::new();

    let manifest = fetch_manifest(&harness.client).await;
    assert_eq!(
        manifest.entities.len(),
        5,
        "this test is written against the documented seed: four lessons and one mind map"
    );

    harness
        .db
        .transaction(|tx| replica::apply_track_manifest(tx, &manifest, None))
        .expect("seed structure from the manifest");

    let batch_id = "live-full-track";
    for entity in &manifest.entities {
        harness
            .db
            .with(|c| {
                queue::enqueue(
                    c,
                    &entity.entity_id,
                    entity.entity_type,
                    Some(&manifest.track_id),
                    batch_id,
                    entity.content_version,
                    &entity.sha256,
                    entity.size_bytes,
                )
            })
            .expect("enqueue entity");
    }

    harness.engine.pump().await;

    let rows = harness.db.with(queue::all).expect("read queue");
    let not_done: Vec<&QueueRow> = rows
        .iter()
        .filter(|row| row.state != QueueState::Done)
        .collect();
    assert!(
        not_done.is_empty(),
        "not every entity reached DONE:\n{}\n\nrecent progress events:\n{}",
        describe_rows(&not_done),
        describe_recent_events(&harness.sink, 40),
    );
    assert_eq!(rows.len(), 5, "one queue row per manifest entity");

    // Every lesson's stored digest matches what the manifest advertised for
    // it -- the bookkeeping half of "the bytes verified", the cryptographic
    // half being that DONE was reachable at all only because verify::digests_match
    // agreed first.
    for entity in manifest
        .entities
        .iter()
        .filter(|e| e.entity_type == crate::model::EntityType::Lesson)
    {
        let entity_id = entity.entity_id.clone();
        let stored: Option<String> = harness
            .db
            .with(move |c| {
                c.query_row(
                    "SELECT sha256 FROM lessons WHERE lesson_id = ?1;",
                    [&entity_id],
                    |row| row.get(0),
                )
            })
            .expect("query lesson digest");
        assert_eq!(
            stored.as_deref(),
            Some(entity.sha256.as_str()),
            "the stored digest for {} must equal the manifest's",
            entity.entity_id
        );
    }

    let lesson_bodies: Vec<Option<String>> = harness
        .db
        .with(|c| {
            let mut statement = c.prepare(
                "SELECT body_markdown FROM lessons WHERE content_version IS NOT NULL ORDER BY lesson_id;",
            )?;
            let rows = statement.query_map([], |row| row.get::<_, Option<String>>(0))?;
            rows.collect()
        })
        .expect("query lesson bodies");
    assert_eq!(
        lesson_bodies.len(),
        4,
        "expected four downloaded lessons, found {}",
        lesson_bodies.len()
    );
    for body in &lesson_bodies {
        assert!(
            !body.as_deref().unwrap_or("").trim().is_empty(),
            "a downloaded lesson must not land with an empty body"
        );
    }

    let mind_map_root: Option<String> = harness
        .db
        .with(|c| {
            c.query_row(
                "SELECT root_json FROM mind_maps WHERE content_version IS NOT NULL;",
                [],
                |row| row.get(0),
            )
        })
        .expect("query mind map root");
    let mind_map_root = mind_map_root.expect("the mind map must be stored with a root");
    assert!(
        !mind_map_root.trim().is_empty() && mind_map_root != "null",
        "the mind map's root must not be empty"
    );

    // The bug this suite exists to catch: a translation row that exists but
    // whose body silently ended up NULL, which every earlier test missed
    // because the field was optional and "absent" looked exactly like
    // "succeeded" everywhere except here.
    let (translation_count, null_bodies): (i64, i64) = harness
        .db
        .with(|c| {
            c.query_row(
                "SELECT count(*), sum(CASE WHEN body IS NULL THEN 1 ELSE 0 END) FROM content_translations;",
                [],
                |row| Ok((row.get(0)?, row.get::<_, Option<i64>>(1)?.unwrap_or(0))),
            )
        })
        .expect("query translations");
    assert_eq!(
        null_bodies, 0,
        "no content_translations row may carry a NULL body"
    );
    assert!(
        translation_count >= 2,
        "expected at least two translation rows (fr + tr for signals-and-reactivity), found {translation_count}"
    );

    let leftover: Vec<_> = std::fs::read_dir(&harness.partials)
        .expect("read partials dir")
        .collect();
    assert!(
        leftover.is_empty(),
        "partials/ must be empty once every entity verified; found {} file(s)",
        leftover.len()
    );
}

/// Resuming a real, interrupted download against the real server: `206`,
/// `Content-Range` and `If-Match`, none of which a mock server can prove.
#[tokio::test]
#[ignore]
async fn live_resume_from_a_partial_uses_range_and_if_match() {
    let harness = LiveHarness::new();
    let manifest = fetch_manifest(&harness.client).await;
    harness
        .db
        .transaction(|tx| replica::apply_track_manifest(tx, &manifest, None))
        .expect("seed structure from the manifest");

    let lesson = manifest
        .lessons()
        .find(|lesson| lesson.slug == "signals-and-reactivity")
        .unwrap_or_else(|| {
            panic!("seed lesson 'signals-and-reactivity' not found in the live manifest")
        });
    let entity = manifest
        .entity(&lesson.lesson_id)
        .unwrap_or_else(|| {
            panic!(
                "entity for lesson {} missing from the manifest",
                lesson.lesson_id
            )
        })
        .clone();

    // Fetch the package once, raw, into a file the engine never looks at, so
    // the test has exact reference bytes to split in half.
    let reference_path = harness.partials.join("reference.bin");
    harness
        .client
        .download_package(PackageRequest {
            entity_type: entity.entity_type,
            entity_id: &entity.entity_id,
            content_version: entity.content_version,
            expected_sha256: &entity.sha256,
            destination: &reference_path,
            progress: None,
        })
        .await
        .expect("raw fetch of the reference package");
    let full_bytes = std::fs::read(&reference_path).expect("read reference bytes");
    assert_eq!(
        full_bytes.len() as i64,
        entity.size_bytes,
        "the manifest's size_bytes must equal what the server actually served"
    );
    std::fs::remove_file(&reference_path).expect("remove the reference file");

    let half = full_bytes.len() / 2;
    assert!(half > 0, "the reference package must not be empty");

    // Mirrors the engine's own (private) partial-file naming convention --
    // `<entity_id>.v<content_version>.part` -- so this partial lands exactly
    // where the engine looks for it. If that convention ever changes, this
    // test fails loudly rather than silently downloading from zero.
    let partial_path = harness.partials.join(format!(
        "{}.v{}.part",
        entity.entity_id, entity.content_version
    ));
    std::fs::write(&partial_path, &full_bytes[..half]).expect("seed the partial file");

    harness
        .db
        .with(|c| {
            queue::enqueue(
                c,
                &entity.entity_id,
                entity.entity_type,
                Some(&manifest.track_id),
                "live-resume",
                entity.content_version,
                &entity.sha256,
                entity.size_bytes,
            )
        })
        .expect("enqueue");

    let row = harness
        .db
        .with(|c| queue::find(c, &entity.entity_id))
        .expect("find")
        .expect("row after enqueue");
    harness.engine.process(row).await;

    let row = harness
        .db
        .with(|c| queue::find(c, &entity.entity_id))
        .expect("find")
        .expect("row after processing");
    assert_eq!(
        row.state,
        QueueState::Done,
        "a real 206/Content-Range/If-Match resume must complete: {row:?}"
    );
    // The queue's attempt counter is zero-based and only advances on a
    // consumed retry (a wrong or incomplete answer); a resume that completes
    // cleanly on its first pass never calls consume_attempt, so it stays 0.
    assert_eq!(
        row.attempt, 0,
        "a resume that completes cleanly consumes no retry attempt"
    );
    assert!(
        !partial_path.exists(),
        "there is no permanent package file once a transfer verifies"
    );

    let stored_body: Option<String> = harness
        .db
        .with({
            let lesson_id = entity.entity_id.clone();
            move |c| {
                c.query_row(
                    "SELECT body_markdown FROM lessons WHERE lesson_id = ?1;",
                    [&lesson_id],
                    |row| row.get(0),
                )
            }
        })
        .expect("query lesson body");
    assert!(
        !stored_body.as_deref().unwrap_or("").trim().is_empty(),
        "the resumed lesson must land with its body, not an empty one"
    );
}

/// The discovery path a freshly installed client actually walks: refresh, then
/// refresh the track it just learned about.
///
/// Every other test in this file seeds the replica by applying a manifest it
/// fetched itself, which means the store is already full before the code under
/// test runs. This one starts from an empty store and drives
/// `commands::refresh_library` instead, so the two halves of the contract are
/// both exercised against the real server: a refresh with no track applies only
/// the catalog, and a refresh naming a track brings that track's structure in.
#[tokio::test]
#[ignore]
async fn live_refresh_fills_a_fresh_store_from_the_catalog_and_then_the_manifest() {
    /// The seeded `angular-path` track, as the sync protocol's worked examples
    /// describe it.
    const SEEDED_TRACK: &str = "019205a0-1000-7000-8000-000000000001";

    let harness = LiveHarness::new();
    let count = |table: &'static str| {
        let sql = format!("SELECT count(*) FROM {table};");
        harness
            .db
            .with(move |c| c.query_row(&sql, [], |row| row.get::<_, i64>(0)))
            .expect("count rows")
    };

    assert_eq!(
        (count("tracks"), count("lessons")),
        (0, 0),
        "this test only means anything from an empty store"
    );

    let summary = crate::commands::refresh_library(&harness.engine, None)
        .await
        .expect("refresh with no track");
    assert_eq!(
        summary.checked_tracks, 0,
        "with nothing held locally there is no track to compare"
    );
    assert!(
        count("tracks") >= 1,
        "the catalog must make the published track visible"
    );
    assert_eq!(
        (count("modules"), count("lessons"), count("mind_maps")),
        (0, 0, 0),
        "the catalog carries no structure, and a refresh does not go looking for it"
    );

    let summary = crate::commands::refresh_library(&harness.engine, Some(SEEDED_TRACK.to_string()))
        .await
        .unwrap_or_else(|error| {
            panic!("refresh of the seeded track {SEEDED_TRACK} failed: {error:?}")
        });
    assert_eq!(summary.checked_tracks, 1);

    assert_eq!(
        (count("modules"), count("lessons"), count("mind_maps")),
        (2, 4, 1),
        "the seeded track publishes two modules, four lessons and one mind map"
    );

    let entities: i64 = harness
        .db
        .with(|c| {
            c.query_row(
                "SELECT (SELECT count(*) FROM lessons WHERE manifest_content_version IS NOT NULL)
                      + (SELECT count(*) FROM mind_maps WHERE manifest_content_version IS NOT NULL);",
                [],
                |row| row.get(0),
            )
        })
        .expect("count manifest entities");
    assert_eq!(
        entities, 5,
        "every manifest entity must land with a version the engine can download"
    );
}

/// A manifest fetched with its own `ETag` must revalidate as `304`.
#[tokio::test]
#[ignore]
async fn live_manifest_revalidation_returns_304() {
    let harness = LiveHarness::new();
    let track_id = fetch_track_id(&harness.client).await;

    let etag = match harness
        .client
        .fetch_track_manifest(&track_id, None)
        .await
        .expect("first fetch")
    {
        ManifestFetch::Fetched { etag, .. } => {
            etag.expect("the track manifest endpoint must publish an ETag")
        }
        ManifestFetch::NotModified => {
            panic!("a fresh request carrying no If-None-Match cannot be answered 304")
        }
    };

    let revalidated = harness
        .client
        .fetch_track_manifest(&track_id, Some(&etag))
        .await
        .expect("second fetch, with the etag");
    assert!(
        matches!(revalidated, ManifestFetch::NotModified),
        "requesting with the manifest's own ETag must be answered 304, got {revalidated:?}"
    );
}

/// Enqueuing a version the server has already superseded must make the engine
/// re-plan against the manifest and finish at the version the server actually
/// holds, per `CONTENT_VERSION_SUPERSEDED` in the sync protocol.
#[tokio::test]
#[ignore]
async fn live_superseded_version_is_replanned() {
    let harness = LiveHarness::new();
    let manifest = fetch_manifest(&harness.client).await;
    harness
        .db
        .transaction(|tx| replica::apply_track_manifest(tx, &manifest, None))
        .expect("seed structure from the manifest");

    let lesson = manifest
        .lessons()
        .find(|lesson| lesson.slug == "signals-and-reactivity")
        .unwrap_or_else(|| {
            panic!("seed lesson 'signals-and-reactivity' not found in the live manifest")
        });
    let current = manifest
        .entity(&lesson.lesson_id)
        .unwrap_or_else(|| {
            panic!(
                "entity for lesson {} missing from the manifest",
                lesson.lesson_id
            )
        })
        .clone();
    assert!(
        current.content_version >= 2,
        "this test requires the seed lesson to already be revised past its first \
         published version; the live server reports content_version {}",
        current.content_version
    );

    // Enqueue a version the server has already superseded. It answers 409
    // before any body is sent, so the digest and size below are never
    // checked against real bytes -- they exist only to satisfy the queue
    // row's schema.
    harness
        .db
        .with(|c| {
            queue::enqueue(
                c,
                &current.entity_id,
                current.entity_type,
                Some(&manifest.track_id),
                "live-superseded",
                1,
                &"0".repeat(64),
                1,
            )
        })
        .expect("enqueue a stale version");

    harness.engine.pump().await;

    let row = harness
        .db
        .with(|c| queue::find(c, &current.entity_id))
        .expect("find")
        .expect("row after pumping");
    assert_eq!(
        row.state,
        QueueState::Done,
        "the engine must re-plan against the current version and finish: {row:?}\n\n\
         recent progress events:\n{}",
        describe_recent_events(&harness.sink, 20)
    );
    assert_eq!(
        row.content_version, current.content_version,
        "re-planning must land on the version the server actually holds"
    );
    assert_eq!(
        row.sha256, current.sha256,
        "the stored digest must be the current version's, not the stale one that was enqueued"
    );
}
