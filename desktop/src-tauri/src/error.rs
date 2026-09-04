//! The error shape every command returns.
//!
//! `code` is a stable identifier the UI maps to a translation key, mirroring
//! the REST error contract so the frontend has one error-handling path rather
//! than two. `message` is English developer-facing text and is never shown to a
//! user. `details` is free-form and exists for diagnostics only.

use serde::Serialize;

/// Stable error codes. Codes that originate on the server during a download are
/// passed through unchanged, so a value looks the same whether the UI learned
/// it from an HTTP call or from a download event.
pub mod codes {
    // Raised locally.
    pub const STORE_UNAVAILABLE: &str = "STORE_UNAVAILABLE";
    pub const ENTITY_NOT_IN_LIBRARY: &str = "ENTITY_NOT_IN_LIBRARY";
    pub const ALREADY_QUEUED: &str = "ALREADY_QUEUED";
    pub const NOTHING_TO_DO: &str = "NOTHING_TO_DO";
    pub const INVALID_ARGUMENT: &str = "INVALID_ARGUMENT";
    pub const NETWORK_UNAVAILABLE: &str = "NETWORK_UNAVAILABLE";
    pub const UNEXPECTED_RESPONSE: &str = "UNEXPECTED_RESPONSE";
    pub const DIGEST_MISMATCH: &str = "DIGEST_MISMATCH";
    pub const INSUFFICIENT_STORAGE: &str = "INSUFFICIENT_STORAGE";

    // Passed through from the server.
    pub const TRACK_NOT_FOUND: &str = "TRACK_NOT_FOUND";
    pub const LESSON_NOT_FOUND: &str = "LESSON_NOT_FOUND";
    pub const MIND_MAP_NOT_FOUND: &str = "MIND_MAP_NOT_FOUND";
    pub const RATE_LIMITED: &str = "RATE_LIMITED";
    pub const INTERNAL_ERROR: &str = "INTERNAL_ERROR";
    pub const SERVICE_UNAVAILABLE: &str = "SERVICE_UNAVAILABLE";
    pub const CONTENT_VERSION_SUPERSEDED: &str = "CONTENT_VERSION_SUPERSEDED";
    pub const CONTENT_CHANGED_DURING_RESUME: &str = "CONTENT_CHANGED_DURING_RESUME";
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CommandError {
    pub code: String,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<serde_json::Value>,
}

impl CommandError {
    pub fn new(code: &str, message: impl Into<String>) -> Self {
        Self {
            code: code.to_string(),
            message: message.into(),
            details: None,
        }
    }

    pub fn with_details(mut self, details: serde_json::Value) -> Self {
        self.details = Some(details);
        self
    }

    pub fn store(error: impl std::fmt::Display) -> Self {
        Self::new(
            codes::STORE_UNAVAILABLE,
            format!("the local store could not be read: {error}"),
        )
    }

    pub fn invalid_argument(message: impl Into<String>) -> Self {
        Self::new(codes::INVALID_ARGUMENT, message)
    }

    pub fn not_in_library(message: impl Into<String>) -> Self {
        Self::new(codes::ENTITY_NOT_IN_LIBRARY, message)
    }
}

impl std::fmt::Display for CommandError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}: {}", self.code, self.message)
    }
}

impl std::error::Error for CommandError {}

impl From<rusqlite::Error> for CommandError {
    fn from(error: rusqlite::Error) -> Self {
        CommandError::store(error)
    }
}

impl From<crate::store::StoreError> for CommandError {
    fn from(error: crate::store::StoreError) -> Self {
        CommandError::store(error)
    }
}

/// Parses a UUID at the command boundary. Identifiers are rejected here rather
/// than passed inward as strings, so a malformed value fails with a code the UI
/// can translate instead of producing an empty result set later.
pub fn parse_uuid(field: &str, value: &str) -> Result<uuid::Uuid, CommandError> {
    uuid::Uuid::parse_str(value).map_err(|_| {
        CommandError::invalid_argument(format!("{field} is not a valid identifier"))
            .with_details(serde_json::json!({ "field": field, "value": value }))
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serializes_in_camel_case_and_omits_absent_details() {
        let json = serde_json::to_value(CommandError::new(
            codes::STORE_UNAVAILABLE,
            "The local store could not be opened",
        ))
        .expect("serialize");
        assert_eq!(json["code"], "STORE_UNAVAILABLE");
        assert!(json.get("details").is_none());
    }

    #[test]
    fn a_malformed_identifier_is_rejected_at_the_boundary() {
        let error = parse_uuid("lessonId", "not-a-uuid").expect_err("must reject");
        assert_eq!(error.code, codes::INVALID_ARGUMENT);
    }
}
