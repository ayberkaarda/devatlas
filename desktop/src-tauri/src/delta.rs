//! Comparing what is stored against a freshly fetched manifest.
//!
//! "Update your library" reads the network and downloads nothing. It reports
//! what a download would do, and the counts it reports are of entities rather
//! than bytes, because "3 lessons updated" is what a reader recognises.
//!
//! A delta never grows the library. Only entities the user already holds are
//! considered for update; content they have never downloaded is counted
//! separately and left alone, because acquiring content is a deliberate action
//! and not a side effect of pressing refresh.

use serde::Serialize;

use crate::db::Db;
use crate::error::CommandError;
use crate::model::EntityType;

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct DeltaSummary {
    pub checked_tracks: i64,
    pub updated_entities: i64,
    pub withdrawn_entities: i64,
    /// Counted separately from `updatedEntities` so the badge can read
    /// "3 lessons updated" rather than "15 changes".
    pub new_entities_available: i64,
    pub anomalies: Vec<Anomaly>,
}

/// An entity whose local version is *ahead* of the manifest, which should be
/// impossible: versions only increase.
///
/// It means either a restored backup or a server rollback. Nothing is deleted
/// and nothing is overwritten -- the local copy keeps being served and the
/// situation is surfaced, because silently discarding a user's content on the
/// strength of an anomaly is worse than a warning.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Anomaly {
    pub entity_id: String,
    pub entity_type: EntityType,
    pub local_version: i64,
    pub manifest_version: i64,
}

/// One entity as the comparison sees it.
#[derive(Debug, Clone)]
pub struct EntityState {
    pub entity_id: String,
    pub entity_type: EntityType,
    /// `None` when the entity has never been downloaded.
    pub stored_version: Option<i64>,
    /// `None` when the manifest no longer lists it.
    pub manifest_version: Option<i64>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct Comparison {
    pub updated: Vec<String>,
    pub withdrawn: Vec<String>,
    pub new_available: Vec<String>,
    pub anomalies: Vec<Anomaly>,
}

/// The comparison itself, with no network and no store in the way.
pub fn compare(entities: &[EntityState]) -> Comparison {
    let mut comparison = Comparison::default();
    for entity in entities {
        match (entity.stored_version, entity.manifest_version) {
            // Not downloaded: available, but not an update.
            (None, Some(_)) => comparison.new_available.push(entity.entity_id.clone()),
            // Present locally, absent from the manifest: unpublished or deleted
            // upstream. Kept on disk and marked.
            (Some(_), None) => comparison.withdrawn.push(entity.entity_id.clone()),
            (Some(local), Some(remote)) if local < remote => {
                comparison.updated.push(entity.entity_id.clone())
            }
            (Some(local), Some(remote)) if local > remote => comparison.anomalies.push(Anomaly {
                entity_id: entity.entity_id.clone(),
                entity_type: entity.entity_type,
                local_version: local,
                manifest_version: remote,
            }),
            _ => {}
        }
    }
    comparison
}

/// Reads the entities of one track out of the store, after a manifest has been
/// applied to it.
pub fn entities_of_track(db: &Db, track_id: &str) -> Result<Vec<EntityState>, CommandError> {
    let track_id = track_id.to_string();
    db.with(move |connection| {
        let mut entities = Vec::new();

        let mut lessons = connection.prepare(
            "SELECT lesson_id, content_version, manifest_content_version, withdrawn_at
             FROM lessons WHERE track_id = ?1;",
        )?;
        let rows = lessons.query_map([&track_id], |row| {
            let withdrawn: Option<String> = row.get(3)?;
            let manifest_version: Option<i64> = row.get(2)?;
            Ok(EntityState {
                entity_id: row.get(0)?,
                entity_type: EntityType::Lesson,
                stored_version: row.get(1)?,
                // A withdrawn row keeps its last known manifest version in the
                // column; the comparison has to see the absence instead.
                manifest_version: if withdrawn.is_some() {
                    None
                } else {
                    manifest_version
                },
            })
        })?;
        for row in rows {
            entities.push(row?);
        }

        let mut maps = connection.prepare(
            "SELECT mind_map_id, content_version, manifest_content_version, withdrawn_at
             FROM mind_maps WHERE track_id = ?1;",
        )?;
        let rows = maps.query_map([&track_id], |row| {
            let withdrawn: Option<String> = row.get(3)?;
            let manifest_version: Option<i64> = row.get(2)?;
            Ok(EntityState {
                entity_id: row.get(0)?,
                entity_type: EntityType::MindMap,
                stored_version: row.get(1)?,
                manifest_version: if withdrawn.is_some() {
                    None
                } else {
                    manifest_version
                },
            })
        })?;
        for row in rows {
            entities.push(row?);
        }

        Ok(entities)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn entity(id: &str, stored: Option<i64>, manifest: Option<i64>) -> EntityState {
        EntityState {
            entity_id: id.to_string(),
            entity_type: EntityType::Lesson,
            stored_version: stored,
            manifest_version: manifest,
        }
    }

    #[test]
    fn only_entities_already_held_count_as_updates() {
        let comparison = compare(&[
            entity("held-and-current", Some(12), Some(12)),
            entity("held-and-stale", Some(11), Some(12)),
            entity("never-downloaded", None, Some(12)),
        ]);

        assert_eq!(comparison.updated, vec!["held-and-stale".to_string()]);
        assert_eq!(
            comparison.new_available,
            vec!["never-downloaded".to_string()],
            "a delta must not silently grow the library"
        );
        assert!(comparison.withdrawn.is_empty());
        assert!(comparison.anomalies.is_empty());
    }

    #[test]
    fn an_entity_missing_from_the_manifest_is_withdrawn() {
        let comparison = compare(&[entity("gone", Some(12), None)]);
        assert_eq!(comparison.withdrawn, vec!["gone".to_string()]);
    }

    #[test]
    fn a_local_version_ahead_of_the_manifest_is_flagged_and_not_repaired() {
        let comparison = compare(&[entity("restored-from-backup", Some(14), Some(12))]);

        assert_eq!(
            comparison.anomalies,
            vec![Anomaly {
                entity_id: "restored-from-backup".to_string(),
                entity_type: EntityType::Lesson,
                local_version: 14,
                manifest_version: 12,
            }]
        );
        assert!(
            comparison.updated.is_empty(),
            "an anomaly is never turned into a download that would overwrite the local copy"
        );
        assert!(
            comparison.withdrawn.is_empty(),
            "nor into a deletion: a restored backup or a server rollback is not a reason to discard content"
        );
    }

    #[test]
    fn an_entity_neither_held_nor_listed_is_nothing_at_all() {
        assert_eq!(
            compare(&[entity("ghost", None, None)]),
            Comparison::default()
        );
    }

    #[test]
    fn the_store_is_read_back_into_the_same_comparison() {
        use crate::db::Db;
        use crate::manifest::TrackManifest;
        use crate::replica;
        use crate::store::open_in_memory;

        let db = Db::new(open_in_memory().expect("store"));
        let manifest: TrackManifest = serde_json::from_value(serde_json::json!({
            "track_id": "t1",
            "slug": "angular-path",
            "content_version": 47,
            "title": "The Angular Path",
            "modules": [{
                "module_id": "m1", "title": "Signals", "order": 1,
                "lessons": [
                    { "lesson_id": "l1", "slug": "a", "title": "A", "order": 1 },
                    { "lesson_id": "l2", "slug": "b", "title": "B", "order": 2 },
                    { "lesson_id": "l3", "slug": "c", "title": "C", "order": 3 }
                ]
            }],
            "entities": [
                { "entity_type": "LESSON", "entity_id": "l1", "content_version": 5, "sha256": "aa", "size_bytes": 10 },
                { "entity_type": "LESSON", "entity_id": "l2", "content_version": 5, "sha256": "bb", "size_bytes": 10 },
                { "entity_type": "LESSON", "entity_id": "l3", "content_version": 5, "sha256": "cc", "size_bytes": 10 }
            ]
        }))
        .expect("manifest");
        db.transaction(|tx| replica::apply_track_manifest(tx, &manifest, None))
            .expect("apply");

        db.with(|connection| {
            // l1 is current, l2 is behind, l3 was never downloaded.
            connection.execute(
                "UPDATE lessons SET content_version = 5 WHERE lesson_id = 'l1';",
                [],
            )?;
            connection.execute(
                "UPDATE lessons SET content_version = 4 WHERE lesson_id = 'l2';",
                [],
            )?;
            Ok(())
        })
        .expect("seed versions");

        let comparison = compare(&entities_of_track(&db, "t1").expect("read entities"));
        assert_eq!(comparison.updated, vec!["l2".to_string()]);
        assert_eq!(comparison.new_available, vec!["l3".to_string()]);
        assert!(comparison.anomalies.is_empty());
    }

    #[test]
    fn a_withdrawn_row_reads_back_as_absent_from_the_manifest() {
        use crate::db::Db;
        use crate::store::open_in_memory;

        let db = Db::new(open_in_memory().expect("store"));
        db.with(|connection| {
            connection.execute_batch(
                "INSERT INTO tracks (track_id, slug, title, content_version) VALUES ('t1', 's', 'T', 1);
                 INSERT INTO modules (module_id, track_id, title, ordinal) VALUES ('m1', 't1', 'M', 1);
                 INSERT INTO lessons (lesson_id, track_id, module_id, slug, title, ordinal,
                                      content_version, manifest_content_version, withdrawn_at)
                 VALUES ('l1', 't1', 'm1', 'a', 'A', 1, 5, 5, '2026-09-04T10:00:00.000Z');",
            )?;
            Ok(())
        })
        .expect("seed");

        let comparison = compare(&entities_of_track(&db, "t1").expect("read entities"));
        assert_eq!(
            comparison.withdrawn,
            vec!["l1".to_string()],
            "a marked row keeps its last known manifest version in the column, and the comparison has to see the absence instead"
        );
    }
}
