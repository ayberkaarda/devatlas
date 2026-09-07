//! The key-value corner of the store: preferences, sync bookkeeping and the
//! session blob.
//!
//! Theme and locale live here so the window can be themed before it is shown
//! and so a preference set offline is not lost. The two sync fields are not
//! preferences at all -- they belong to the Angular layer, which owns
//! synchronisation but has nowhere durable of its own to record it.

use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};

use crate::model::explicit_option;

pub const KEY_LOCALE: &str = "locale";
pub const KEY_THEME: &str = "theme";
pub const KEY_PREFERENCES_DIRTY_AT: &str = "preferences_dirty_at";
pub const KEY_LAST_SYNC_AT: &str = "last_sync_at";
pub const KEY_SESSION: &str = "session";
/// Which account's progress rows the progress commands read and write.
pub const KEY_ACTIVE_USER: &str = "active_user_id";

const DEFAULT_LOCALE: &str = "en";
const DEFAULT_THEME: &str = "DARK";

/// The scope progress is recorded under when nobody is signed in.
///
/// Progress is keyed by user because the replica is not tied to an account:
/// two people sharing one installation keep separate progress over the same
/// downloaded content. Marking a lesson complete has to work before anyone
/// signs in, so those rows need a scope of their own rather than an error, and
/// the nil identifier is one no server-issued account can collide with.
pub const ANONYMOUS_USER: &str = "00000000-0000-0000-0000-000000000000";

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    pub locale: String,
    pub theme: String,
    /// Set when a preference changes while offline, cleared once the server has
    /// it. Several offline changes collapse into one push of the final state.
    pub preferences_dirty_at: Option<String>,
    pub last_sync_at: Option<String>,
}

/// A partial update. Every field is optional, so clearing one does not require
/// restating the others; the nullable fields use a double option so that an
/// explicit `null` clears the value and an absent key leaves it alone.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingsPatch {
    #[serde(default)]
    pub locale: Option<String>,
    #[serde(default)]
    pub theme: Option<String>,
    #[serde(default, deserialize_with = "explicit_option")]
    pub preferences_dirty_at: Option<Option<String>>,
    #[serde(default, deserialize_with = "explicit_option")]
    pub last_sync_at: Option<Option<String>>,
}

/// What the UI hands over to be kept across restarts.
///
/// Rust never parses, validates, refreshes or transmits the tokens: this is a
/// typed key-value store and nothing more. `userId` is the exception, and it is
/// not an exception to that rule -- it is the key progress rows are filed
/// under, which is storage, not session handling.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct StoredSession {
    pub user_id: String,
    pub access_token: String,
    pub refresh_token: String,
    #[serde(default)]
    pub access_token_expires_at: Option<String>,
}

pub fn get(connection: &Connection, key: &str) -> rusqlite::Result<Option<String>> {
    let mut statement = connection.prepare("SELECT value FROM app_settings WHERE key = ?1;")?;
    let mut rows = statement.query(params![key])?;
    match rows.next()? {
        Some(row) => Ok(Some(row.get(0)?)),
        None => Ok(None),
    }
}

pub fn set(connection: &Connection, key: &str, value: &str) -> rusqlite::Result<()> {
    connection.execute(
        "INSERT INTO app_settings (key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value;",
        params![key, value],
    )?;
    Ok(())
}

pub fn clear(connection: &Connection, key: &str) -> rusqlite::Result<()> {
    connection.execute("DELETE FROM app_settings WHERE key = ?1;", params![key])?;
    Ok(())
}

pub fn read(connection: &Connection) -> rusqlite::Result<AppSettings> {
    Ok(AppSettings {
        locale: get(connection, KEY_LOCALE)?.unwrap_or_else(|| DEFAULT_LOCALE.to_string()),
        theme: get(connection, KEY_THEME)?.unwrap_or_else(|| DEFAULT_THEME.to_string()),
        preferences_dirty_at: get(connection, KEY_PREFERENCES_DIRTY_AT)?,
        last_sync_at: get(connection, KEY_LAST_SYNC_AT)?,
    })
}

pub fn apply(connection: &Connection, patch: &SettingsPatch) -> rusqlite::Result<AppSettings> {
    if let Some(locale) = &patch.locale {
        set(connection, KEY_LOCALE, locale)?;
    }
    if let Some(theme) = &patch.theme {
        set(connection, KEY_THEME, theme)?;
    }
    if let Some(value) = &patch.preferences_dirty_at {
        match value {
            Some(value) => set(connection, KEY_PREFERENCES_DIRTY_AT, value)?,
            None => clear(connection, KEY_PREFERENCES_DIRTY_AT)?,
        }
    }
    if let Some(value) = &patch.last_sync_at {
        match value {
            Some(value) => set(connection, KEY_LAST_SYNC_AT, value)?,
            None => clear(connection, KEY_LAST_SYNC_AT)?,
        }
    }
    read(connection)
}

pub fn active_locale(connection: &Connection) -> rusqlite::Result<String> {
    Ok(get(connection, KEY_LOCALE)?.unwrap_or_else(|| DEFAULT_LOCALE.to_string()))
}

pub fn active_user(connection: &Connection) -> rusqlite::Result<String> {
    Ok(get(connection, KEY_ACTIVE_USER)?.unwrap_or_else(|| ANONYMOUS_USER.to_string()))
}

pub fn store_session(connection: &Connection, session: &StoredSession) -> rusqlite::Result<()> {
    // A struct of owned strings has no serialization failure mode; the empty
    // fallback exists so a store write cannot be turned into a panic.
    let blob = serde_json::to_string(session).unwrap_or_default();
    set(connection, KEY_SESSION, &blob)?;
    set(connection, KEY_ACTIVE_USER, &session.user_id)?;
    Ok(())
}

pub fn load_session(connection: &Connection) -> rusqlite::Result<Option<StoredSession>> {
    Ok(get(connection, KEY_SESSION)?.and_then(|blob| serde_json::from_str(&blob).ok()))
}

/// Forgets the session.
///
/// The active user is forgotten with it, so progress recorded after signing out
/// does not land in the previous account's rows. Nothing is deleted: the rows
/// stay exactly where they are and reappear when that user signs in again.
pub fn clear_session(connection: &Connection) -> rusqlite::Result<()> {
    clear(connection, KEY_SESSION)?;
    clear(connection, KEY_ACTIVE_USER)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::open_in_memory;

    #[test]
    fn defaults_are_returned_for_an_empty_store() {
        let connection = open_in_memory().expect("store");
        let settings = read(&connection).expect("read");
        assert_eq!(settings.locale, "en");
        assert_eq!(settings.preferences_dirty_at, None);
    }

    #[test]
    fn a_patch_touches_only_what_it_names() {
        let connection = open_in_memory().expect("store");
        apply(
            &connection,
            &SettingsPatch {
                locale: Some("tr".to_string()),
                preferences_dirty_at: Some(Some("2026-09-04T08:41:02.310Z".to_string())),
                ..SettingsPatch::default()
            },
        )
        .expect("apply");

        let after = apply(
            &connection,
            &SettingsPatch {
                theme: Some("LIGHT".to_string()),
                ..SettingsPatch::default()
            },
        )
        .expect("apply");

        assert_eq!(after.locale, "tr", "an absent field is left alone");
        assert_eq!(after.theme, "LIGHT");
        assert_eq!(
            after.preferences_dirty_at.as_deref(),
            Some("2026-09-04T08:41:02.310Z")
        );
    }

    #[test]
    fn an_explicit_null_clears_a_field() {
        let connection = open_in_memory().expect("store");
        let patch: SettingsPatch =
            serde_json::from_str(r#"{"preferencesDirtyAt":"2026-09-04T08:41:02.310Z"}"#)
                .expect("parse");
        apply(&connection, &patch).expect("apply");

        let patch: SettingsPatch =
            serde_json::from_str(r#"{"preferencesDirtyAt":null}"#).expect("parse");
        let after = apply(&connection, &patch).expect("apply");

        assert_eq!(after.preferences_dirty_at, None);
    }

    #[test]
    fn a_session_round_trips_and_names_the_active_user() {
        let connection = open_in_memory().expect("store");
        let session = StoredSession {
            user_id: "018f3b90-0000-7000-8000-000000000001".to_string(),
            access_token: "a".to_string(),
            refresh_token: "r".to_string(),
            access_token_expires_at: Some("2026-09-04T10:00:00.000Z".to_string()),
        };
        store_session(&connection, &session).expect("store");

        assert_eq!(load_session(&connection).expect("load"), Some(session));
        assert_eq!(
            active_user(&connection).expect("active user"),
            "018f3b90-0000-7000-8000-000000000001"
        );

        clear_session(&connection).expect("clear");
        assert_eq!(load_session(&connection).expect("load"), None);
        assert_eq!(active_user(&connection).expect("active"), ANONYMOUS_USER);
    }
}
