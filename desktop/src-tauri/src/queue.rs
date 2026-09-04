//! The download queue: rows, reads and the transitions the engine drives.
//!
//! The queue lives in SQLite rather than in memory, because it has to survive a
//! restart. Everything the engine needs to resume a transfer it never finished
//! -- which version it was fetching, which digest it expected, how many
//! attempts it has already spent -- is in the row, so a process that dies
//! mid-download loses nothing but the socket.

use rusqlite::{params, Connection, Row};

use crate::db::now_iso;
use crate::model::{EntityType, PauseReason, QueueState};

#[derive(Debug, Clone)]
pub struct QueueRow {
    pub entity_id: String,
    pub entity_type: EntityType,
    pub track_id: Option<String>,
    pub batch_id: String,
    pub content_version: i64,
    pub sha256: String,
    pub size_bytes: i64,
    pub received_bytes: i64,
    pub state: QueueState,
    pub attempt: i64,
    pub partial_path: Option<String>,
    pub pause_reason: Option<PauseReason>,
    pub error_code: Option<String>,
    pub next_attempt_at: Option<String>,
}

/// Aggregate progress for the user-visible operation an entry belongs to.
#[derive(Debug, Clone, Default)]
pub struct BatchTotals {
    pub completed_entities: i64,
    pub failed_entities: i64,
    pub total_entities: i64,
    pub received_bytes: i64,
    pub total_bytes: i64,
    pub open_entities: i64,
}

const COLUMNS: &str = "entity_id, entity_type, track_id, batch_id, content_version, sha256, \
                       size_bytes, received_bytes, state, attempt, partial_path, pause_reason, \
                       error_code, next_attempt_at";

fn row_from(row: &Row<'_>) -> rusqlite::Result<QueueRow> {
    let entity_type: String = row.get(1)?;
    let state: String = row.get(8)?;
    let pause_reason: Option<String> = row.get(11)?;
    Ok(QueueRow {
        entity_id: row.get(0)?,
        entity_type: entity_type.parse().unwrap_or(EntityType::Lesson),
        track_id: row.get(2)?,
        batch_id: row.get(3)?,
        content_version: row.get(4)?,
        sha256: row.get(5)?,
        size_bytes: row.get(6)?,
        received_bytes: row.get(7)?,
        state: state.parse().unwrap_or(QueueState::Queued),
        attempt: row.get(9)?,
        partial_path: row.get(10)?,
        pause_reason: pause_reason.and_then(|value| value.parse().ok()),
        error_code: row.get(12)?,
        next_attempt_at: row.get(13)?,
    })
}

/// Inserts an entry, or replaces one that is already terminal.
///
/// A `DONE` or `FAILED` row is a record of what happened, not a claim on the
/// entity, so enqueuing the same entity again replaces it. A row that is still
/// working is left alone and reported as already queued.
pub enum EnqueueOutcome {
    Inserted,
    AlreadyQueued,
}

#[allow(clippy::too_many_arguments)]
pub fn enqueue(
    connection: &Connection,
    entity_id: &str,
    entity_type: EntityType,
    track_id: Option<&str>,
    batch_id: &str,
    content_version: i64,
    sha256: &str,
    size_bytes: i64,
) -> rusqlite::Result<EnqueueOutcome> {
    if let Some(existing) = find(connection, entity_id)? {
        if !existing.state.is_terminal() {
            return Ok(EnqueueOutcome::AlreadyQueued);
        }
        remove(connection, entity_id)?;
    }

    let now = now_iso();
    connection.execute(
        "INSERT INTO download_queue
             (entity_id, entity_type, track_id, batch_id, content_version, sha256, size_bytes,
              received_bytes, state, attempt, partial_path, pause_reason, error_code,
              next_attempt_at, enqueued_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 0, 'QUEUED', 0, NULL, NULL, NULL, NULL, ?8, ?8);",
        params![
            entity_id,
            entity_type.as_str(),
            track_id,
            batch_id,
            content_version,
            sha256,
            size_bytes,
            now
        ],
    )?;
    Ok(EnqueueOutcome::Inserted)
}

pub fn find(connection: &Connection, entity_id: &str) -> rusqlite::Result<Option<QueueRow>> {
    let mut statement = connection.prepare(&format!(
        "SELECT {COLUMNS} FROM download_queue WHERE entity_id = ?1;"
    ))?;
    let mut rows = statement.query(params![entity_id])?;
    match rows.next()? {
        Some(row) => Ok(Some(row_from(row)?)),
        None => Ok(None),
    }
}

pub fn all(connection: &Connection) -> rusqlite::Result<Vec<QueueRow>> {
    let mut statement = connection.prepare(&format!(
        "SELECT {COLUMNS} FROM download_queue ORDER BY enqueued_at;"
    ))?;
    let rows = statement.query_map([], row_from)?;
    rows.collect()
}

/// Entries the engine may pick up now.
///
/// `VERIFYING` is included and `DOWNLOADING` is not. A restart turns
/// `DOWNLOADING` back into `QUEUED` with its partial intact, but a `VERIFYING`
/// entry has a complete file by definition, so it is re-verified rather than
/// re-downloaded: throwing away a transfer that already succeeded because the
/// process died at the wrong moment is pure waste.
pub fn ready(connection: &Connection, now: &str, limit: usize) -> rusqlite::Result<Vec<QueueRow>> {
    let mut statement = connection.prepare(&format!(
        "SELECT {COLUMNS} FROM download_queue
         WHERE (state = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= ?1))
            OR state = 'VERIFYING'
         ORDER BY enqueued_at
         LIMIT ?2;"
    ))?;
    let rows = statement.query_map(params![now, limit as i64], row_from)?;
    rows.collect()
}

/// Whether any entry still owes the user an outcome.
pub fn has_open_work(connection: &Connection) -> rusqlite::Result<bool> {
    let count: i64 = connection.query_row(
        "SELECT count(*) FROM download_queue WHERE state IN ('QUEUED', 'DOWNLOADING', 'VERIFYING');",
        [],
        |row| row.get(0),
    )?;
    Ok(count > 0)
}

/// The earliest time a backed-off entry becomes eligible.
pub fn earliest_next_attempt(connection: &Connection) -> rusqlite::Result<Option<String>> {
    connection.query_row(
        "SELECT min(next_attempt_at) FROM download_queue WHERE state = 'QUEUED' AND next_attempt_at IS NOT NULL;",
        [],
        |row| row.get(0),
    )
}

pub fn batch_totals(connection: &Connection, batch_id: &str) -> rusqlite::Result<BatchTotals> {
    connection.query_row(
        "SELECT
             sum(CASE WHEN state = 'DONE' THEN 1 ELSE 0 END),
             sum(CASE WHEN state = 'FAILED' THEN 1 ELSE 0 END),
             count(*),
             sum(CASE WHEN state = 'DONE' THEN size_bytes ELSE received_bytes END),
             sum(size_bytes),
             sum(CASE WHEN state IN ('QUEUED', 'DOWNLOADING', 'VERIFYING', 'PAUSED') THEN 1 ELSE 0 END)
         FROM download_queue WHERE batch_id = ?1;",
        params![batch_id],
        |row| {
            Ok(BatchTotals {
                completed_entities: row.get::<_, Option<i64>>(0)?.unwrap_or(0),
                failed_entities: row.get::<_, Option<i64>>(1)?.unwrap_or(0),
                total_entities: row.get::<_, i64>(2)?,
                received_bytes: row.get::<_, Option<i64>>(3)?.unwrap_or(0),
                total_bytes: row.get::<_, Option<i64>>(4)?.unwrap_or(0),
                open_entities: row.get::<_, Option<i64>>(5)?.unwrap_or(0),
            })
        },
    )
}

/// Writes a state transition. Every field the UI reads travels together so that
/// no observer can see a state without the error code or byte count that
/// belongs to it.
#[allow(clippy::too_many_arguments)]
pub fn set_state(
    connection: &Connection,
    entity_id: &str,
    state: QueueState,
    attempt: i64,
    received_bytes: i64,
    partial_path: Option<&str>,
    pause_reason: Option<PauseReason>,
    error_code: Option<&str>,
    next_attempt_at: Option<&str>,
) -> rusqlite::Result<()> {
    connection.execute(
        "UPDATE download_queue
         SET state = ?2, attempt = ?3, received_bytes = ?4, partial_path = ?5,
             pause_reason = ?6, error_code = ?7, next_attempt_at = ?8, updated_at = ?9
         WHERE entity_id = ?1;",
        params![
            entity_id,
            state.as_str(),
            attempt,
            received_bytes,
            partial_path,
            pause_reason.map(|r| r.as_str()),
            error_code,
            next_attempt_at,
            now_iso()
        ],
    )?;
    Ok(())
}

/// Points an entry at a different version of the same entity, after the server
/// said the one it was asking for is no longer current.
pub fn replan(
    connection: &Connection,
    entity_id: &str,
    content_version: i64,
    sha256: &str,
    size_bytes: i64,
) -> rusqlite::Result<()> {
    connection.execute(
        "UPDATE download_queue
         SET content_version = ?2, sha256 = ?3, size_bytes = ?4, received_bytes = 0,
             partial_path = NULL, state = 'QUEUED', next_attempt_at = NULL, updated_at = ?5
         WHERE entity_id = ?1;",
        params![entity_id, content_version, sha256, size_bytes, now_iso()],
    )?;
    Ok(())
}

pub fn remove(connection: &Connection, entity_id: &str) -> rusqlite::Result<()> {
    connection.execute(
        "DELETE FROM download_queue WHERE entity_id = ?1;",
        params![entity_id],
    )?;
    Ok(())
}

/// Pauses every entry that is still working, optionally within one batch.
pub fn pause(
    connection: &Connection,
    batch_id: Option<&str>,
    reason: PauseReason,
) -> rusqlite::Result<usize> {
    let changed = match batch_id {
        Some(batch_id) => connection.execute(
            "UPDATE download_queue SET state = 'PAUSED', pause_reason = ?2, updated_at = ?3
             WHERE batch_id = ?1 AND state IN ('QUEUED', 'DOWNLOADING', 'VERIFYING');",
            params![batch_id, reason.as_str(), now_iso()],
        )?,
        None => connection.execute(
            "UPDATE download_queue SET state = 'PAUSED', pause_reason = ?1, updated_at = ?2
             WHERE state IN ('QUEUED', 'DOWNLOADING', 'VERIFYING');",
            params![reason.as_str(), now_iso()],
        )?,
    };
    Ok(changed)
}

/// Returns paused entries to the queue.
///
/// One command clears both pause reasons. A disk that filled up and a user who
/// pressed pause are the same situation from the queue's point of view: work
/// that stopped and is now allowed to continue.
pub fn resume(connection: &Connection, batch_id: Option<&str>) -> rusqlite::Result<usize> {
    let changed = match batch_id {
        Some(batch_id) => connection.execute(
            "UPDATE download_queue
             SET state = 'QUEUED', pause_reason = NULL, next_attempt_at = NULL, updated_at = ?2
             WHERE batch_id = ?1 AND state = 'PAUSED';",
            params![batch_id, now_iso()],
        )?,
        None => connection.execute(
            "UPDATE download_queue
             SET state = 'QUEUED', pause_reason = NULL, next_attempt_at = NULL, updated_at = ?1
             WHERE state = 'PAUSED';",
            params![now_iso()],
        )?,
    };
    Ok(changed)
}

/// Moves failed entries back to `QUEUED` with their attempt counter reset.
pub fn retry(connection: &Connection, entity_id: Option<&str>) -> rusqlite::Result<usize> {
    let changed = match entity_id {
        Some(entity_id) => connection.execute(
            "UPDATE download_queue
             SET state = 'QUEUED', attempt = 0, error_code = NULL, next_attempt_at = NULL, updated_at = ?2
             WHERE entity_id = ?1 AND state = 'FAILED';",
            params![entity_id, now_iso()],
        )?,
        None => connection.execute(
            "UPDATE download_queue
             SET state = 'QUEUED', attempt = 0, error_code = NULL, next_attempt_at = NULL, updated_at = ?1
             WHERE state = 'FAILED';",
            params![now_iso()],
        )?,
    };
    Ok(changed)
}

/// Entries of a batch that a cancel removes: everything except what already
/// arrived. Cancelling a download does not undo what has already been written
/// into the replica.
pub fn cancellable(connection: &Connection, batch_id: &str) -> rusqlite::Result<Vec<QueueRow>> {
    let mut statement = connection.prepare(&format!(
        "SELECT {COLUMNS} FROM download_queue WHERE batch_id = ?1 AND state <> 'DONE';"
    ))?;
    let rows = statement.query_map(params![batch_id], row_from)?;
    rows.collect()
}

/// A restart leaves entries mid-transfer. `DOWNLOADING` becomes `QUEUED` with
/// its partial file intact, so the next attempt resumes with `Range`.
pub fn recover_after_restart(connection: &Connection) -> rusqlite::Result<usize> {
    let changed = connection.execute(
        "UPDATE download_queue SET state = 'QUEUED', next_attempt_at = NULL, updated_at = ?1
         WHERE state = 'DOWNLOADING';",
        params![now_iso()],
    )?;
    Ok(changed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::open_in_memory;

    fn seeded() -> Connection {
        let connection = open_in_memory().expect("store");
        enqueue(
            &connection,
            "e1",
            EntityType::Lesson,
            Some("t1"),
            "b1",
            12,
            "abc",
            100,
        )
        .expect("enqueue");
        connection
    }

    #[test]
    fn a_second_enqueue_of_working_entry_is_reported_as_already_queued() {
        let connection = seeded();
        let outcome = enqueue(
            &connection,
            "e1",
            EntityType::Lesson,
            Some("t1"),
            "b2",
            12,
            "abc",
            100,
        )
        .expect("enqueue");
        assert!(matches!(outcome, EnqueueOutcome::AlreadyQueued));
    }

    #[test]
    fn a_terminal_entry_can_be_enqueued_again() {
        let connection = seeded();
        set_state(
            &connection,
            "e1",
            QueueState::Failed,
            3,
            0,
            None,
            None,
            Some("DIGEST_MISMATCH"),
            None,
        )
        .expect("fail it");

        let outcome = enqueue(
            &connection,
            "e1",
            EntityType::Lesson,
            Some("t1"),
            "b2",
            13,
            "def",
            120,
        )
        .expect("enqueue");
        assert!(matches!(outcome, EnqueueOutcome::Inserted));

        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 0);
        assert_eq!(row.content_version, 13);
    }

    #[test]
    fn a_restart_returns_downloading_entries_to_the_queue() {
        let connection = seeded();
        set_state(
            &connection,
            "e1",
            QueueState::Downloading,
            0,
            40,
            Some("e1.part"),
            None,
            None,
            None,
        )
        .expect("downloading");

        assert_eq!(recover_after_restart(&connection).expect("recover"), 1);
        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(
            row.partial_path.as_deref(),
            Some("e1.part"),
            "the partial has to survive so the next attempt can resume"
        );
    }

    #[test]
    fn a_verifying_entry_survives_a_restart_as_verifying() {
        let connection = seeded();
        set_state(
            &connection,
            "e1",
            QueueState::Verifying,
            0,
            100,
            Some("e1.part"),
            None,
            None,
            None,
        )
        .expect("verifying");

        recover_after_restart(&connection).expect("recover");
        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(
            row.state,
            QueueState::Verifying,
            "a complete file is re-verified, never re-downloaded"
        );
        assert_eq!(ready(&connection, &now_iso(), 3).expect("ready").len(), 1);
    }

    #[test]
    fn a_backed_off_entry_is_not_ready_before_its_time() {
        let connection = seeded();
        set_state(
            &connection,
            "e1",
            QueueState::Queued,
            1,
            0,
            None,
            None,
            Some("INTERNAL_ERROR"),
            Some("2999-01-01T00:00:00.000Z"),
        )
        .expect("backoff");

        assert!(ready(&connection, &now_iso(), 3).expect("ready").is_empty());
        assert!(!ready(&connection, "2999-06-01T00:00:00.000Z", 3)
            .expect("ready")
            .is_empty());
    }

    #[test]
    fn pausing_and_resuming_move_only_open_entries() {
        let connection = seeded();
        enqueue(
            &connection,
            "e2",
            EntityType::MindMap,
            Some("t1"),
            "b1",
            5,
            "def",
            50,
        )
        .expect("enqueue");
        set_state(
            &connection,
            "e2",
            QueueState::Done,
            0,
            50,
            None,
            None,
            None,
            None,
        )
        .expect("done");

        assert_eq!(
            pause(&connection, None, PauseReason::User).expect("pause"),
            1
        );
        assert_eq!(
            find(&connection, "e2").expect("find").expect("row").state,
            QueueState::Done,
            "a completed entry is not something that can be paused"
        );
        assert_eq!(resume(&connection, None).expect("resume"), 1);
    }

    #[test]
    fn retry_resets_the_attempt_counter() {
        let connection = seeded();
        set_state(
            &connection,
            "e1",
            QueueState::Failed,
            3,
            0,
            None,
            None,
            Some("DIGEST_MISMATCH"),
            None,
        )
        .expect("fail");

        assert_eq!(retry(&connection, None).expect("retry"), 1);
        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(row.attempt, 0);
        assert_eq!(row.error_code, None);
    }

    #[test]
    fn batch_totals_count_entities_and_bytes() {
        let connection = seeded();
        enqueue(
            &connection,
            "e2",
            EntityType::Lesson,
            Some("t1"),
            "b1",
            3,
            "def",
            50,
        )
        .expect("enqueue");
        set_state(
            &connection,
            "e2",
            QueueState::Done,
            0,
            50,
            None,
            None,
            None,
            None,
        )
        .expect("done");

        let totals = batch_totals(&connection, "b1").expect("totals");
        assert_eq!(totals.total_entities, 2);
        assert_eq!(totals.completed_entities, 1);
        assert_eq!(totals.total_bytes, 150);
        assert_eq!(totals.received_bytes, 50);
        assert_eq!(totals.open_entities, 1);
    }

    #[test]
    fn ready_never_hands_out_more_than_the_cap() {
        let connection = seeded();
        for index in 2..=5 {
            enqueue(
                &connection,
                &format!("e{index}"),
                EntityType::Lesson,
                Some("t1"),
                "b1",
                1,
                "abc",
                10,
            )
            .expect("enqueue");
        }

        // The cap is on the queue rather than per track, so starting a second
        // track download does not multiply connections.
        let ready = ready(&connection, &now_iso(), 3).expect("ready");
        assert_eq!(ready.len(), 3);
    }

    #[test]
    fn a_storage_pause_records_why_and_resume_clears_it() {
        let connection = seeded();
        pause(&connection, None, PauseReason::InsufficientStorage).expect("pause");

        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(row.state, QueueState::Paused);
        assert_eq!(row.pause_reason, Some(PauseReason::InsufficientStorage));
        assert_eq!(row.attempt, 0, "a full disk costs no attempt");

        // One command clears both reasons: from the queue's point of view a
        // user pause and a full disk are the same stopped work.
        resume(&connection, None).expect("resume");
        let row = find(&connection, "e1").expect("find").expect("row");
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.pause_reason, None);
    }
}
