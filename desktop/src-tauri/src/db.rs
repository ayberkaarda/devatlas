//! Shared access to the single SQLite connection.
//!
//! One connection behind a mutex rather than a pool. The store is a local file
//! with one writer -- this process -- and SQLite serializes writers anyway; a
//! pool would add a way for two transactions to interleave without adding any
//! concurrency the engine can use.
//!
//! The lock is never held across an `await`. Every database call in this crate
//! is synchronous and short, and holding the connection while a transfer runs
//! would turn a slow network into a frozen UI.

use std::sync::Mutex;

use chrono::SecondsFormat;
use rusqlite::Connection;

use crate::error::CommandError;

pub struct Db {
    connection: Mutex<Connection>,
}

impl Db {
    pub fn new(connection: Connection) -> Self {
        Self {
            connection: Mutex::new(connection),
        }
    }

    /// Runs `f` against the connection.
    pub fn with<T>(
        &self,
        f: impl FnOnce(&Connection) -> rusqlite::Result<T>,
    ) -> Result<T, CommandError> {
        let guard = self
            .connection
            .lock()
            .map_err(|_| CommandError::store("the store lock was poisoned"))?;
        f(&guard).map_err(CommandError::from)
    }

    /// Runs `f` inside a transaction, committing only if it returns `Ok`.
    pub fn transaction<T>(
        &self,
        f: impl FnOnce(&rusqlite::Transaction<'_>) -> rusqlite::Result<T>,
    ) -> Result<T, CommandError> {
        let mut guard = self
            .connection
            .lock()
            .map_err(|_| CommandError::store("the store lock was poisoned"))?;
        let transaction = guard.transaction().map_err(CommandError::from)?;
        let value = f(&transaction).map_err(CommandError::from)?;
        transaction.commit().map_err(CommandError::from)?;
        Ok(value)
    }
}

/// The timestamp format both protocol surfaces use: ISO-8601 UTC with
/// milliseconds and a `Z`. A value written here can move to the REST API
/// untouched, which is the point of fixing the format rather than the type.
pub fn now_iso() -> String {
    chrono::Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true)
}

/// Formats an arbitrary instant the same way.
pub fn to_iso(value: chrono::DateTime<chrono::Utc>) -> String {
    value.to_rfc3339_opts(SecondsFormat::Millis, true)
}

/// Parses a stored timestamp back. A value the store cannot parse is treated as
/// absent rather than as an error: a scheduling hint is not worth failing a
/// download over.
pub fn parse_iso(value: &str) -> Option<chrono::DateTime<chrono::Utc>> {
    chrono::DateTime::parse_from_rfc3339(value)
        .ok()
        .map(|value| value.with_timezone(&chrono::Utc))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timestamps_round_trip_through_the_wire_format() {
        let now = now_iso();
        assert!(now.ends_with('Z'), "{now} must be UTC");
        // `2026-09-04T09:12:44.117Z` -- 24 characters, milliseconds always
        // present so that string comparison orders values correctly.
        assert_eq!(now.len(), 24, "{now}");
        assert!(parse_iso(&now).is_some());
    }

    #[test]
    fn a_transaction_that_fails_writes_nothing() {
        let db = Db::new(crate::store::open_in_memory().expect("store"));
        let outcome = db.transaction(|tx| {
            tx.execute(
                "INSERT INTO app_settings (key, value) VALUES ('locale', 'tr');",
                [],
            )?;
            // Same key twice: the second insert violates the primary key and
            // takes the first one down with it.
            tx.execute(
                "INSERT INTO app_settings (key, value) VALUES ('locale', 'en');",
                [],
            )?;
            Ok(())
        });

        assert!(outcome.is_err());
        let rows: i64 = db
            .with(|c| c.query_row("SELECT count(*) FROM app_settings;", [], |row| row.get(0)))
            .expect("count");
        assert_eq!(rows, 0);
    }
}
