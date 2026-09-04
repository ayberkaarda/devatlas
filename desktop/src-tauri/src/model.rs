//! Shared domain vocabulary.
//!
//! These enums appear on three surfaces at once -- the SQLite store, the
//! manifest JSON and the command payloads -- so they are defined once and
//! serialized in the wire form the protocol documents use.

use std::fmt;
use std::str::FromStr;

use serde::{Deserialize, Serialize};

/// An independently downloadable unit of content. There are exactly two.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum EntityType {
    Lesson,
    MindMap,
}

impl EntityType {
    /// The spelling used in a content URL path. The path segment is lowercase
    /// while every JSON body spells the same value in upper snake case, and the
    /// two are not interchangeable.
    pub fn path_segment(self) -> &'static str {
        match self {
            EntityType::Lesson => "lesson",
            EntityType::MindMap => "mind_map",
        }
    }

    /// The not-found code the server returns for this entity type. There is no
    /// generic content-not-found code, so the mapping lives here rather than at
    /// each call site.
    pub fn not_found_code(self) -> &'static str {
        match self {
            EntityType::Lesson => crate::error::codes::LESSON_NOT_FOUND,
            EntityType::MindMap => crate::error::codes::MIND_MAP_NOT_FOUND,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            EntityType::Lesson => "LESSON",
            EntityType::MindMap => "MIND_MAP",
        }
    }
}

impl fmt::Display for EntityType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for EntityType {
    type Err = ();

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "LESSON" => Ok(EntityType::Lesson),
            "MIND_MAP" => Ok(EntityType::MindMap),
            _ => Err(()),
        }
    }
}

/// Queue states, exactly the set the sync protocol defines.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum QueueState {
    Queued,
    Downloading,
    Verifying,
    Done,
    Failed,
    Paused,
}

impl QueueState {
    pub fn as_str(self) -> &'static str {
        match self {
            QueueState::Queued => "QUEUED",
            QueueState::Downloading => "DOWNLOADING",
            QueueState::Verifying => "VERIFYING",
            QueueState::Done => "DONE",
            QueueState::Failed => "FAILED",
            QueueState::Paused => "PAUSED",
        }
    }

    /// `DONE` and `FAILED` are the states a batch can finish in. Everything else
    /// still owes the user an outcome, which is what decides when
    /// `download://batch-complete` fires.
    pub fn is_terminal(self) -> bool {
        matches!(self, QueueState::Done | QueueState::Failed)
    }
}

impl fmt::Display for QueueState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for QueueState {
    type Err = ();

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "QUEUED" => Ok(QueueState::Queued),
            "DOWNLOADING" => Ok(QueueState::Downloading),
            "VERIFYING" => Ok(QueueState::Verifying),
            "DONE" => Ok(QueueState::Done),
            "FAILED" => Ok(QueueState::Failed),
            "PAUSED" => Ok(QueueState::Paused),
            _ => Err(()),
        }
    }
}

/// Why an entry is paused. The distinction matters to the UI: one of these is
/// something the user did and the other is something only the user can clear.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PauseReason {
    User,
    InsufficientStorage,
}

impl PauseReason {
    pub fn as_str(self) -> &'static str {
        match self {
            PauseReason::User => "USER",
            PauseReason::InsufficientStorage => "INSUFFICIENT_STORAGE",
        }
    }
}

impl FromStr for PauseReason {
    type Err = ();

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "USER" => Ok(PauseReason::User),
            "INSUFFICIENT_STORAGE" => Ok(PauseReason::InsufficientStorage),
            _ => Err(()),
        }
    }
}

/// Whether a locally stored entity is current, behind, ahead or gone.
///
/// Two axes rather than one: what is stored, and what the queue is doing about
/// it. This enum is only the first axis; transfer state travels separately, so
/// a lesson can be downloaded, out of date and downloading at the same time
/// without any of the three statements displacing the others.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Availability {
    NotDownloaded,
    Downloaded,
    UpdateAvailable,
    Withdrawn,
    LocalAhead,
}

/// Whether a progress row still owes the server a write.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SyncState {
    Pending,
    Synced,
    Orphaned,
}

impl FromStr for SyncState {
    type Err = ();

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "PENDING" => Ok(SyncState::Pending),
            "SYNCED" => Ok(SyncState::Synced),
            "ORPHANED" => Ok(SyncState::Orphaned),
            _ => Err(()),
        }
    }
}

/// What a user asked to download or delete. One command covers all three
/// granularities because nothing about the engine differs between them.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ScopeKind {
    Lesson,
    Module,
    Track,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadScope {
    pub kind: ScopeKind,
    pub id: String,
}

/// Derives availability from what the manifest advertises and what is stored.
pub fn availability_of(
    manifest_version: Option<i64>,
    stored_version: Option<i64>,
    withdrawn: bool,
) -> Availability {
    if withdrawn {
        return Availability::Withdrawn;
    }
    match (stored_version, manifest_version) {
        (None, _) => Availability::NotDownloaded,
        (Some(_), None) => Availability::Withdrawn,
        (Some(stored), Some(manifest)) if stored < manifest => Availability::UpdateAvailable,
        (Some(stored), Some(manifest)) if stored > manifest => Availability::LocalAhead,
        _ => Availability::Downloaded,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn entity_type_spells_paths_and_bodies_differently() {
        assert_eq!(EntityType::MindMap.path_segment(), "mind_map");
        assert_eq!(EntityType::MindMap.as_str(), "MIND_MAP");
        assert_eq!(
            serde_json::to_string(&EntityType::MindMap).expect("serialize"),
            "\"MIND_MAP\""
        );
    }

    #[test]
    fn availability_covers_every_comparison() {
        assert_eq!(
            availability_of(Some(12), None, false),
            Availability::NotDownloaded
        );
        assert_eq!(
            availability_of(Some(12), Some(12), false),
            Availability::Downloaded
        );
        assert_eq!(
            availability_of(Some(13), Some(12), false),
            Availability::UpdateAvailable
        );
        assert_eq!(
            availability_of(Some(11), Some(12), false),
            Availability::LocalAhead
        );
        assert_eq!(
            availability_of(None, Some(12), false),
            Availability::Withdrawn
        );
        assert_eq!(
            availability_of(Some(12), Some(12), true),
            Availability::Withdrawn
        );
    }

    #[test]
    fn only_done_and_failed_end_a_batch() {
        assert!(QueueState::Done.is_terminal());
        assert!(QueueState::Failed.is_terminal());
        for state in [
            QueueState::Queued,
            QueueState::Downloading,
            QueueState::Verifying,
            QueueState::Paused,
        ] {
            assert!(!state.is_terminal(), "{state} must keep a batch open");
        }
    }
}
