//! Local SQLite store.
//!
//! The desktop client keeps a read replica of published content plus its own
//! download queue and progress data. Schema versioning uses SQLite's
//! `user_version` pragma and an ordered list of migration steps, so the store
//! can be upgraded in place across releases without a migration framework.

use std::path::{Path, PathBuf};

use rusqlite::Connection;
use tauri::{AppHandle, Manager};

/// File name of the store inside the resolved application data directory.
const DATABASE_FILE_NAME: &str = "bytelore.db";

/// Ordered schema migrations. Index `i` upgrades the schema from
/// `user_version = i` to `user_version = i + 1`, so steps are append-only:
/// editing or reordering an existing entry would leave already-upgraded
/// installations on a schema that no longer matches what this list describes.
const MIGRATIONS: &[&str] = &[
    // 0 -> 1: baseline. Content, queue and progress tables arrive with the
    // download engine; this step only establishes that the store exists and
    // that the versioning mechanism is wired up.
    "CREATE TABLE app_settings (
         key   TEXT PRIMARY KEY NOT NULL,
         value TEXT NOT NULL
     );",
    // 1 -> 2: the content replica, the download queue, progress, and the sync
    // journal.
    //
    // Structure and content live in the same rows but in separate columns. A
    // track manifest describes every lesson of a track whether or not it has
    // been downloaded, so the library screen can list lessons the user does not
    // hold yet: the `manifest_*` columns carry what the server advertises and
    // the plain columns carry what is actually stored. Comparing the two is the
    // whole of availability -- equal means downloaded, lower means an update is
    // waiting, higher is an anomaly that gets reported and never repaired.
    //
    // Identifiers are UUIDv7 strings matching the server and the manifest, and
    // are stored as text so a value read out of the store compares directly
    // against one read out of a manifest, with no conversion in between.
    "CREATE TABLE tracks (
         track_id            TEXT    PRIMARY KEY NOT NULL,
         slug                TEXT    NOT NULL UNIQUE,
         title               TEXT    NOT NULL,
         description         TEXT,
         icon                TEXT,
         content_version     INTEGER NOT NULL,
         lesson_count        INTEGER NOT NULL DEFAULT 0,
         total_size_bytes    INTEGER NOT NULL DEFAULT 0,
         manifest_etag       TEXT,
         manifest_fetched_at TEXT,
         updated_at          TEXT
     );

     CREATE TABLE modules (
         module_id         TEXT    PRIMARY KEY NOT NULL,
         track_id          TEXT    NOT NULL REFERENCES tracks(track_id) ON DELETE CASCADE,
         title             TEXT    NOT NULL,
         ordinal           INTEGER NOT NULL,
         estimated_minutes INTEGER
     );
     CREATE INDEX idx_modules_track ON modules(track_id, ordinal);

     CREATE TABLE lessons (
         lesson_id                TEXT    PRIMARY KEY NOT NULL,
         track_id                 TEXT    NOT NULL REFERENCES tracks(track_id) ON DELETE CASCADE,
         module_id                TEXT    NOT NULL REFERENCES modules(module_id) ON DELETE CASCADE,
         slug                     TEXT    NOT NULL,
         title                    TEXT    NOT NULL,
         difficulty               TEXT,
         estimated_minutes        INTEGER,
         ordinal                  INTEGER NOT NULL,
         manifest_content_version INTEGER,
         manifest_sha256          TEXT,
         manifest_size_bytes      INTEGER,
         body_markdown            TEXT,
         content_version          INTEGER,
         sha256                   TEXT,
         size_bytes               INTEGER,
         downloaded_at            TEXT,
         withdrawn_at             TEXT
     );
     CREATE INDEX idx_lessons_track ON lessons(track_id);
     CREATE INDEX idx_lessons_module ON lessons(module_id, ordinal);

     -- A package lists its code examples in an array. `position` is that array
     -- index and exists only to give the row a stable key: the declared `order`
     -- is content, and two examples may legitimately carry the same one.
     CREATE TABLE code_examples (
         lesson_id TEXT    NOT NULL REFERENCES lessons(lesson_id) ON DELETE CASCADE,
         position  INTEGER NOT NULL,
         ordinal   INTEGER NOT NULL,
         caption   TEXT,
         code      TEXT    NOT NULL,
         language  TEXT    NOT NULL,
         PRIMARY KEY (lesson_id, position)
     );

     -- `mind_map_id` is the mind map's own identifier, which is what a manifest
     -- names as the entity. Keying this table by track_id instead would leave
     -- the client unable to address the entity it is told to download.
     CREATE TABLE mind_maps (
         mind_map_id              TEXT    PRIMARY KEY NOT NULL,
         track_id                 TEXT    NOT NULL UNIQUE REFERENCES tracks(track_id) ON DELETE CASCADE,
         root_json                TEXT,
         manifest_content_version INTEGER,
         manifest_sha256          TEXT,
         manifest_size_bytes      INTEGER,
         content_version          INTEGER,
         sha256                   TEXT,
         size_bytes               INTEGER,
         downloaded_at            TEXT,
         withdrawn_at             TEXT
     );

     -- Translations of replicated content. They arrive inside the document that
     -- owns them -- a lesson package, or the track manifest for track and
     -- module titles -- so a row carries the version and digest of that
     -- document rather than one of its own.
     CREATE TABLE content_translations (
         entity_type     TEXT NOT NULL,
         entity_id       TEXT NOT NULL,
         locale          TEXT NOT NULL,
         title           TEXT,
         body            TEXT,
         content_version INTEGER,
         sha256          TEXT,
         PRIMARY KEY (entity_type, entity_id, locale)
     );

     -- One row per enqueued entity. The key is the entity alone: an entity is
     -- either in the queue or it is not, and enqueuing it again at the same
     -- version is a no-op rather than a second row.
     CREATE TABLE download_queue (
         entity_id       TEXT    PRIMARY KEY NOT NULL,
         entity_type     TEXT    NOT NULL,
         track_id        TEXT,
         batch_id        TEXT    NOT NULL,
         content_version INTEGER NOT NULL,
         sha256          TEXT    NOT NULL,
         size_bytes      INTEGER NOT NULL,
         received_bytes  INTEGER NOT NULL DEFAULT 0,
         state           TEXT    NOT NULL,
         attempt         INTEGER NOT NULL DEFAULT 0,
         partial_path    TEXT,
         pause_reason    TEXT,
         error_code      TEXT,
         next_attempt_at TEXT,
         enqueued_at     TEXT    NOT NULL,
         updated_at      TEXT    NOT NULL
     );
     CREATE INDEX idx_queue_state ON download_queue(state);
     CREATE INDEX idx_queue_batch ON download_queue(batch_id);

     -- Deliberately carries no foreign key to `lessons`. Progress outlives the
     -- content it refers to: deleting a track to reclaim space has to leave the
     -- completions behind, and a cascade would silently remove them.
     --
     -- `completed_at` is nullable and meaningful. NULL records an explicit
     -- 'marked incomplete', which is a user action that has to survive a sync
     -- round trip; the absence of the row is what means 'never touched'.
     CREATE TABLE user_progress (
         user_id           TEXT NOT NULL,
         lesson_id         TEXT NOT NULL,
         completed_at      TEXT,
         client_updated_at TEXT NOT NULL,
         sync_state        TEXT NOT NULL,
         PRIMARY KEY (user_id, lesson_id)
     );
     CREATE INDEX idx_progress_sync ON user_progress(user_id, sync_state);

     CREATE TABLE sync_log (
         sync_log_id INTEGER PRIMARY KEY AUTOINCREMENT,
         kind        TEXT    NOT NULL,
         subject_id  TEXT,
         outcome     TEXT    NOT NULL,
         detail      TEXT,
         occurred_at TEXT    NOT NULL
     );
     CREATE INDEX idx_sync_log_time ON sync_log(occurred_at);",
];

#[derive(Debug, thiserror::Error)]
pub enum StoreError {
    #[error("sqlite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("could not resolve the application data directory: {0}")]
    DataDir(String),
    #[error("could not create the application data directory: {0}")]
    CreateDataDir(#[from] std::io::Error),
}

/// Resolves the store path from the platform's application data directory.
///
/// The location always comes from the runtime's directory resolver. Writing to
/// a path relative to the current working directory or to the repository would
/// put a user's downloaded content and progress somewhere that depends on how
/// the binary happened to be launched.
pub fn database_path(app: &AppHandle) -> Result<PathBuf, StoreError> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| StoreError::DataDir(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join(DATABASE_FILE_NAME))
}

/// Directory holding partially downloaded packages.
///
/// It sits beside the store, in application data, for the same reason: a
/// partial file has to survive a restart so that a resumed transfer can pick up
/// where it stopped, which rules out the system temp directory on platforms
/// that clean it between sessions.
pub fn partial_directory(app: &AppHandle) -> Result<PathBuf, StoreError> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| StoreError::DataDir(e.to_string()))?
        .join("partials");
    std::fs::create_dir_all(&dir)?;
    Ok(dir)
}

/// Opens the store at `path` and brings its schema up to date.
pub fn open(path: &Path) -> Result<Connection, StoreError> {
    let mut connection = Connection::open(path)?;
    configure(&connection)?;
    migrate(&mut connection)?;
    Ok(connection)
}

/// Opens an in-memory store with the same schema, for tests.
#[cfg(test)]
pub fn open_in_memory() -> Result<Connection, StoreError> {
    let mut connection = Connection::open_in_memory()?;
    configure(&connection)?;
    migrate(&mut connection)?;
    Ok(connection)
}

fn configure(connection: &Connection) -> Result<(), StoreError> {
    // WAL keeps reads from blocking while the download engine writes queue
    // updates; foreign keys are off by default in SQLite and have to be asked
    // for on every connection.
    connection.pragma_update(None, "journal_mode", "WAL")?;
    connection.pragma_update(None, "foreign_keys", "ON")?;
    Ok(())
}

/// Applies every migration the store has not seen yet, in one transaction per
/// step, and advances `user_version` alongside it.
fn migrate(connection: &mut Connection) -> Result<(), StoreError> {
    let current: i64 = connection.query_row("PRAGMA user_version;", [], |row| row.get(0))?;
    let current = current.max(0) as usize;

    for (index, statement) in MIGRATIONS.iter().enumerate().skip(current) {
        let transaction = connection.transaction()?;
        transaction.execute_batch(statement)?;
        // `pragma_update` does not accept a bound parameter for the value, and
        // `index` is a loop counter over a compile-time constant, not input.
        transaction.pragma_update(None, "user_version", (index + 1) as i64)?;
        transaction.commit()?;
    }

    Ok(())
}

/// Current schema version of an open store.
pub fn schema_version(connection: &Connection) -> Result<i64, StoreError> {
    Ok(connection.query_row("PRAGMA user_version;", [], |row| row.get(0))?)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn open_migrated_in_memory() -> Connection {
        open_in_memory().expect("in-memory store")
    }

    fn table_exists(connection: &Connection, name: &str) -> bool {
        let count: i64 = connection
            .query_row(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?1;",
                [name],
                |row| row.get(0),
            )
            .expect("query sqlite_master");
        count == 1
    }

    #[test]
    fn migrates_a_fresh_store_to_the_latest_version() {
        let connection = open_migrated_in_memory();
        assert_eq!(
            schema_version(&connection).expect("version"),
            MIGRATIONS.len() as i64
        );
    }

    #[test]
    fn migrating_twice_is_a_no_op() {
        let mut connection = open_migrated_in_memory();
        let after_first = schema_version(&connection).expect("version");

        // Re-running must not replay a step; replaying step 0 would fail on
        // `CREATE TABLE` and that failure is the regression this guards.
        migrate(&mut connection).expect("second migrate must be a no-op");

        assert_eq!(schema_version(&connection).expect("version"), after_first);
    }

    #[test]
    fn baseline_migration_creates_the_settings_table() {
        let connection = open_migrated_in_memory();
        assert!(table_exists(&connection, "app_settings"));
    }

    #[test]
    fn replica_and_operational_tables_exist() {
        let connection = open_migrated_in_memory();
        for name in [
            "tracks",
            "modules",
            "lessons",
            "code_examples",
            "mind_maps",
            "content_translations",
            "download_queue",
            "user_progress",
            "sync_log",
        ] {
            assert!(table_exists(&connection, name), "missing table {name}");
        }
    }

    #[test]
    fn progress_is_keyed_by_user_and_lesson() {
        let connection = open_migrated_in_memory();
        // Two people sharing one installation keep separate progress over the
        // same downloaded lesson; a key on lesson alone would merge them.
        connection
            .execute(
                "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('user-a', 'lesson-1', '2026-09-04T10:00:00.000Z', '2026-09-04T10:00:00.000Z', 'PENDING');",
                [],
            )
            .expect("first user");
        connection
            .execute(
                "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('user-b', 'lesson-1', NULL, '2026-09-04T10:00:01.000Z', 'PENDING');",
                [],
            )
            .expect("second user over the same lesson");

        let rows: i64 = connection
            .query_row("SELECT count(*) FROM user_progress;", [], |row| row.get(0))
            .expect("count");
        assert_eq!(rows, 2);
    }

    #[test]
    fn a_null_completed_at_is_stored_as_a_value() {
        let connection = open_migrated_in_memory();
        connection
            .execute(
                "INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('user-a', 'lesson-1', NULL, '2026-09-04T10:00:00.000Z', 'PENDING');",
                [],
            )
            .expect("insert");

        // 'Explicitly marked incomplete' is a row whose completed_at is NULL,
        // and it has to be distinguishable from never having been touched --
        // which is the absence of any row at all.
        let present: i64 = connection
            .query_row(
                "SELECT count(*) FROM user_progress WHERE user_id = 'user-a' AND lesson_id = 'lesson-1' AND completed_at IS NULL;",
                [],
                |row| row.get(0),
            )
            .expect("count");
        assert_eq!(present, 1);
    }

    #[test]
    fn deleting_a_track_leaves_progress_untouched() {
        let connection = open_migrated_in_memory();
        connection
            .execute_batch(
                "INSERT INTO tracks (track_id, slug, title, content_version) VALUES ('t1', 'angular-path', 'The Angular Path', 47);
                 INSERT INTO modules (module_id, track_id, title, ordinal) VALUES ('m1', 't1', 'Signals', 1);
                 INSERT INTO lessons (lesson_id, track_id, module_id, slug, title, ordinal) VALUES ('l1', 't1', 'm1', 'signals', 'Signals', 1);
                 INSERT INTO user_progress (user_id, lesson_id, completed_at, client_updated_at, sync_state)
                 VALUES ('user-a', 'l1', '2026-09-04T10:00:00.000Z', '2026-09-04T10:00:00.000Z', 'SYNCED');",
            )
            .expect("seed");

        connection
            .execute("DELETE FROM tracks WHERE track_id = 't1';", [])
            .expect("delete track");

        let lessons: i64 = connection
            .query_row("SELECT count(*) FROM lessons;", [], |row| row.get(0))
            .expect("count lessons");
        let progress: i64 = connection
            .query_row("SELECT count(*) FROM user_progress;", [], |row| row.get(0))
            .expect("count progress");

        assert_eq!(lessons, 0, "content cascades away with its track");
        assert_eq!(progress, 1, "progress survives the content it refers to");
    }

    #[test]
    fn opens_a_store_on_disk_under_a_temporary_directory() {
        // Tests never touch the real application data directory: a developer's
        // downloaded content and progress live there.
        let dir = tempfile::tempdir().expect("temp dir");
        let path = dir.path().join("bytelore-test.db");

        let connection = open(&path).expect("open store");
        assert_eq!(
            schema_version(&connection).expect("version"),
            MIGRATIONS.len() as i64
        );
        assert!(path.exists());
    }
}
