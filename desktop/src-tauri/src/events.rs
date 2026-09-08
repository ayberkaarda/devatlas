//! Events the engine emits to the UI.
//!
//! The engine talks to a sink rather than to the window directly. That is not
//! indirection for its own sake: the queue's behaviour is defined in terms of
//! what it emits and when, and a sink that records instead of emitting is what
//! lets those rules be asserted without a running WebView.

use serde::Serialize;

use crate::model::{EntityType, PauseReason, QueueState};

pub const DOWNLOAD_PROGRESS: &str = "download://progress";
pub const DOWNLOAD_BATCH_COMPLETE: &str = "download://batch-complete";
pub const LIBRARY_UPDATED: &str = "library://updated";

/// Per-entity progress.
///
/// Unlike a package document, this payload may carry `null`. The rule that
/// forbids `null` applies to hashed documents; an event is not hashed, and
/// omitting the key would force the UI to tell "absent" from "null" for no
/// benefit.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ProgressEvent {
    pub entity_id: String,
    pub entity_type: EntityType,
    pub state: QueueState,
    pub received_bytes: i64,
    /// From the manifest, not from `Content-Length`, so a percentage exists
    /// before the response headers arrive.
    pub total_bytes: i64,
    pub attempt: i64,
    pub batch: BatchProgress,
    pub pause_reason: Option<PauseReason>,
    pub error_code: Option<String>,
}

/// The user-visible operation the entity belongs to, so the UI can render a
/// per-item bar and an overall one without keeping its own tally.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BatchProgress {
    pub batch_id: String,
    pub completed_entities: i64,
    pub total_entities: i64,
    pub received_bytes: i64,
    pub total_bytes: i64,
}

/// One edge per batch, once nothing in it is still working. Deriving this from
/// per-entity events would mean reimplementing the queue's bookkeeping in the
/// UI, and the two copies would disagree the first time an entry failed.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BatchCompleteEvent {
    pub batch_id: String,
    pub completed_entities: i64,
    pub failed_entities: i64,
}

pub trait EventSink: Send + Sync {
    fn download_progress(&self, event: ProgressEvent);
    fn batch_complete(&self, event: BatchCompleteEvent);
    /// The replica changed in a way a rendered screen would have to reflect: an
    /// entity finished downloading, was deleted, or was marked withdrawn.
    fn library_updated(&self);
}

/// Sends events to the WebView.
pub struct TauriEventSink {
    app: tauri::AppHandle,
}

impl TauriEventSink {
    pub fn new(app: tauri::AppHandle) -> Self {
        Self { app }
    }
}

impl EventSink for TauriEventSink {
    fn download_progress(&self, event: ProgressEvent) {
        emit(&self.app, DOWNLOAD_PROGRESS, &event);
    }

    fn batch_complete(&self, event: BatchCompleteEvent) {
        emit(&self.app, DOWNLOAD_BATCH_COMPLETE, &event);
    }

    fn library_updated(&self) {
        emit(&self.app, LIBRARY_UPDATED, &());
    }
}

/// A failed emit is logged and swallowed. The download it describes has already
/// happened, and tearing down a transfer because a window went away would turn
/// a cosmetic problem into a data one.
fn emit<T: Serialize + Clone>(app: &tauri::AppHandle, name: &str, payload: &T) {
    use tauri::Emitter;
    if let Err(error) = app.emit(name, payload.clone()) {
        log::warn!("could not emit {name}: {error}");
    }
}

#[cfg(test)]
pub mod testing {
    use super::*;
    use std::sync::Mutex;

    /// Records what the engine emitted, so the throttling and state-change
    /// rules can be asserted directly.
    #[derive(Default)]
    pub struct RecordingSink {
        pub progress: Mutex<Vec<ProgressEvent>>,
        pub batches: Mutex<Vec<BatchCompleteEvent>>,
        pub library_updates: Mutex<usize>,
    }

    impl RecordingSink {
        /// The state carried by every published event, in order, one entry per
        /// event. Use this only where the number of events is itself the
        /// subject, such as asserting that the throttle suppressed some.
        pub fn states(&self) -> Vec<QueueState> {
            self.progress
                .lock()
                .expect("progress lock")
                .iter()
                .map(|event| event.state)
                .collect()
        }

        /// The same sequence with consecutive repeats collapsed, leaving the
        /// state changes and nothing else.
        ///
        /// Byte progress is published under whatever state the entry is
        /// already in, and how many such ticks a transfer produces depends on
        /// how long the transfer took against a periodic timer. That is a
        /// property of the machine, not of the engine: the same code publishes
        /// one `DOWNLOADING` event on a host that finishes inside the interval
        /// and several on a slower one. Asserting the raw sequence therefore
        /// pins the speed of whoever runs the test, and fails without any
        /// defect behind it.
        ///
        /// Collapsing runs keeps everything the rule is actually about — each
        /// transition published, in the right order, none swallowed by the
        /// throttle — and discards only the tick count, which the engine never
        /// promised. A dropped or reordered transition still changes this
        /// sequence and still fails. Do not "tighten" a caller back to
        /// [`Self::states`] to pin an exact event count; that count is not a
        /// guarantee.
        pub fn state_changes(&self) -> Vec<QueueState> {
            let mut states = self.states();
            states.dedup();
            states
        }
    }

    impl EventSink for RecordingSink {
        fn download_progress(&self, event: ProgressEvent) {
            self.progress.lock().expect("progress lock").push(event);
        }

        fn batch_complete(&self, event: BatchCompleteEvent) {
            self.batches.lock().expect("batch lock").push(event);
        }

        fn library_updated(&self) {
            *self.library_updates.lock().expect("library lock") += 1;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_progress_event_is_camel_case_and_keeps_its_nulls() {
        let event = ProgressEvent {
            entity_id: "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70".to_string(),
            entity_type: EntityType::Lesson,
            state: QueueState::Downloading,
            received_bytes: 20480,
            total_bytes: 41233,
            attempt: 1,
            batch: BatchProgress {
                batch_id: "018f3c55-7b1e-7d02-a933-0e84f2617b59".to_string(),
                completed_entities: 4,
                total_entities: 32,
                received_bytes: 512000,
                total_bytes: 1893441,
            },
            pause_reason: None,
            error_code: None,
        };

        let json = serde_json::to_value(&event).expect("serialize");
        assert_eq!(json["entityId"], "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70");
        assert_eq!(json["receivedBytes"], 20480);
        assert_eq!(json["batch"]["totalEntities"], 32);
        assert!(json["errorCode"].is_null());
        assert!(json.get("errorCode").is_some(), "the key stays present");
    }
}
