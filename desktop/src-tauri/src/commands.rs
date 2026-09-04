//! The `invoke` surface.
//!
//! Payloads are `camelCase` here and `snake_case` on the REST side. That is
//! deliberate: the two surfaces are separate, forcing one convention on both
//! would mean fighting one framework or the other, and the mapping happens in
//! exactly one place per shape.
//!
//! Two rules hold across everything below. No command performs authenticated
//! HTTP -- manifests and packages are anonymous and the session stays in the
//! UI layer. And reads never fall back to the network: a `library_*` command
//! answers from the store or fails, so a caller always knows which source
//! answered and an offline screen cannot quietly become an online one.

use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::db::{now_iso, Db};
use crate::delta::{self, DeltaSummary};
use crate::engine::Engine;
use crate::error::{codes, parse_uuid, CommandError};
use crate::http::{ManifestFetch, TransferError};
use crate::manifest::TrackManifest;
use crate::model::{
    availability_of, Availability, DownloadScope, EntityType, PauseReason, QueueState, ScopeKind,
    SyncState,
};
use crate::queue::{self, EnqueueOutcome};
use crate::replica;
use crate::settings::{self, AppSettings, SettingsPatch, StoredSession};

pub struct AppState {
    pub engine: Arc<Engine>,
}

impl AppState {
    fn engine(&self) -> Arc<Engine> {
        Arc::clone(&self.engine)
    }

    fn db(&self) -> &Arc<Db> {
        self.engine.db()
    }
}

type Command<T> = Result<T, CommandError>;

// ---------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------

/// How much of a track is held locally.
///
/// A track is not one entity, so it needs a coarser answer than a lesson does:
/// "some of it" is a real state and the per-entity vocabulary has no word for
/// it.
#[derive(Debug, Clone, Copy, Serialize, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TrackAvailability {
    NotDownloaded,
    PartiallyDownloaded,
    Downloaded,
    UpdateAvailable,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackSummary {
    pub track_id: String,
    pub slug: String,
    /// Already resolved to the active locale, with English as the fallback.
    /// Resolution happens here rather than in the UI so that both platform
    /// implementations return an identically shaped object.
    pub title: String,
    pub description: Option<String>,
    pub icon: Option<String>,
    pub content_version: i64,
    pub lesson_count: i64,
    pub downloaded_lesson_count: i64,
    pub total_size_bytes: i64,
    pub downloaded_size_bytes: i64,
    pub availability: TrackAvailability,
    pub update_available_count: i64,
    pub withdrawn_count: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackDetail {
    pub track_id: String,
    pub slug: String,
    pub title: String,
    pub description: Option<String>,
    pub icon: Option<String>,
    pub content_version: i64,
    pub modules: Vec<ModuleDetail>,
    pub mind_map: Option<MindMapSummary>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModuleDetail {
    pub module_id: String,
    pub title: String,
    pub order: i64,
    pub estimated_minutes: Option<i64>,
    pub lessons: Vec<LessonSummary>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LessonSummary {
    pub lesson_id: String,
    pub slug: String,
    pub title: String,
    pub difficulty: Option<String>,
    pub estimated_minutes: Option<i64>,
    pub order: i64,
    pub availability: Availability,
    pub content_version: Option<i64>,
    pub size_bytes: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MindMapSummary {
    pub mind_map_id: String,
    pub availability: Availability,
    pub content_version: Option<i64>,
    pub size_bytes: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Lesson {
    pub lesson_id: String,
    pub track_id: String,
    pub module_id: String,
    pub slug: String,
    pub title: String,
    pub body_markdown: String,
    pub difficulty: Option<String>,
    pub estimated_minutes: Option<i64>,
    pub order: i64,
    pub content_version: i64,
    pub locale: String,
    /// True when the requested locale had no translation and English was used.
    pub is_fallback: bool,
    pub code_examples: Vec<CodeExample>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CodeExample {
    pub caption: Option<String>,
    pub code: String,
    pub language: String,
    pub order: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MindMap {
    pub mind_map_id: String,
    pub track_id: String,
    pub content_version: i64,
    /// The node tree exactly as the package carried it.
    pub root: serde_json::Value,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BatchHandle {
    pub batch_id: String,
    /// Only what actually entered the queue: a batch's denominator has to match
    /// what the user will watch happen.
    pub queued_entities: i64,
    pub skipped_entities: i64,
    pub total_bytes: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CancelSummary {
    pub cancelled_entities: i64,
    pub kept_entities: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeleteSummary {
    pub removed_entities: i64,
    pub freed_bytes: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct QueueEntry {
    pub entity_id: String,
    pub entity_type: EntityType,
    pub title: Option<String>,
    pub batch_id: String,
    pub state: QueueState,
    pub received_bytes: i64,
    pub total_bytes: i64,
    pub attempt: i64,
    pub pause_reason: Option<PauseReason>,
    pub error_code: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProgressEntry {
    pub lesson_id: String,
    /// Null is a value, not an absence: it records "explicitly marked
    /// incomplete" and has to survive a sync round trip as such.
    pub completed_at: Option<String>,
    pub client_updated_at: String,
    pub sync_state: SyncState,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProgressResult {
    pub lesson_id: String,
    /// `APPLIED`, `STALE` or `REJECTED`.
    pub status: String,
    #[serde(default)]
    pub code: Option<String>,
    /// What the server holds after processing. A clamped or superseded row
    /// takes this value, so a device with a skewed clock converges instead of
    /// arguing forever.
    #[serde(default)]
    pub server_client_updated_at: Option<String>,
    /// The server's completion state, when the caller has it. Absent leaves the
    /// local value alone.
    #[serde(default)]
    pub completed_at: Option<Option<String>>,
}

// ---------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------

#[tauri::command]
pub fn library_list_tracks(state: tauri::State<'_, AppState>) -> Command<Vec<TrackSummary>> {
    let db = state.db();
    db.with(|connection| {
        let locale = settings::active_locale(connection)?;
        let mut statement = connection.prepare(
            "SELECT t.track_id, t.slug, t.title, t.description, t.icon, t.content_version,
                    t.lesson_count, t.total_size_bytes,
                    (SELECT count(*) FROM lessons l
                      WHERE l.track_id = t.track_id AND l.content_version IS NOT NULL),
                    (SELECT coalesce(sum(l.size_bytes), 0) FROM lessons l
                      WHERE l.track_id = t.track_id AND l.content_version IS NOT NULL),
                    (SELECT count(*) FROM lessons l
                      WHERE l.track_id = t.track_id AND l.content_version IS NOT NULL
                        AND l.withdrawn_at IS NULL
                        AND l.manifest_content_version > l.content_version),
                    (SELECT count(*) FROM lessons l
                      WHERE l.track_id = t.track_id AND l.withdrawn_at IS NOT NULL)
             FROM tracks t ORDER BY t.title;",
        )?;
        let rows = statement.query_map([], |row| {
            let track_id: String = row.get(0)?;
            let lesson_count: i64 = row.get(6)?;
            let downloaded: i64 = row.get(8)?;
            let updates: i64 = row.get(10)?;
            Ok(TrackSummary {
                slug: row.get(1)?,
                title: row.get(2)?,
                description: row.get(3)?,
                icon: row.get(4)?,
                content_version: row.get(5)?,
                lesson_count,
                total_size_bytes: row.get(7)?,
                downloaded_lesson_count: downloaded,
                downloaded_size_bytes: row.get(9)?,
                availability: track_availability(lesson_count, downloaded, updates),
                update_available_count: updates,
                withdrawn_count: row.get(11)?,
                track_id,
            })
        })?;

        let mut summaries = Vec::new();
        for summary in rows {
            let mut summary = summary?;
            if let Some((title, body)) =
                translation(connection, "TRACK", &summary.track_id, &locale)?
            {
                if let Some(title) = title {
                    summary.title = title;
                }
                if body.is_some() {
                    summary.description = body;
                }
            }
            summaries.push(summary);
        }
        Ok(summaries)
    })
}

fn track_availability(lesson_count: i64, downloaded: i64, updates: i64) -> TrackAvailability {
    if downloaded == 0 {
        TrackAvailability::NotDownloaded
    } else if updates > 0 {
        TrackAvailability::UpdateAvailable
    } else if downloaded >= lesson_count {
        TrackAvailability::Downloaded
    } else {
        TrackAvailability::PartiallyDownloaded
    }
}

#[tauri::command]
pub fn library_get_track(
    state: tauri::State<'_, AppState>,
    track_id: String,
) -> Command<TrackDetail> {
    parse_uuid("trackId", &track_id)?;
    let db = state.db();
    db.with(move |connection| {
        let locale = settings::active_locale(connection)?;
        let mut track = connection.query_row(
            "SELECT track_id, slug, title, description, icon, content_version
             FROM tracks WHERE track_id = ?1;",
            [&track_id],
            |row| {
                Ok(TrackDetail {
                    track_id: row.get(0)?,
                    slug: row.get(1)?,
                    title: row.get(2)?,
                    description: row.get(3)?,
                    icon: row.get(4)?,
                    content_version: row.get(5)?,
                    modules: Vec::new(),
                    mind_map: None,
                })
            },
        )?;
        if let Some((title, body)) = translation(connection, "TRACK", &track.track_id, &locale)? {
            if let Some(title) = title {
                track.title = title;
            }
            if body.is_some() {
                track.description = body;
            }
        }

        let mut modules = connection.prepare(
            "SELECT module_id, title, ordinal, estimated_minutes FROM modules
             WHERE track_id = ?1 ORDER BY ordinal, module_id;",
        )?;
        let module_rows: Vec<ModuleDetail> = modules
            .query_map([&track_id], |row| {
                Ok(ModuleDetail {
                    module_id: row.get(0)?,
                    title: row.get(1)?,
                    order: row.get(2)?,
                    estimated_minutes: row.get(3)?,
                    lessons: Vec::new(),
                })
            })?
            .collect::<rusqlite::Result<_>>()?;

        for mut module in module_rows {
            if let Some((Some(title), _)) =
                translation(connection, "MODULE", &module.module_id, &locale)?
            {
                module.title = title;
            }
            let mut lessons = connection.prepare(
                "SELECT lesson_id, slug, title, difficulty, estimated_minutes, ordinal,
                        content_version, manifest_content_version, size_bytes, manifest_size_bytes,
                        withdrawn_at
                 FROM lessons WHERE module_id = ?1 ORDER BY ordinal, lesson_id;",
            )?;
            let lesson_rows: Vec<LessonSummary> = lessons
                .query_map([&module.module_id], |row| {
                    let stored: Option<i64> = row.get(6)?;
                    let advertised: Option<i64> = row.get(7)?;
                    let withdrawn: Option<String> = row.get(10)?;
                    let size: Option<i64> = row.get(8)?;
                    let manifest_size: Option<i64> = row.get(9)?;
                    Ok(LessonSummary {
                        lesson_id: row.get(0)?,
                        slug: row.get(1)?,
                        title: row.get(2)?,
                        difficulty: row.get(3)?,
                        estimated_minutes: row.get(4)?,
                        order: row.get(5)?,
                        availability: availability_of(advertised, stored, withdrawn.is_some()),
                        content_version: stored,
                        size_bytes: size.or(manifest_size),
                    })
                })?
                .collect::<rusqlite::Result<_>>()?;

            for mut lesson in lesson_rows {
                if let Some((Some(title), _)) =
                    translation(connection, "LESSON", &lesson.lesson_id, &locale)?
                {
                    lesson.title = title;
                }
                module.lessons.push(lesson);
            }
            track.modules.push(module);
        }

        let mut maps = connection.prepare(
            "SELECT mind_map_id, content_version, manifest_content_version, size_bytes,
                    manifest_size_bytes, withdrawn_at
             FROM mind_maps WHERE track_id = ?1;",
        )?;
        let mut rows = maps.query([&track_id])?;
        if let Some(row) = rows.next()? {
            let stored: Option<i64> = row.get(1)?;
            let advertised: Option<i64> = row.get(2)?;
            let withdrawn: Option<String> = row.get(5)?;
            let size: Option<i64> = row.get(3)?;
            let manifest_size: Option<i64> = row.get(4)?;
            track.mind_map = Some(MindMapSummary {
                mind_map_id: row.get(0)?,
                availability: availability_of(advertised, stored, withdrawn.is_some()),
                content_version: stored,
                size_bytes: size.or(manifest_size),
            });
        }

        Ok(track)
    })
}

#[tauri::command]
pub fn library_get_lesson(state: tauri::State<'_, AppState>, lesson_id: String) -> Command<Lesson> {
    parse_uuid("lessonId", &lesson_id)?;
    let db = state.db();
    let requested = lesson_id.clone();
    let lesson = db.with(move |connection| {
        let locale = settings::active_locale(connection)?;
        let mut statement = connection.prepare(
            "SELECT lesson_id, track_id, module_id, slug, title, body_markdown, difficulty,
                    estimated_minutes, ordinal, content_version
             FROM lessons WHERE lesson_id = ?1 AND content_version IS NOT NULL;",
        )?;
        let mut rows = statement.query([&lesson_id])?;
        let Some(row) = rows.next()? else {
            return Ok(None);
        };

        let mut lesson = Lesson {
            lesson_id: row.get(0)?,
            track_id: row.get(1)?,
            module_id: row.get(2)?,
            slug: row.get(3)?,
            title: row.get(4)?,
            body_markdown: row.get::<_, Option<String>>(5)?.unwrap_or_default(),
            difficulty: row.get(6)?,
            estimated_minutes: row.get(7)?,
            order: row.get(8)?,
            content_version: row.get(9)?,
            locale: "en".to_string(),
            is_fallback: locale != "en",
            code_examples: Vec::new(),
        };
        drop(rows);
        drop(statement);

        // A missing locale is not an error and not an empty entry: the client
        // falls back to English and the UI marks it untranslated.
        if let Some((title, body)) = translation(connection, "LESSON", &lesson.lesson_id, &locale)?
        {
            if let Some(title) = title {
                lesson.title = title;
            }
            if let Some(body) = body {
                lesson.body_markdown = body;
            }
            lesson.locale = locale;
            lesson.is_fallback = false;
        }

        let mut examples = connection.prepare(
            "SELECT caption, code, language, ordinal FROM code_examples
             WHERE lesson_id = ?1 ORDER BY position;",
        )?;
        lesson.code_examples = examples
            .query_map([&lesson.lesson_id], |row| {
                Ok(CodeExample {
                    caption: row.get(0)?,
                    code: row.get(1)?,
                    language: row.get(2)?,
                    order: row.get(3)?,
                })
            })?
            .collect::<rusqlite::Result<_>>()?;

        Ok(Some(lesson))
    })?;

    lesson.ok_or_else(|| {
        CommandError::not_in_library("the lesson has not been downloaded")
            .with_details(serde_json::json!({ "lessonId": requested }))
    })
}

#[tauri::command]
pub fn library_get_mind_map(
    state: tauri::State<'_, AppState>,
    track_id: String,
) -> Command<MindMap> {
    parse_uuid("trackId", &track_id)?;
    let db = state.db();
    let map = db.with(move |connection| {
        let mut statement = connection.prepare(
            "SELECT mind_map_id, track_id, content_version, root_json FROM mind_maps
             WHERE track_id = ?1 AND content_version IS NOT NULL;",
        )?;
        let mut rows = statement.query([&track_id])?;
        let Some(row) = rows.next()? else {
            return Ok(None);
        };
        let root: Option<String> = row.get(3)?;
        Ok(Some(MindMap {
            mind_map_id: row.get(0)?,
            track_id: row.get(1)?,
            content_version: row.get(2)?,
            root: root
                .and_then(|json| serde_json::from_str(&json).ok())
                .unwrap_or(serde_json::Value::Null),
        }))
    })?;

    map.ok_or_else(|| CommandError::not_in_library("the mind map has not been downloaded"))
}

/// Fetches manifests and compares them against the store. Reads the network and
/// downloads nothing: it only reports what a download would do.
#[tauri::command]
pub async fn library_refresh(
    state: tauri::State<'_, AppState>,
    track_id: Option<String>,
) -> Command<DeltaSummary> {
    let engine = state.engine();
    let targets = match &track_id {
        Some(track_id) => {
            parse_uuid("trackId", track_id)?;
            vec![track_id.clone()]
        }
        None => {
            // Refresh the catalog first so a track published since the last
            // visit becomes visible, then look at every track the user has any
            // content from.
            refresh_catalog(&engine).await?;
            engine.db().with(|connection| {
                let mut statement = connection.prepare(
                    "SELECT DISTINCT track_id FROM lessons WHERE content_version IS NOT NULL
                     UNION
                     SELECT DISTINCT track_id FROM mind_maps WHERE content_version IS NOT NULL;",
                )?;
                let ids: rusqlite::Result<Vec<String>> = statement
                    .query_map([], |row| row.get::<_, String>(0))?
                    .collect();
                ids
            })?
        }
    };

    let mut summary = DeltaSummary {
        checked_tracks: 0,
        updated_entities: 0,
        withdrawn_entities: 0,
        new_entities_available: 0,
        anomalies: Vec::new(),
    };

    for track in targets {
        let etag = engine
            .db()
            .with(|connection| settings_etag(connection, &track))?;
        match engine
            .client()
            .fetch_track_manifest(&track, etag.as_deref())
            .await
        {
            Ok(ManifestFetch::NotModified) => {
                summary.checked_tracks += 1;
                continue;
            }
            Ok(ManifestFetch::Fetched { value, etag }) => {
                apply_manifest(&engine, &value, etag.as_deref())?;
                summary.checked_tracks += 1;
            }
            // A 404 here is not data loss. Every entity the user holds from
            // that track is marked withdrawn, exactly as an absent entity would
            // be, and nothing is deleted.
            Err(TransferError::Status { code, .. }) if code == codes::TRACK_NOT_FOUND => {
                withdraw_whole_track(&engine, &track)?;
                summary.checked_tracks += 1;
            }
            Err(error) => return Err(map_transfer(error)),
        }

        let comparison = delta::compare(&delta::entities_of_track(engine.db(), &track)?);
        summary.updated_entities += comparison.updated.len() as i64;
        summary.withdrawn_entities += comparison.withdrawn.len() as i64;
        summary.new_entities_available += comparison.new_available.len() as i64;
        summary.anomalies.extend(comparison.anomalies);
    }

    engine.events().library_updated();
    Ok(summary)
}

// ---------------------------------------------------------------------------
// Downloads
// ---------------------------------------------------------------------------

/// One command for all three granularities. A lesson, a module and a track all
/// resolve to a list of entity entries, and the engine has no behaviour that
/// differs between them.
#[tauri::command]
pub async fn download_enqueue(
    state: tauri::State<'_, AppState>,
    scope: DownloadScope,
) -> Command<BatchHandle> {
    let engine = state.engine();
    parse_uuid("scope.id", &scope.id)?;

    let track_id = resolve_track(engine.db(), &scope)?;
    let manifest = match engine.client().fetch_track_manifest(&track_id, None).await {
        Ok(ManifestFetch::Fetched { value, etag }) => {
            apply_manifest(&engine, &value, etag.as_deref())?;
            value
        }
        Ok(ManifestFetch::NotModified) => {
            return Err(CommandError::new(
                codes::UNEXPECTED_RESPONSE,
                "the server answered 304 to an unconditional manifest request",
            ))
        }
        Err(error) => return Err(map_transfer(error)),
    };

    let wanted = resolve_entities(&manifest, &scope)?;
    let batch_id = uuid::Uuid::now_v7().to_string();

    let mut queued = 0i64;
    let mut skipped = 0i64;
    let mut already_queued = 0i64;
    let mut total_bytes = 0i64;

    for entity in wanted {
        // Enqueuing an entity already stored at the same version is a no-op, so
        // downloading a module and then its track does not re-fetch anything.
        if is_stored_at(engine.db(), &entity.entity_id, entity.content_version)? {
            skipped += 1;
            continue;
        }
        let outcome = engine.db().with(|connection| {
            queue::enqueue(
                connection,
                &entity.entity_id,
                entity.entity_type,
                Some(&track_id),
                &batch_id,
                entity.content_version,
                &entity.sha256,
                entity.size_bytes,
            )
        })?;
        match outcome {
            EnqueueOutcome::Inserted => {
                queued += 1;
                total_bytes += entity.size_bytes;
            }
            EnqueueOutcome::AlreadyQueued => {
                skipped += 1;
                already_queued += 1;
            }
        }
    }

    if queued == 0 {
        // Which of the two it is matters: one means the work is already done,
        // the other means it is already happening, and a progress bar that
        // completes instantly says neither.
        return Err(if already_queued > 0 {
            CommandError::new(
                codes::ALREADY_QUEUED,
                "every requested entity is already queued or in flight at this version",
            )
        } else {
            CommandError::new(
                codes::NOTHING_TO_DO,
                "every requested entity is already stored at the manifest version",
            )
        });
    }

    let pump = Arc::clone(&engine);
    tauri::async_runtime::spawn(async move { pump.pump().await });

    Ok(BatchHandle {
        batch_id,
        queued_entities: queued,
        skipped_entities: skipped,
        total_bytes,
    })
}

#[tauri::command]
pub fn download_pause(state: tauri::State<'_, AppState>, batch_id: Option<String>) -> Command<()> {
    let db = state.db();
    db.with(move |connection| queue::pause(connection, batch_id.as_deref(), PauseReason::User))?;
    Ok(())
}

#[tauri::command]
pub fn download_resume(state: tauri::State<'_, AppState>, batch_id: Option<String>) -> Command<()> {
    let engine = state.engine();
    engine
        .db()
        .with(move |connection| queue::resume(connection, batch_id.as_deref()))?;
    let pump = Arc::clone(&engine);
    tauri::async_runtime::spawn(async move { pump.pump().await });
    Ok(())
}

/// Removes queued and in-flight entries and deletes their partial files.
/// Already completed entities stay: cancelling a download does not undo what
/// already arrived.
#[tauri::command]
pub fn download_cancel(
    state: tauri::State<'_, AppState>,
    batch_id: String,
) -> Command<CancelSummary> {
    let engine = state.engine();
    let rows = engine
        .db()
        .with(|connection| queue::cancellable(connection, &batch_id))?;
    let kept = engine.db().with(|connection| {
        connection.query_row(
            "SELECT count(*) FROM download_queue WHERE batch_id = ?1 AND state = 'DONE';",
            [&batch_id],
            |row| row.get::<_, i64>(0),
        )
    })?;

    for row in &rows {
        engine.remove_partial_for(row);
        engine
            .db()
            .with(|connection| queue::remove(connection, &row.entity_id))?;
    }

    Ok(CancelSummary {
        cancelled_entities: rows.len() as i64,
        kept_entities: kept,
    })
}

#[tauri::command]
pub fn download_retry(state: tauri::State<'_, AppState>, entity_id: Option<String>) -> Command<()> {
    let engine = state.engine();
    engine
        .db()
        .with(move |connection| queue::retry(connection, entity_id.as_deref()))?;
    let pump = Arc::clone(&engine);
    tauri::async_runtime::spawn(async move { pump.pump().await });
    Ok(())
}

/// The full queue, for a UI that has just started and missed the events so far.
/// Events describe changes, and a screen opened mid-download has no history to
/// replay.
#[tauri::command]
pub fn download_queue_state(state: tauri::State<'_, AppState>) -> Command<Vec<QueueEntry>> {
    let db = state.db();
    db.with(|connection| {
        let rows = queue::all(connection)?;
        let mut entries = Vec::with_capacity(rows.len());
        for row in rows {
            let title = entity_title(connection, row.entity_type, &row.entity_id)?;
            entries.push(QueueEntry {
                entity_id: row.entity_id,
                entity_type: row.entity_type,
                title,
                batch_id: row.batch_id,
                state: row.state,
                received_bytes: row.received_bytes,
                total_bytes: row.size_bytes,
                attempt: row.attempt,
                pause_reason: row.pause_reason,
                error_code: row.error_code,
            });
        }
        Ok(entries)
    })
}

/// Removes downloaded content from the store.
///
/// Progress data is never touched. A user who deletes a track to reclaim space
/// and downloads it again later finds their completed lessons still marked.
/// This command deliberately does less than its name suggests, and it is
/// written down here so nobody later "fixes" it into a cascade.
#[tauri::command]
pub fn download_delete(
    state: tauri::State<'_, AppState>,
    scope: DownloadScope,
) -> Command<DeleteSummary> {
    parse_uuid("scope.id", &scope.id)?;
    let engine = state.engine();
    let db = engine.db();

    let (lessons, mind_maps) = db.with(|connection| {
        let (lesson_sql, map_sql) = match scope.kind {
            ScopeKind::Lesson => (
                "SELECT lesson_id, coalesce(size_bytes, 0) FROM lessons
                 WHERE lesson_id = ?1 AND content_version IS NOT NULL",
                "SELECT mind_map_id, coalesce(size_bytes, 0) FROM mind_maps WHERE 1 = 0 AND ?1 = ?1",
            ),
            ScopeKind::Module => (
                "SELECT lesson_id, coalesce(size_bytes, 0) FROM lessons
                 WHERE module_id = ?1 AND content_version IS NOT NULL",
                "SELECT mind_map_id, coalesce(size_bytes, 0) FROM mind_maps WHERE 1 = 0 AND ?1 = ?1",
            ),
            ScopeKind::Track => (
                "SELECT lesson_id, coalesce(size_bytes, 0) FROM lessons
                 WHERE track_id = ?1 AND content_version IS NOT NULL",
                "SELECT mind_map_id, coalesce(size_bytes, 0) FROM mind_maps
                 WHERE track_id = ?1 AND content_version IS NOT NULL",
            ),
        };
        let mut lessons = connection.prepare(lesson_sql)?;
        let lessons: Vec<(String, i64)> = lessons
            .query_map([&scope.id], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect::<rusqlite::Result<_>>()?;
        let mut maps = connection.prepare(map_sql)?;
        let maps: Vec<(String, i64)> = maps
            .query_map([&scope.id], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect::<rusqlite::Result<_>>()?;
        Ok((lessons, maps))
    })?;

    if lessons.is_empty() && mind_maps.is_empty() {
        return Err(CommandError::not_in_library(
            "nothing in that scope is stored locally",
        ));
    }

    let mut freed = 0i64;
    let mut removed = 0i64;
    db.with(|connection| {
        for (lesson_id, size) in &lessons {
            removed += replica::clear_lesson_content(connection, lesson_id)? as i64;
            freed += size;
        }
        for (mind_map_id, size) in &mind_maps {
            removed += replica::clear_mind_map_content(connection, mind_map_id)? as i64;
            freed += size;
        }
        Ok(())
    })?;

    engine.events().library_updated();
    Ok(DeleteSummary {
        removed_entities: removed,
        freed_bytes: freed,
    })
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

/// Records completion locally. Works offline and with an expired token, because
/// it touches nothing but SQLite.
#[tauri::command]
pub fn progress_mark(
    state: tauri::State<'_, AppState>,
    lesson_id: String,
    completed: bool,
) -> Command<ProgressEntry> {
    parse_uuid("lessonId", &lesson_id)?;
    let db = state.db();
    db.with(move |connection| {
        let user_id = settings::active_user(connection)?;
        let now = now_iso();
        // `completed: false` is a real state, not an absence: it records
        // "explicitly marked incomplete" and must survive sync as such.
        let completed_at = completed.then(|| now.clone());
        connection.execute(
            "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
             VALUES (?1, ?2, ?3, ?4, 'PENDING')
             ON CONFLICT(user_id, lesson_id) DO UPDATE SET
                 completed_at = excluded.completed_at,
                 client_updated_at = excluded.client_updated_at,
                 sync_state = 'PENDING';",
            rusqlite::params![user_id, lesson_id, completed_at, now],
        )?;
        Ok(ProgressEntry {
            lesson_id,
            completed_at,
            client_updated_at: now,
            sync_state: SyncState::Pending,
        })
    })
}

/// Rows the server has not acknowledged. `ORPHANED` rows are deliberately not
/// here: a row the server rejected is never sent again, which is what stops a
/// batch growing for the life of the installation.
#[tauri::command]
pub fn progress_pending(state: tauri::State<'_, AppState>) -> Command<Vec<ProgressEntry>> {
    state
        .db()
        .with(|connection| read_progress(connection, true))
}

/// Reads the active user's progress, either all of it or only what still owes
/// the server a write.
///
/// One query behind both commands, so the two can never disagree about which
/// rows belong to whom or how a stored `sync_state` is spelled.
fn read_progress(
    connection: &rusqlite::Connection,
    pending_only: bool,
) -> rusqlite::Result<Vec<ProgressEntry>> {
    let user_id = settings::active_user(connection)?;
    let sql = if pending_only {
        "SELECT lesson_id, completed_at, client_updated_at, sync_state FROM user_progress
         WHERE user_id = ?1 AND sync_state = 'PENDING' ORDER BY client_updated_at;"
    } else {
        "SELECT lesson_id, completed_at, client_updated_at, sync_state FROM user_progress
         WHERE user_id = ?1 ORDER BY client_updated_at;"
    };
    let mut statement = connection.prepare(sql)?;
    let entries: rusqlite::Result<Vec<ProgressEntry>> = statement
        .query_map([&user_id], |row| {
            let sync_state: String = row.get(3)?;
            Ok(ProgressEntry {
                lesson_id: row.get(0)?,
                completed_at: row.get(1)?,
                client_updated_at: row.get(2)?,
                // A value the store cannot name is reported as pending rather
                // than dropped: an unsendable row is worse than one sent twice,
                // because reconciliation is last-write-wins and replaying a row
                // changes nothing.
                sync_state: sync_state.parse().unwrap_or(SyncState::Pending),
            })
        })?
        .collect();
    entries
}

/// Every progress row the active user holds, whatever its sync state.
///
/// This is what the UI reads to show a lesson as completed. It is not
/// `progress_pending` with a wider filter: that command answers "what still
/// owes the server a write", and using it to render completion would make a
/// lesson look incomplete the moment its row synchronised.
///
/// `ORPHANED` rows are included. The server rejected them -- usually because
/// the lesson was deleted upstream -- but the user did the work, and the row is
/// kept precisely so it can still be shown.
#[tauri::command]
pub fn progress_list(state: tauri::State<'_, AppState>) -> Command<Vec<ProgressEntry>> {
    state
        .db()
        .with(|connection| read_progress(connection, false))
}

/// Writes back what the server said, per row. Nothing here deletes a progress
/// row.
#[tauri::command]
pub fn progress_apply_results(
    state: tauri::State<'_, AppState>,
    results: Vec<ProgressResult>,
) -> Command<()> {
    let db = state.db();
    db.transaction(move |tx| {
        let user_id = settings::active_user(tx)?;
        for result in &results {
            match result.status.as_str() {
                // The server took it. If it clamped the timestamp, the clamped
                // value is stored, so a device with a skewed clock converges
                // instead of arguing forever.
                "APPLIED" => {
                    let timestamp = result.server_client_updated_at.clone();
                    tx.execute(
                        "UPDATE user_progress
                         SET sync_state = 'SYNCED',
                             client_updated_at = coalesce(?3, client_updated_at)
                         WHERE user_id = ?1 AND lesson_id = ?2;",
                        rusqlite::params![user_id, result.lesson_id, timestamp],
                    )?;
                }
                // The server holds a newer record; the local row takes it.
                "STALE" => {
                    tx.execute(
                        "UPDATE user_progress
                         SET sync_state = 'SYNCED',
                             client_updated_at = coalesce(?3, client_updated_at),
                             completed_at = CASE WHEN ?4 THEN ?5 ELSE completed_at END
                         WHERE user_id = ?1 AND lesson_id = ?2;",
                        rusqlite::params![
                            user_id,
                            result.lesson_id,
                            result.server_client_updated_at,
                            result.completed_at.is_some(),
                            result.completed_at.clone().flatten()
                        ],
                    )?;
                }
                // Usually because the lesson has since been deleted. The row is
                // kept and never sent again; without this a client that
                // accumulated writes offline would carry the same rejected row
                // in every future batch, forever.
                "REJECTED" => {
                    tx.execute(
                        "UPDATE user_progress SET sync_state = 'ORPHANED'
                         WHERE user_id = ?1 AND lesson_id = ?2;",
                        rusqlite::params![user_id, result.lesson_id],
                    )?;
                }
                _ => {}
            }
            tx.execute(
                "INSERT INTO sync_log (kind, subject_id, outcome, detail, occurred_at)
                 VALUES ('PROGRESS', ?1, ?2, ?3, ?4);",
                rusqlite::params![result.lesson_id, result.status, result.code, now_iso()],
            )?;
        }
        Ok(())
    })
}

// ---------------------------------------------------------------------------
// Settings and session
// ---------------------------------------------------------------------------

#[tauri::command]
pub fn settings_get(state: tauri::State<'_, AppState>) -> Command<AppSettings> {
    state.db().with(settings::read)
}

#[tauri::command]
pub fn settings_set(
    state: tauri::State<'_, AppState>,
    patch: SettingsPatch,
) -> Command<AppSettings> {
    state
        .db()
        .with(move |connection| settings::apply(connection, &patch))
}

#[tauri::command]
pub fn session_store(state: tauri::State<'_, AppState>, session: StoredSession) -> Command<()> {
    state
        .db()
        .with(move |connection| settings::store_session(connection, &session))
}

#[tauri::command]
pub fn session_load(state: tauri::State<'_, AppState>) -> Command<Option<StoredSession>> {
    state.db().with(settings::load_session)
}

#[tauri::command]
pub fn session_clear(state: tauri::State<'_, AppState>) -> Command<()> {
    state.db().with(settings::clear_session)
}

/// Reveals the main window.
///
/// It starts hidden so that nothing is painted before the theme is applied; the
/// UI calls this once the theme is on the document. The two halves are a pair --
/// removing the hidden start makes this command pointless, and removing the
/// call leaves a window that never appears.
#[tauri::command]
pub fn window_show(app: tauri::AppHandle) -> Command<()> {
    use tauri::Manager;
    let window = app.get_webview_window("main").ok_or_else(|| {
        CommandError::new(codes::UNEXPECTED_RESPONSE, "the main window does not exist")
    })?;
    window.show().map_err(|error| {
        CommandError::new(
            codes::UNEXPECTED_RESPONSE,
            format!("the window could not be shown: {error}"),
        )
    })
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fn translation(
    connection: &rusqlite::Connection,
    entity_type: &str,
    entity_id: &str,
    locale: &str,
) -> rusqlite::Result<Option<(Option<String>, Option<String>)>> {
    if locale == "en" {
        return Ok(None);
    }
    let mut statement = connection.prepare(
        "SELECT title, body FROM content_translations
         WHERE entity_type = ?1 AND entity_id = ?2 AND locale = ?3;",
    )?;
    let mut rows = statement.query(rusqlite::params![entity_type, entity_id, locale])?;
    match rows.next()? {
        Some(row) => Ok(Some((row.get(0)?, row.get(1)?))),
        None => Ok(None),
    }
}

fn entity_title(
    connection: &rusqlite::Connection,
    entity_type: EntityType,
    entity_id: &str,
) -> rusqlite::Result<Option<String>> {
    let sql = match entity_type {
        EntityType::Lesson => "SELECT title FROM lessons WHERE lesson_id = ?1;",
        EntityType::MindMap => {
            "SELECT t.title FROM mind_maps m JOIN tracks t ON t.track_id = m.track_id
             WHERE m.mind_map_id = ?1;"
        }
    };
    let mut statement = connection.prepare(sql)?;
    let mut rows = statement.query([entity_id])?;
    match rows.next()? {
        Some(row) => Ok(Some(row.get(0)?)),
        None => Ok(None),
    }
}

fn settings_etag(
    connection: &rusqlite::Connection,
    track_id: &str,
) -> rusqlite::Result<Option<String>> {
    let mut statement =
        connection.prepare("SELECT manifest_etag FROM tracks WHERE track_id = ?1;")?;
    let mut rows = statement.query([track_id])?;
    match rows.next()? {
        Some(row) => Ok(row.get(0)?),
        None => Ok(None),
    }
}

fn apply_manifest(
    engine: &Arc<Engine>,
    manifest: &TrackManifest,
    etag: Option<&str>,
) -> Command<()> {
    engine
        .db()
        .transaction(|tx| replica::apply_track_manifest(tx, manifest, etag))
}

/// A track that stopped being published. Everything held from it is marked
/// withdrawn and nothing is deleted.
fn withdraw_whole_track(engine: &Arc<Engine>, track_id: &str) -> Command<()> {
    engine.db().transaction(|tx| {
        let now = now_iso();
        tx.execute(
            "UPDATE lessons SET withdrawn_at = ?2
             WHERE track_id = ?1 AND content_version IS NOT NULL AND withdrawn_at IS NULL;",
            rusqlite::params![track_id, now],
        )?;
        tx.execute(
            "UPDATE mind_maps SET withdrawn_at = ?2
             WHERE track_id = ?1 AND content_version IS NOT NULL AND withdrawn_at IS NULL;",
            rusqlite::params![track_id, now],
        )?;
        Ok(())
    })
}

async fn refresh_catalog(engine: &Arc<Engine>) -> Command<()> {
    match engine.client().fetch_catalog(None).await {
        Ok(ManifestFetch::Fetched { value, .. }) => engine.db().with(|connection| {
            for track in &value.tracks {
                connection.execute(
                    "INSERT INTO tracks (track_id, slug, title, content_version, lesson_count,
                                         total_size_bytes, updated_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                     ON CONFLICT(track_id) DO UPDATE SET
                         slug = excluded.slug,
                         title = excluded.title,
                         content_version = excluded.content_version,
                         lesson_count = excluded.lesson_count,
                         total_size_bytes = excluded.total_size_bytes,
                         updated_at = excluded.updated_at;",
                    rusqlite::params![
                        track.track_id,
                        track.slug,
                        track.title,
                        track.content_version,
                        track.lesson_count,
                        track.total_size_bytes,
                        track.updated_at
                    ],
                )?;
            }
            Ok(())
        }),
        Ok(ManifestFetch::NotModified) => Ok(()),
        Err(error) => Err(map_transfer(error)),
    }
}

fn resolve_track(db: &Arc<Db>, scope: &DownloadScope) -> Command<String> {
    let id = scope.id.clone();
    let sql = match scope.kind {
        ScopeKind::Track => return Ok(id),
        ScopeKind::Module => "SELECT track_id FROM modules WHERE module_id = ?1;",
        ScopeKind::Lesson => "SELECT track_id FROM lessons WHERE lesson_id = ?1;",
    };
    let found: Option<String> = db.with(move |connection| {
        let mut statement = connection.prepare(sql)?;
        let mut rows = statement.query([&id])?;
        match rows.next()? {
            Some(row) => Ok(Some(row.get(0)?)),
            None => Ok(None),
        }
    })?;

    found.ok_or_else(|| {
        CommandError::not_in_library(
            "that scope is not in the local store; refresh the library first",
        )
    })
}

fn resolve_entities(
    manifest: &TrackManifest,
    scope: &DownloadScope,
) -> Command<Vec<crate::manifest::ManifestEntity>> {
    let entities = match scope.kind {
        ScopeKind::Track => manifest.entities.clone(),
        ScopeKind::Module => {
            let module = manifest
                .modules
                .iter()
                .find(|module| module.module_id == scope.id)
                .ok_or_else(|| {
                    CommandError::not_in_library("the module is not in the current manifest")
                })?;
            module
                .lessons
                .iter()
                .filter_map(|lesson| manifest.entity(&lesson.lesson_id).cloned())
                .collect()
        }
        ScopeKind::Lesson => manifest
            .entity(&scope.id)
            .cloned()
            .map(|entity| vec![entity])
            .ok_or_else(|| {
                CommandError::not_in_library("the lesson is not in the current manifest")
            })?,
    };

    if entities.is_empty() {
        return Err(CommandError::new(
            codes::NOTHING_TO_DO,
            "the scope resolved to no entities",
        ));
    }
    Ok(entities)
}

fn is_stored_at(db: &Arc<Db>, entity_id: &str, version: i64) -> Command<bool> {
    let entity_id = entity_id.to_string();
    db.with(move |connection| {
        let count: i64 = connection.query_row(
            "SELECT (SELECT count(*) FROM lessons
                      WHERE lesson_id = ?1 AND content_version = ?2)
                  + (SELECT count(*) FROM mind_maps
                      WHERE mind_map_id = ?1 AND content_version = ?2);",
            rusqlite::params![entity_id, version],
            |row| row.get(0),
        )?;
        Ok(count > 0)
    })
}

fn map_transfer(error: TransferError) -> CommandError {
    match error {
        TransferError::Unreachable(message) => {
            CommandError::new(codes::NETWORK_UNAVAILABLE, message)
        }
        TransferError::Interrupted(message) => {
            CommandError::new(codes::NETWORK_UNAVAILABLE, message)
        }
        TransferError::Status { status, code, .. } => {
            CommandError::new(&code, format!("the server answered {status}"))
        }
        TransferError::StorageFull(message) => {
            CommandError::new(codes::INSUFFICIENT_STORAGE, message)
        }
        TransferError::Local(message) => CommandError::new(codes::STORE_UNAVAILABLE, message),
        TransferError::Unexpected(message) => {
            CommandError::new(codes::UNEXPECTED_RESPONSE, message)
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use crate::settings::ANONYMOUS_USER;
    use crate::store::open_in_memory;

    fn seeded() -> rusqlite::Connection {
        let connection = open_in_memory().expect("store");
        connection
            .execute_batch(&format!(
                "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('{ANONYMOUS_USER}', 'l1', '2026-09-04T10:00:00.000Z', '2026-09-04T10:00:00.000Z', 'SYNCED');
                 INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('{ANONYMOUS_USER}', 'l2', NULL, '2026-09-04T10:00:01.000Z', 'PENDING');
                 INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('{ANONYMOUS_USER}', 'l3', '2026-09-04T10:00:02.000Z', '2026-09-04T10:00:02.000Z', 'ORPHANED');
                 INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('someone-else', 'l4', '2026-09-04T10:00:03.000Z', '2026-09-04T10:00:03.000Z', 'PENDING');"
            ))
            .expect("seed");
        connection
    }

    #[test]
    fn listing_progress_returns_every_row_of_the_active_user() {
        let connection = seeded();
        let entries = read_progress(&connection, false).expect("list");

        assert_eq!(
            entries
                .iter()
                .map(|e| e.lesson_id.as_str())
                .collect::<Vec<_>>(),
            vec!["l1", "l2", "l3"],
            "another account's progress is never in the answer"
        );
        assert_eq!(entries[0].sync_state, SyncState::Synced);
        assert_eq!(entries[1].sync_state, SyncState::Pending);
        assert_eq!(
            entries[2].sync_state,
            SyncState::Orphaned,
            "the server rejected it, but the user did the work and it is still shown"
        );
    }

    #[test]
    fn listing_keeps_an_explicit_incompletion_apart_from_a_completion() {
        let connection = seeded();
        let entries = read_progress(&connection, false).expect("list");

        let marked_incomplete = entries.iter().find(|e| e.lesson_id == "l2").expect("l2");
        assert_eq!(
            marked_incomplete.completed_at, None,
            "a null completed_at is a value, not the absence of a row"
        );
        assert!(entries
            .iter()
            .find(|e| e.lesson_id == "l1")
            .expect("l1")
            .completed_at
            .is_some());
    }

    #[test]
    fn pending_is_a_strict_subset_of_the_full_list() {
        let connection = seeded();
        let all = read_progress(&connection, false).expect("list");
        let pending = read_progress(&connection, true).expect("pending");

        assert_eq!(
            pending
                .iter()
                .map(|e| e.lesson_id.as_str())
                .collect::<Vec<_>>(),
            vec!["l2"]
        );
        assert!(
            pending.len() < all.len(),
            "rendering completion from the pending set would make a lesson look \
             incomplete the moment its row synchronised"
        );
    }

    #[test]
    fn signing_in_changes_which_rows_are_answered_without_deleting_any() {
        let connection = seeded();
        settings::set(&connection, settings::KEY_ACTIVE_USER, "someone-else").expect("sign in");

        let entries = read_progress(&connection, false).expect("list");
        assert_eq!(
            entries
                .iter()
                .map(|e| e.lesson_id.as_str())
                .collect::<Vec<_>>(),
            vec!["l4"]
        );

        let total: i64 = connection
            .query_row("SELECT count(*) FROM user_progress;", [], |row| row.get(0))
            .expect("count");
        assert_eq!(total, 4, "signing in as someone else discards nothing");
    }
}
