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
const DATABASE_FILE_NAME: &str = "devatlas.db";

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

/// Opens the store at `path` and brings its schema up to date.
pub fn open(path: &Path) -> Result<Connection, StoreError> {
    let mut connection = Connection::open(path)?;
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
        let mut connection = Connection::open_in_memory().expect("in-memory store");
        configure(&connection).expect("configure");
        migrate(&mut connection).expect("migrate");
        connection
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
        let count: i64 = connection
            .query_row(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'app_settings';",
                [],
                |row| row.get(0),
            )
            .expect("query sqlite_master");
        assert_eq!(count, 1);
    }

    #[test]
    fn opens_a_store_on_disk_under_a_temporary_directory() {
        // Tests never touch the real application data directory: a developer's
        // downloaded content and progress live there.
        let dir = tempfile::tempdir().expect("temp dir");
        let path = dir.path().join("devatlas-test.db");

        let connection = open(&path).expect("open store");
        assert_eq!(
            schema_version(&connection).expect("version"),
            MIGRATIONS.len() as i64
        );
        assert!(path.exists());
    }
}
