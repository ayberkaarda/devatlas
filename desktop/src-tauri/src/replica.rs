//! Writing manifests and packages into the local read replica.
//!
//! Two write paths meet here and they carry different things. A track manifest
//! brings structure -- which modules exist, which lessons they hold, and which
//! version of each entity the server is currently publishing. A package brings
//! the content of exactly one entity. Structure arrives first and content fills
//! it in, which is why a lesson row can exist with no body: that is the state
//! the library screen renders as "not downloaded".

use rusqlite::{params, Connection, Transaction};

use crate::db::now_iso;
use crate::manifest::{Package, TrackManifest};
use crate::model::EntityType;

/// Applies a freshly fetched track manifest to the replica.
///
/// Nothing downloaded is ever deleted here. An entity that disappeared upstream
/// is marked withdrawn and kept: a reader who was halfway through a lesson has
/// lost something real if it vanishes, and text costs almost nothing to store.
/// Only structural rows that were never downloaded are removed, because they
/// describe content that no longer exists and hold nothing of the user's.
pub fn apply_track_manifest(
    tx: &Transaction<'_>,
    manifest: &TrackManifest,
    etag: Option<&str>,
) -> rusqlite::Result<()> {
    let now = now_iso();

    let lesson_count = manifest.lessons().count() as i64;
    let total_size: i64 = manifest.entities.iter().map(|e| e.size_bytes).sum();

    tx.execute(
        "INSERT INTO tracks (track_id, slug, title, description, icon, content_version,
                             lesson_count, total_size_bytes, manifest_etag, manifest_fetched_at,
                             updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?10)
         ON CONFLICT(track_id) DO UPDATE SET
             slug = excluded.slug,
             title = excluded.title,
             description = excluded.description,
             icon = excluded.icon,
             content_version = excluded.content_version,
             lesson_count = excluded.lesson_count,
             total_size_bytes = excluded.total_size_bytes,
             manifest_etag = excluded.manifest_etag,
             manifest_fetched_at = excluded.manifest_fetched_at,
             updated_at = excluded.updated_at;",
        params![
            manifest.track_id,
            manifest.slug,
            manifest.title,
            manifest.description,
            manifest.icon,
            manifest.content_version,
            lesson_count,
            total_size,
            etag,
            now
        ],
    )?;

    write_translations(
        tx,
        "TRACK",
        &manifest.track_id,
        manifest.translations.iter().map(|t| {
            (
                t.locale.as_str(),
                t.title.as_deref(),
                t.body.as_deref(),
                manifest.content_version,
            )
        }),
    )?;

    for module in &manifest.modules {
        tx.execute(
            "INSERT INTO modules (module_id, track_id, title, ordinal, estimated_minutes)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(module_id) DO UPDATE SET
                 track_id = excluded.track_id,
                 title = excluded.title,
                 ordinal = excluded.ordinal,
                 estimated_minutes = excluded.estimated_minutes;",
            params![
                module.module_id,
                manifest.track_id,
                module.title,
                module.order,
                module.estimated_minutes
            ],
        )?;
        write_translations(
            tx,
            "MODULE",
            &module.module_id,
            module.translations.iter().map(|t| {
                (
                    t.locale.as_str(),
                    t.title.as_deref(),
                    t.body.as_deref(),
                    manifest.content_version,
                )
            }),
        )?;

        for lesson in &module.lessons {
            let entity = manifest.entity(&lesson.lesson_id);
            tx.execute(
                "INSERT INTO lessons (lesson_id, track_id, module_id, slug, title, difficulty,
                                      estimated_minutes, ordinal, manifest_content_version,
                                      manifest_sha256, manifest_size_bytes, withdrawn_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, NULL)
                 ON CONFLICT(lesson_id) DO UPDATE SET
                     track_id = excluded.track_id,
                     module_id = excluded.module_id,
                     slug = excluded.slug,
                     title = excluded.title,
                     difficulty = excluded.difficulty,
                     estimated_minutes = excluded.estimated_minutes,
                     ordinal = excluded.ordinal,
                     manifest_content_version = excluded.manifest_content_version,
                     manifest_sha256 = excluded.manifest_sha256,
                     manifest_size_bytes = excluded.manifest_size_bytes,
                     -- An entity that reappears in a manifest stops being
                     -- withdrawn, which is the other half of never deleting it.
                     withdrawn_at = NULL;",
                params![
                    lesson.lesson_id,
                    manifest.track_id,
                    module.module_id,
                    lesson.slug,
                    lesson.title,
                    lesson.difficulty,
                    lesson.estimated_minutes,
                    lesson.order,
                    entity.map(|e| e.content_version),
                    entity.map(|e| e.sha256.as_str()),
                    entity.map(|e| e.size_bytes),
                ],
            )?;
            write_translations(
                tx,
                "LESSON",
                &lesson.lesson_id,
                lesson.translations.iter().map(|t| {
                    (
                        t.locale.as_str(),
                        t.title.as_deref(),
                        t.body.as_deref(),
                        manifest.content_version,
                    )
                }),
            )?;
        }
    }

    if let Some(entity) = manifest
        .entities
        .iter()
        .find(|e| e.entity_type == EntityType::MindMap)
    {
        tx.execute(
            "INSERT INTO mind_maps (mind_map_id, track_id, manifest_content_version,
                                    manifest_sha256, manifest_size_bytes, withdrawn_at)
             VALUES (?1, ?2, ?3, ?4, ?5, NULL)
             ON CONFLICT(mind_map_id) DO UPDATE SET
                 track_id = excluded.track_id,
                 manifest_content_version = excluded.manifest_content_version,
                 manifest_sha256 = excluded.manifest_sha256,
                 manifest_size_bytes = excluded.manifest_size_bytes,
                 withdrawn_at = NULL;",
            params![
                entity.entity_id,
                manifest.track_id,
                entity.content_version,
                entity.sha256,
                entity.size_bytes
            ],
        )?;
    }

    mark_withdrawn(tx, manifest, &now)?;
    Ok(())
}

/// Marks stored entities the manifest no longer lists, and drops structural
/// rows that were never downloaded.
fn mark_withdrawn(
    tx: &Transaction<'_>,
    manifest: &TrackManifest,
    now: &str,
) -> rusqlite::Result<()> {
    let present: Vec<String> = manifest.lessons().map(|l| l.lesson_id.clone()).collect();
    // The two statements below bind a different number of leading parameters,
    // so each needs its own placeholder numbering.
    let update_placeholders = sql_placeholder_list(present.len(), 3);
    let delete_placeholders = sql_placeholder_list(present.len(), 2);
    let bindings: Vec<&dyn rusqlite::ToSql> =
        std::iter::once(&manifest.track_id as &dyn rusqlite::ToSql)
            .chain(std::iter::once(&now as &dyn rusqlite::ToSql))
            .chain(present.iter().map(|id| id as &dyn rusqlite::ToSql))
            .collect();

    tx.execute(
        &format!(
            "UPDATE lessons SET withdrawn_at = ?2
             WHERE track_id = ?1 AND content_version IS NOT NULL AND withdrawn_at IS NULL
               AND lesson_id NOT IN ({update_placeholders});"
        ),
        bindings.as_slice(),
    )?;

    let delete_bindings: Vec<&dyn rusqlite::ToSql> =
        std::iter::once(&manifest.track_id as &dyn rusqlite::ToSql)
            .chain(present.iter().map(|id| id as &dyn rusqlite::ToSql))
            .collect();
    tx.execute(
        &format!(
            "DELETE FROM lessons
             WHERE track_id = ?1 AND content_version IS NULL AND lesson_id NOT IN ({delete_placeholders});"
        ),
        delete_bindings.as_slice(),
    )?;

    // A module is removed only once nothing is left inside it. Deleting it
    // while it still held a downloaded lesson would cascade that lesson away,
    // which is exactly what withdrawal exists to avoid.
    tx.execute(
        "DELETE FROM modules
         WHERE track_id = ?1
           AND NOT EXISTS (SELECT 1 FROM lessons WHERE lessons.module_id = modules.module_id);",
        params![manifest.track_id],
    )?;

    Ok(())
}

/// Placeholders for a variable-length `IN` list, numbered from `start`. An
/// empty list produces a value nothing equals, so the surrounding `NOT IN`
/// stays true for every row.
fn sql_placeholder_list(count: usize, start: usize) -> String {
    if count == 0 {
        return "NULL".to_string();
    }
    (0..count)
        .map(|index| format!("?{}", index + start))
        .collect::<Vec<_>>()
        .join(", ")
}

fn write_translations<'a>(
    tx: &Transaction<'_>,
    entity_type: &str,
    entity_id: &str,
    translations: impl Iterator<Item = (&'a str, Option<&'a str>, Option<&'a str>, i64)>,
) -> rusqlite::Result<()> {
    for (locale, title, body, version) in translations {
        tx.execute(
            "INSERT INTO content_translations (entity_type, entity_id, locale, title, body, content_version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)
             ON CONFLICT(entity_type, entity_id, locale) DO UPDATE SET
                 title = excluded.title,
                 body = excluded.body,
                 content_version = excluded.content_version;",
            params![entity_type, entity_id, locale, title, body, version],
        )?;
    }
    Ok(())
}

/// Writes a verified package into the replica.
///
/// Called inside the same transaction that marks the queue entry `DONE`. That
/// pairing is the point: an entity marked complete with no rows behind it would
/// satisfy every future delta comparison while the lesson does not actually
/// exist locally, and nothing would ever notice.
pub fn write_package(
    tx: &Transaction<'_>,
    package: &Package,
    sha256: &str,
    size_bytes: i64,
) -> rusqlite::Result<usize> {
    let now = now_iso();
    match package {
        Package::Lesson(lesson) => {
            let updated = tx.execute(
                "UPDATE lessons
                 SET slug = ?2, title = ?3, body_markdown = ?4, difficulty = ?5,
                     estimated_minutes = ?6, ordinal = ?7, module_id = ?8,
                     content_version = ?9, sha256 = ?10, size_bytes = ?11,
                     downloaded_at = ?12, withdrawn_at = NULL
                 WHERE lesson_id = ?1;",
                params![
                    lesson.entity_id,
                    lesson.slug,
                    lesson.title,
                    lesson.body_markdown,
                    lesson.difficulty,
                    lesson.estimated_minutes,
                    lesson.order,
                    lesson.module_id,
                    lesson.content_version,
                    sha256,
                    size_bytes,
                    now
                ],
            )?;
            if updated == 0 {
                return Ok(0);
            }

            // Replaced wholesale rather than merged. The package is the entire
            // truth about this lesson's examples, so an example deleted
            // upstream has to disappear here too.
            tx.execute(
                "DELETE FROM code_examples WHERE lesson_id = ?1;",
                params![lesson.entity_id],
            )?;
            for (position, example) in lesson.code_examples.iter().enumerate() {
                tx.execute(
                    "INSERT INTO code_examples (lesson_id, position, ordinal, caption, code, language)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6);",
                    params![
                        lesson.entity_id,
                        position as i64,
                        example.order,
                        example.caption,
                        example.code,
                        example.language
                    ],
                )?;
            }

            tx.execute(
                "DELETE FROM content_translations WHERE entity_type = 'LESSON' AND entity_id = ?1;",
                params![lesson.entity_id],
            )?;
            for translation in &lesson.translations {
                tx.execute(
                    "INSERT INTO content_translations
                         (entity_type, entity_id, locale, title, body, content_version, sha256)
                     VALUES ('LESSON', ?1, ?2, ?3, ?4, ?5, ?6);",
                    params![
                        lesson.entity_id,
                        translation.locale,
                        translation.title,
                        translation.body_markdown,
                        lesson.content_version,
                        sha256
                    ],
                )?;
            }
            Ok(updated)
        }
        Package::MindMap(mind_map) => {
            let root = serde_json::to_string(&mind_map.root).unwrap_or_else(|_| "null".to_string());
            let updated = tx.execute(
                "UPDATE mind_maps
                 SET track_id = ?2, root_json = ?3, content_version = ?4, sha256 = ?5,
                     size_bytes = ?6, downloaded_at = ?7, withdrawn_at = NULL
                 WHERE mind_map_id = ?1;",
                params![
                    mind_map.entity_id,
                    mind_map.track_id,
                    root,
                    mind_map.content_version,
                    sha256,
                    size_bytes,
                    now
                ],
            )?;
            Ok(updated)
        }
    }
}

/// Removes downloaded content, leaving the structure in place so the entity can
/// be offered for download again.
///
/// Progress is never touched. A user who deletes a track to reclaim space and
/// downloads it again later finds their completed lessons still marked.
pub fn clear_lesson_content(connection: &Connection, lesson_id: &str) -> rusqlite::Result<usize> {
    connection.execute(
        "DELETE FROM code_examples WHERE lesson_id = ?1;",
        params![lesson_id],
    )?;
    connection.execute(
        "DELETE FROM content_translations WHERE entity_type = 'LESSON' AND entity_id = ?1;",
        params![lesson_id],
    )?;
    connection.execute(
        "UPDATE lessons
         SET body_markdown = NULL, content_version = NULL, sha256 = NULL, size_bytes = NULL,
             downloaded_at = NULL
         WHERE lesson_id = ?1 AND content_version IS NOT NULL;",
        params![lesson_id],
    )
}

pub fn clear_mind_map_content(
    connection: &Connection,
    mind_map_id: &str,
) -> rusqlite::Result<usize> {
    connection.execute(
        "UPDATE mind_maps
         SET root_json = NULL, content_version = NULL, sha256 = NULL, size_bytes = NULL,
             downloaded_at = NULL
         WHERE mind_map_id = ?1 AND content_version IS NOT NULL;",
        params![mind_map_id],
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::open_in_memory;

    fn manifest_json(lesson_ids: &[&str]) -> String {
        let lessons: Vec<String> = lesson_ids
            .iter()
            .enumerate()
            .map(|(index, id)| {
                format!(
                    r#"{{"lesson_id":"{id}","slug":"s{index}","title":"Lesson {index}","order":{}}}"#,
                    index + 1
                )
            })
            .collect();
        let entities: Vec<String> = lesson_ids
            .iter()
            .map(|id| {
                format!(
                    r#"{{"entity_type":"LESSON","entity_id":"{id}","content_version":3,"sha256":"aa","size_bytes":10}}"#
                )
            })
            .collect();
        format!(
            r#"{{"track_id":"t1","slug":"angular-path","content_version":47,"title":"The Angular Path",
                 "modules":[{{"module_id":"m1","title":"Signals","order":1,"lessons":[{}]}}],
                 "entities":[{}]}}"#,
            lessons.join(","),
            entities.join(",")
        )
    }

    fn apply(connection: &mut Connection, json: &str) {
        let manifest: TrackManifest = serde_json::from_str(json).expect("parse manifest");
        let tx = connection.transaction().expect("tx");
        apply_track_manifest(&tx, &manifest, Some("\"etag\"")).expect("apply");
        tx.commit().expect("commit");
    }

    #[test]
    fn a_manifest_creates_structure_without_content() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1", "l2"]));

        let (stored, advertised): (Option<i64>, Option<i64>) = connection
            .query_row(
                "SELECT content_version, manifest_content_version FROM lessons WHERE lesson_id = 'l1';",
                [],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .expect("query");
        assert_eq!(stored, None, "structure arrives before content");
        assert_eq!(advertised, Some(3));
    }

    #[test]
    fn a_lesson_missing_from_a_later_manifest_is_withdrawn_not_deleted() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1", "l2"]));
        connection
            .execute(
                "UPDATE lessons SET content_version = 3, body_markdown = 'body' WHERE lesson_id = 'l2';",
                [],
            )
            .expect("simulate a download");

        apply(&mut connection, &manifest_json(&["l1"]));

        let withdrawn: Option<String> = connection
            .query_row(
                "SELECT withdrawn_at FROM lessons WHERE lesson_id = 'l2';",
                [],
                |row| row.get(0),
            )
            .expect("query");
        assert!(withdrawn.is_some(), "downloaded content is kept and marked");
    }

    #[test]
    fn a_never_downloaded_lesson_missing_from_a_manifest_is_dropped() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1", "l2"]));
        apply(&mut connection, &manifest_json(&["l1"]));

        let remaining: i64 = connection
            .query_row(
                "SELECT count(*) FROM lessons WHERE lesson_id = 'l2';",
                [],
                |row| row.get(0),
            )
            .expect("query");
        assert_eq!(remaining, 0, "it holds nothing of the user's");
    }

    #[test]
    fn a_withdrawn_lesson_that_reappears_is_unmarked() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1", "l2"]));
        connection
            .execute(
                "UPDATE lessons SET content_version = 3 WHERE lesson_id = 'l2';",
                [],
            )
            .expect("simulate a download");
        apply(&mut connection, &manifest_json(&["l1"]));
        apply(&mut connection, &manifest_json(&["l1", "l2"]));

        let withdrawn: Option<String> = connection
            .query_row(
                "SELECT withdrawn_at FROM lessons WHERE lesson_id = 'l2';",
                [],
                |row| row.get(0),
            )
            .expect("query");
        assert!(withdrawn.is_none());
    }

    #[test]
    fn writing_a_package_fills_in_body_examples_and_translations() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1"]));

        let package: Package = serde_json::from_str(
            r##"{
                "entity_type":"LESSON","entity_id":"l1","content_version":3,"module_id":"m1",
                "slug":"signals","title":"Signals","body_markdown":"# Signals","order":1,
                "code_examples":[{"caption":"c","code":"x","language":"typescript","order":1}],
                "translations":[{"locale":"tr","title":"Sinyaller","body_markdown":"# Sinyaller"}]
            }"##,
        )
        .expect("parse package");

        let tx = connection.transaction().expect("tx");
        assert_eq!(write_package(&tx, &package, "aa", 10).expect("write"), 1);
        tx.commit().expect("commit");

        let body: Option<String> = connection
            .query_row(
                "SELECT body_markdown FROM lessons WHERE lesson_id = 'l1';",
                [],
                |row| row.get(0),
            )
            .expect("query");
        assert_eq!(body.as_deref(), Some("# Signals"));

        let examples: i64 = connection
            .query_row("SELECT count(*) FROM code_examples;", [], |row| row.get(0))
            .expect("query");
        assert_eq!(examples, 1);

        let translations: i64 = connection
            .query_row(
                "SELECT count(*) FROM content_translations WHERE entity_type = 'LESSON';",
                [],
                |row| row.get(0),
            )
            .expect("query");
        assert_eq!(translations, 1);
    }

    #[test]
    fn a_package_for_an_unknown_lesson_writes_nothing() {
        let mut connection = open_in_memory().expect("store");
        let package: Package = serde_json::from_str(
            r##"{"entity_type":"LESSON","entity_id":"ghost","content_version":1,"module_id":"m1",
                 "slug":"s","title":"t","body_markdown":"b"}"##,
        )
        .expect("parse");
        let tx = connection.transaction().expect("tx");
        assert_eq!(write_package(&tx, &package, "aa", 10).expect("write"), 0);
        tx.commit().expect("commit");
    }

    #[test]
    fn clearing_content_keeps_the_lesson_and_its_progress() {
        let mut connection = open_in_memory().expect("store");
        apply(&mut connection, &manifest_json(&["l1"]));
        connection
            .execute(
                "UPDATE lessons SET content_version = 3, body_markdown = 'b' WHERE lesson_id = 'l1';",
                [],
            )
            .expect("simulate a download");
        connection
            .execute(
                "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('u1', 'l1', '2026-09-04T10:00:00.000Z', '2026-09-04T10:00:00.000Z', 'SYNCED');",
                [],
            )
            .expect("progress");

        assert_eq!(clear_lesson_content(&connection, "l1").expect("clear"), 1);

        let (rows, progress): (i64, i64) = connection
            .query_row(
                "SELECT (SELECT count(*) FROM lessons WHERE lesson_id = 'l1'),
                        (SELECT count(*) FROM user_progress WHERE lesson_id = 'l1');",
                [],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .expect("query");
        assert_eq!(rows, 1, "the lesson stays offerable");
        assert_eq!(progress, 1, "deleting content never deletes progress");
    }
}
