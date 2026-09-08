//! The download engine.
//!
//! One engine serves all three download granularities, because nothing about a
//! transfer differs between a lesson, a module and a track: each resolves to a
//! list of entities and the queue does the rest.
//!
//! The rules that shape this module, and the reason each one exists:
//!
//! * Being unable to reach the server does not consume an attempt. Three
//!   attempts and their backoff elapse in about fourteen seconds, so a laptop
//!   opened after a week away would otherwise mark its whole queue failed
//!   before the user finished signing in. Attempts bound retries against a
//!   server that answers badly, not against a network that is not there.
//! * A response that arrives and is wrong does consume one: a 5xx, a digest
//!   mismatch, or a connection that dropped mid-body.
//! * An unexpected 4xx fails immediately. 401, 403, 400, 405 and 415 are
//!   configuration errors, and three attempts cannot fix one.
//! * Truncation and corruption are different failures. A short response is
//!   incomplete, so the partial is kept and the next attempt resumes; a
//!   complete response whose digest is wrong is wrong, so the partial is
//!   deleted and the next attempt starts from zero. Resuming onto known-bad
//!   bytes would splice one document out of two.
//! * Verification, the replica write and the `DONE` transition are one
//!   transaction. An entity marked complete with no rows behind it would
//!   satisfy every future delta comparison while the lesson does not exist.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use crate::db::{now_iso, parse_iso, to_iso, Db};
use crate::error::{codes, CommandError};
use crate::events::{BatchCompleteEvent, BatchProgress, EventSink, ProgressEvent};
use crate::http::{ContentClient, ManifestFetch, PackageRequest, TransferError};
use crate::manifest::Package;
use crate::model::{PauseReason, QueueState};
use crate::queue::{self, QueueRow};
use crate::replica;
use crate::verify;

#[derive(Debug, Clone)]
pub struct EngineConfig {
    /// Three attempts, then the entry is failed and waits for the user.
    pub max_attempts: i64,
    /// Exponential with jitter, starting here.
    pub base_backoff: Duration,
    /// The cap is on the queue rather than per track, so starting a second
    /// track download does not multiply connections.
    pub concurrency: usize,
    /// At most one progress event per entity per interval, plus an unthrottled
    /// one on every state change.
    pub progress_interval: Duration,
    /// How many times one pump run will come back from being offline before it
    /// yields. Without a bound, a server that accepts a probe and then refuses
    /// every transfer would spin forever without consuming an attempt.
    pub max_offline_recoveries: u32,
}

impl Default for EngineConfig {
    fn default() -> Self {
        Self {
            max_attempts: 3,
            base_backoff: Duration::from_secs(2),
            concurrency: 3,
            progress_interval: Duration::from_millis(250),
            max_offline_recoveries: 3,
        }
    }
}

pub struct Engine {
    db: Arc<Db>,
    client: ContentClient,
    partials: PathBuf,
    events: Arc<dyn EventSink>,
    config: EngineConfig,
    offline: AtomicBool,
    pumping: AtomicBool,
    last_emitted: Mutex<HashMap<String, Instant>>,
    announced_batches: Mutex<HashSet<String>>,
}

impl Engine {
    pub fn new(
        db: Arc<Db>,
        client: ContentClient,
        partials: PathBuf,
        events: Arc<dyn EventSink>,
        config: EngineConfig,
    ) -> Arc<Self> {
        Arc::new(Self {
            db,
            client,
            partials,
            events,
            config,
            offline: AtomicBool::new(false),
            pumping: AtomicBool::new(false),
            last_emitted: Mutex::new(HashMap::new()),
            announced_batches: Mutex::new(HashSet::new()),
        })
    }

    pub fn db(&self) -> &Arc<Db> {
        &self.db
    }

    pub fn client(&self) -> &ContentClient {
        &self.client
    }

    pub fn events(&self) -> &Arc<dyn EventSink> {
        &self.events
    }

    pub fn is_offline(&self) -> bool {
        self.offline.load(Ordering::Relaxed)
    }

    /// Brings the queue back to a state the scheduler understands after a
    /// restart, and clears temp files nothing refers to any more.
    pub fn recover(&self) -> Result<(), CommandError> {
        self.db.with(queue::recover_after_restart)?;
        let live: HashSet<String> = self
            .db
            .with(queue::all)?
            .into_iter()
            .filter(|row| !row.state.is_terminal())
            .map(|row| self.partial_path(&row).to_string_lossy().to_string())
            .collect();

        if let Ok(entries) = std::fs::read_dir(&self.partials) {
            for entry in entries.flatten() {
                let path = entry.path();
                if !live.contains(&path.to_string_lossy().to_string()) {
                    let _ = std::fs::remove_file(&path);
                }
            }
        }
        Ok(())
    }

    /// Named by entity and version, so a partial from an abandoned version can
    /// never be appended to a transfer of a different one.
    fn partial_path(&self, row: &QueueRow) -> PathBuf {
        self.partials
            .join(format!("{}.v{}.part", row.entity_id, row.content_version))
    }

    /// Deletes the partial file belonging to a queue entry, if any.
    ///
    /// A cancelled entry leaves nothing behind: there is no permanent package
    /// file, so an orphaned partial is pure waste of disk.
    pub fn remove_partial_for(&self, row: &QueueRow) {
        remove_partial(&self.partial_path(row));
    }

    /// Runs the queue until nothing is ready.
    pub async fn pump(self: &Arc<Self>) {
        if self.pumping.swap(true, Ordering::SeqCst) {
            return;
        }
        let outcome = self.pump_inner().await;
        self.pumping.store(false, Ordering::SeqCst);
        if let Err(error) = outcome {
            log::warn!("download pump stopped: {error}");
        }
    }

    async fn pump_inner(self: &Arc<Self>) -> Result<(), CommandError> {
        let mut recoveries = 0u32;
        loop {
            if self.is_offline() {
                if recoveries >= self.config.max_offline_recoveries {
                    return Ok(());
                }
                recoveries += 1;
                if !self.client.probe().await {
                    // Stop scheduling entirely. Nothing is failed and no
                    // attempt is spent; the queue simply waits.
                    return Ok(());
                }
                self.offline.store(false, Ordering::Relaxed);
            }

            let now = now_iso();
            let ready = self
                .db
                .with(|c| queue::ready(c, &now, self.config.concurrency))?;

            if ready.is_empty() {
                if !self.db.with(queue::has_open_work)? {
                    return Ok(());
                }
                match self.db.with(queue::earliest_next_attempt)? {
                    Some(at) => self.sleep_until(&at).await,
                    None => return Ok(()),
                }
                continue;
            }

            let mut running = Vec::new();
            for row in ready {
                let engine = Arc::clone(self);
                running.push(async move { engine.process(row).await });
            }
            futures_util::future::join_all(running).await;
        }
    }

    async fn sleep_until(&self, timestamp: &str) {
        let wait = parse_iso(timestamp)
            .map(|at| at - chrono::Utc::now())
            .and_then(|delta| delta.to_std().ok())
            .unwrap_or_default();
        // Capped so a far-future timestamp cannot park the pump indefinitely.
        tokio::time::sleep(wait.min(Duration::from_secs(5))).await;
    }

    /// One pass over one entry.
    pub async fn process(self: &Arc<Self>, row: QueueRow) {
        let outcome = match row.state {
            QueueState::Verifying => self.verify_and_commit(row).await,
            _ => self.transfer(row).await,
        };
        if let Err(error) = outcome {
            log::warn!("queue entry could not be processed: {error}");
        }
    }

    async fn transfer(self: &Arc<Self>, row: QueueRow) -> Result<(), CommandError> {
        let path = self.partial_path(&row);
        let on_disk = file_len(&path);
        if let Some(scheduled) = &row.next_attempt_at {
            log::debug!(
                "{} resumes after backing off until {scheduled}",
                row.entity_id
            );
        }

        // More bytes than the manifest promised means the manifest is stale.
        // Nothing can be salvaged from the partial, and hashing it would only
        // report a mismatch for a reason that has nothing to do with corruption.
        if on_disk > row.size_bytes as u64 {
            remove_partial(&path);
            return self
                .replan(&row, codes::CONTENT_VERSION_SUPERSEDED, true)
                .await;
        }

        let row = self.transition(
            &row,
            QueueState::Downloading,
            row.attempt,
            on_disk as i64,
            Some(&path),
            None,
            None,
            None,
        )?;

        let counter = Arc::new(AtomicU64::new(on_disk));
        let ticker = self.spawn_progress_ticker(&row, Arc::clone(&counter));

        let outcome = self
            .client
            .download_package(PackageRequest {
                entity_type: row.entity_type,
                entity_id: &row.entity_id,
                content_version: row.content_version,
                expected_sha256: &row.sha256,
                destination: &path,
                progress: Some(Arc::clone(&counter)),
            })
            .await;

        ticker.abort();

        match outcome {
            Ok(()) => {
                let received = file_len(&path) as i64;
                if received == row.size_bytes {
                    let row = self.transition(
                        &row,
                        QueueState::Verifying,
                        row.attempt,
                        received,
                        Some(&path),
                        None,
                        None,
                        None,
                    )?;
                    self.verify_and_commit(row).await
                } else if received < row.size_bytes {
                    // Incomplete, not wrong. The partial is a valid prefix and
                    // the next attempt asks for the rest with `Range`.
                    self.consume_attempt(&row, codes::UNEXPECTED_RESPONSE, received)
                } else {
                    remove_partial(&path);
                    self.replan(&row, codes::CONTENT_VERSION_SUPERSEDED, true)
                        .await
                }
            }
            Err(TransferError::Unreachable(message)) => {
                log::info!("server unreachable, queue paused for a probe: {message}");
                self.go_offline(&row)
            }
            Err(TransferError::Interrupted(message)) => {
                log::info!("transfer interrupted mid-body: {message}");
                self.consume_attempt(&row, codes::NETWORK_UNAVAILABLE, file_len(&path) as i64)
            }
            Err(TransferError::StorageFull(message)) => {
                log::warn!("no space left for downloads: {message}");
                self.pause_for_storage(&row)
            }
            Err(TransferError::Status {
                status,
                code,
                retry_after,
            }) => {
                self.handle_status(&row, status, &code, retry_after, &path)
                    .await
            }
            Err(TransferError::Local(message)) | Err(TransferError::Unexpected(message)) => {
                log::warn!("transfer failed locally: {message}");
                self.consume_attempt(&row, codes::UNEXPECTED_RESPONSE, file_len(&path) as i64)
            }
        }
    }

    async fn handle_status(
        self: &Arc<Self>,
        row: &QueueRow,
        status: u16,
        code: &str,
        retry_after: Option<Duration>,
        path: &Path,
    ) -> Result<(), CommandError> {
        match status {
            // A wait, not a failure. The server said when to come back and no
            // attempt is spent on being told to slow down.
            429 => {
                let at = to_iso(
                    chrono::Utc::now()
                        + chrono::Duration::from_std(retry_after.unwrap_or(Duration::from_secs(5)))
                            .unwrap_or_else(|_| chrono::Duration::seconds(5)),
                );
                self.transition(
                    row,
                    QueueState::Queued,
                    row.attempt,
                    file_len(path) as i64,
                    Some(path),
                    None,
                    Some(code),
                    Some(&at),
                )?;
                Ok(())
            }
            // The version this entry was asking for is no longer current.
            409 => self.replan(row, code, false).await,
            // Content was republished between attempts. The partial belongs to
            // a version that no longer exists, so it goes.
            412 => {
                remove_partial(path);
                self.replan(row, code, true).await
            }
            404 => {
                remove_partial(path);
                self.fail(row, code, 0)
            }
            status if (500..600).contains(&status) => {
                self.consume_attempt(row, code, file_len(path) as i64)
            }
            _ => {
                // 400, 401, 403, 405, 415 and anything else in this range are
                // configuration errors. Retrying three times cannot fix one,
                // and the rest of the batch is unaffected.
                self.fail(row, code, file_len(path) as i64)
            }
        }
    }

    /// Hashes the file, parses it, and commits the content and the `DONE`
    /// transition together.
    async fn verify_and_commit(self: &Arc<Self>, row: QueueRow) -> Result<(), CommandError> {
        let path = self.partial_path(&row);
        if !path.exists() {
            return self.consume_attempt(&row, codes::UNEXPECTED_RESPONSE, 0);
        }

        // Read once and hash what was read, so the document that gets parsed
        // and stored is byte for byte the one the digest describes.
        let (bytes, digest) = match verify::read_and_digest(&path) {
            Ok(verified) => verified,
            Err(error) => {
                log::warn!("could not read the downloaded package: {error}");
                return self.consume_attempt(
                    &row,
                    codes::UNEXPECTED_RESPONSE,
                    file_len(&path) as i64,
                );
            }
        };

        if !verify::digests_match(&row.sha256, &digest) {
            // Complete and wrong. Resuming would append new bytes onto
            // known-bad ones, so this restarts from zero.
            remove_partial(&path);
            return self.consume_attempt(&row, codes::DIGEST_MISMATCH, 0);
        }

        let package: Package = match serde_json::from_slice(&bytes) {
            Ok(package) => package,
            Err(error) => {
                // The digest matched, so these are exactly the bytes the
                // manifest promised. Another attempt would fetch the same ones.
                log::warn!("a verified package did not parse: {error}");
                remove_partial(&path);
                return self.fail(&row, codes::UNEXPECTED_RESPONSE, 0);
            }
        };

        if package.entity_id() != row.entity_id || package.content_version() != row.content_version
        {
            remove_partial(&path);
            return self.fail(&row, codes::UNEXPECTED_RESPONSE, 0);
        }

        let entity_id = row.entity_id.clone();
        let sha = row.sha256.clone();
        let size = row.size_bytes;
        let written = self.db.transaction(move |tx| {
            let written = replica::write_package(tx, &package, &sha, size)?;
            if written == 0 {
                return Ok(0);
            }
            tx.execute(
                "UPDATE download_queue
                 SET state = 'DONE', received_bytes = ?2, partial_path = NULL, pause_reason = NULL,
                     error_code = NULL, next_attempt_at = NULL, updated_at = ?3
                 WHERE entity_id = ?1;",
                rusqlite::params![entity_id, size, now_iso()],
            )?;
            Ok(written)
        })?;

        if written == 0 {
            // The manifest structure this package belongs to is not in the
            // store, so there is nothing to fill in. Retrying would not create
            // it; refreshing the library would.
            remove_partial(&path);
            return self.fail(&row, codes::UNEXPECTED_RESPONSE, row.size_bytes);
        }

        // Only after the transaction committed. There is no permanent package
        // file: the document lives in the replica from here on.
        remove_partial(&path);

        let done = self.reload(&row.entity_id)?;
        self.emit(&done, true)?;
        self.events.library_updated();
        self.announce_batch_if_complete(&done.batch_id)?;
        Ok(())
    }

    /// Re-reads the manifest and points the entry at whatever is current.
    ///
    /// `discard_partial` says whether the bytes already on disk belong to a
    /// version that no longer exists.
    async fn replan(
        self: &Arc<Self>,
        row: &QueueRow,
        code: &str,
        discard_partial: bool,
    ) -> Result<(), CommandError> {
        if discard_partial {
            remove_partial(&self.partial_path(row));
        }

        let Some(track_id) = row.track_id.clone() else {
            return self.fail(row, codes::UNEXPECTED_RESPONSE, 0);
        };

        let fetched = match self.client.fetch_track_manifest(&track_id, None).await {
            Ok(ManifestFetch::Fetched { value, etag }) => (value, etag),
            Ok(ManifestFetch::NotModified) => {
                return self.consume_attempt(row, code, 0);
            }
            Err(TransferError::Unreachable(_)) => return self.go_offline(row),
            Err(TransferError::Status { code, .. }) if code == codes::TRACK_NOT_FOUND => {
                return self.fail(row, &code, 0);
            }
            Err(error) => {
                log::warn!("could not re-plan against the manifest: {error}");
                return self.consume_attempt(row, code, 0);
            }
        };

        let (manifest, etag) = fetched;
        let entity = manifest.entity(&row.entity_id).cloned();
        self.db
            .transaction(|tx| replica::apply_track_manifest(tx, &manifest, etag.as_deref()))?;
        self.events.library_updated();

        let Some(entity) = entity else {
            // The manifest no longer lists it. The manifest was stale, and
            // this entity is terminal.
            return self.fail(row, row.entity_type.not_found_code(), 0);
        };

        if entity.content_version == row.content_version && entity.sha256 == row.sha256 {
            // Nothing changed, so re-planning again would repeat this exact
            // round trip forever. This is a server that answers badly, which
            // is what attempts are for.
            return self.consume_attempt(row, code, 0);
        }

        let entity_id = row.entity_id.clone();
        self.db.with(|c| {
            queue::replan(
                c,
                &entity_id,
                entity.content_version,
                &entity.sha256,
                entity.size_bytes,
            )
        })?;
        let replanned = self.reload(&row.entity_id)?;
        self.emit(&replanned, true)?;
        Ok(())
    }

    /// The server could not be reached. No attempt is spent and no entry fails;
    /// the engine stops scheduling until a probe succeeds.
    fn go_offline(&self, row: &QueueRow) -> Result<(), CommandError> {
        self.offline.store(true, Ordering::Relaxed);
        let at = to_iso(
            chrono::Utc::now()
                + chrono::Duration::from_std(self.config.base_backoff)
                    .unwrap_or_else(|_| chrono::Duration::seconds(2)),
        );
        self.transition(
            row,
            QueueState::Queued,
            row.attempt,
            row.received_bytes,
            row.partial_path.as_deref().map(Path::new),
            None,
            Some(codes::NETWORK_UNAVAILABLE),
            Some(&at),
        )?;
        Ok(())
    }

    /// A full disk pauses everything that is still working, without consuming
    /// an attempt: retrying into a disk that is still full would burn the queue
    /// for a condition only the user can clear.
    fn pause_for_storage(&self, row: &QueueRow) -> Result<(), CommandError> {
        self.db
            .with(|c| queue::pause(c, None, PauseReason::InsufficientStorage))?;
        let paused = self.reload(&row.entity_id)?;
        self.emit(&paused, true)?;
        Ok(())
    }

    fn consume_attempt(
        &self,
        row: &QueueRow,
        code: &str,
        received: i64,
    ) -> Result<(), CommandError> {
        let attempt = row.attempt + 1;
        if attempt >= self.config.max_attempts {
            // The attempt that just failed is the one recorded, so the UI can
            // say how many were spent rather than how many had been spent
            // before the last one.
            return self.fail_at(row, code, received, attempt);
        }
        let at = to_iso(
            chrono::Utc::now()
                + chrono::Duration::from_std(self.backoff(attempt))
                    .unwrap_or_else(|_| chrono::Duration::seconds(2)),
        );
        let partial = self.partial_path(row);
        self.transition(
            row,
            QueueState::Queued,
            attempt,
            received,
            partial.exists().then_some(partial.as_path()),
            None,
            Some(code),
            Some(&at),
        )?;
        Ok(())
    }

    fn fail(&self, row: &QueueRow, code: &str, received: i64) -> Result<(), CommandError> {
        self.fail_at(row, code, received, row.attempt.max(1))
    }

    fn fail_at(
        &self,
        row: &QueueRow,
        code: &str,
        received: i64,
        attempt: i64,
    ) -> Result<(), CommandError> {
        let failed = self.transition(
            row,
            QueueState::Failed,
            attempt,
            received,
            None,
            None,
            Some(code),
            None,
        )?;
        self.announce_batch_if_complete(&failed.batch_id)?;
        Ok(())
    }

    /// Exponential with jitter. The jitter matters when a batch of entities
    /// fails together against one bad server: without it they would all come
    /// back at the same instant, three times over.
    fn backoff(&self, attempt: i64) -> Duration {
        if self.config.base_backoff.is_zero() {
            return Duration::ZERO;
        }
        let steps = attempt.clamp(1, 10) as u32 - 1;
        let base = self.config.base_backoff * 2u32.pow(steps);
        base + base.mul_f64(jitter_fraction())
    }

    #[allow(clippy::too_many_arguments)]
    fn transition(
        &self,
        row: &QueueRow,
        state: QueueState,
        attempt: i64,
        received: i64,
        partial: Option<&Path>,
        pause_reason: Option<PauseReason>,
        error_code: Option<&str>,
        next_attempt_at: Option<&str>,
    ) -> Result<QueueRow, CommandError> {
        let partial = partial.map(|path| path.to_string_lossy().to_string());
        let entity_id = row.entity_id.clone();
        let error = error_code.map(|code| code.to_string());
        let next = next_attempt_at.map(|value| value.to_string());
        self.db.with(move |c| {
            queue::set_state(
                c,
                &entity_id,
                state,
                attempt,
                received,
                partial.as_deref(),
                pause_reason,
                error.as_deref(),
                next.as_deref(),
            )
        })?;
        let updated = self.reload(&row.entity_id)?;
        self.emit(&updated, true)?;
        Ok(updated)
    }

    fn reload(&self, entity_id: &str) -> Result<QueueRow, CommandError> {
        let id = entity_id.to_string();
        self.db
            .with(move |c| queue::find(c, &id))?
            .ok_or_else(|| CommandError::not_in_library(format!("{entity_id} left the queue")))
    }

    /// Publishes progress. A state change is always published; byte counts are
    /// rate limited per entity so a fast connection cannot flood the WebView.
    fn emit(&self, row: &QueueRow, state_changed: bool) -> Result<(), CommandError> {
        if !state_changed && !self.due_for_progress(&row.entity_id) {
            return Ok(());
        }
        let batch_id = row.batch_id.clone();
        let totals = self.db.with(move |c| queue::batch_totals(c, &batch_id))?;
        self.events.download_progress(ProgressEvent {
            entity_id: row.entity_id.clone(),
            entity_type: row.entity_type,
            state: row.state,
            received_bytes: row.received_bytes,
            total_bytes: row.size_bytes,
            attempt: row.attempt,
            batch: BatchProgress {
                batch_id: row.batch_id.clone(),
                completed_entities: totals.completed_entities,
                total_entities: totals.total_entities,
                received_bytes: totals.received_bytes,
                total_bytes: totals.total_bytes,
            },
            pause_reason: row.pause_reason,
            error_code: row.error_code.clone(),
        });
        Ok(())
    }

    fn due_for_progress(&self, entity_id: &str) -> bool {
        let mut last = match self.last_emitted.lock() {
            Ok(last) => last,
            Err(_) => return true,
        };
        let now = Instant::now();
        match last.get(entity_id) {
            Some(previous) if now.duration_since(*previous) < self.config.progress_interval => {
                false
            }
            _ => {
                last.insert(entity_id.to_string(), now);
                true
            }
        }
    }

    /// Emits `download://batch-complete` once, when nothing in the batch is
    /// still working.
    fn announce_batch_if_complete(&self, batch_id: &str) -> Result<(), CommandError> {
        let id = batch_id.to_string();
        let totals = self.db.with(move |c| queue::batch_totals(c, &id))?;
        if totals.open_entities > 0 {
            return Ok(());
        }
        let mut announced = match self.announced_batches.lock() {
            Ok(announced) => announced,
            Err(_) => return Ok(()),
        };
        if !announced.insert(batch_id.to_string()) {
            return Ok(());
        }
        self.events.batch_complete(BatchCompleteEvent {
            batch_id: batch_id.to_string(),
            completed_entities: totals.completed_entities,
            failed_entities: totals.failed_entities,
        });
        Ok(())
    }

    /// Publishes byte progress while a transfer runs.
    fn spawn_progress_ticker(
        self: &Arc<Self>,
        row: &QueueRow,
        counter: Arc<AtomicU64>,
    ) -> tokio::task::JoinHandle<()> {
        let engine = Arc::clone(self);
        let mut row = row.clone();
        let interval = self.config.progress_interval;
        tokio::spawn(async move {
            loop {
                tokio::time::sleep(interval).await;
                row.received_bytes = counter.load(Ordering::Relaxed) as i64;
                let _ = engine.emit(&row, false);
            }
        })
    }
}

fn file_len(path: &Path) -> u64 {
    std::fs::metadata(path).map(|meta| meta.len()).unwrap_or(0)
}

fn remove_partial(path: &Path) {
    let _ = std::fs::remove_file(path);
}

/// A fraction in `[0, 0.25)`, derived from the clock.
///
/// The engine has no other use for randomness, and a dependency whose only job
/// is to spread three retries over half a second is not worth carrying.
fn jitter_fraction() -> f64 {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|since| since.subsec_nanos())
        .unwrap_or(0);
    f64::from(nanos % 1_000) / 4_000.0
}
#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::testing::RecordingSink;
    use crate::model::EntityType;
    use crate::store::open_in_memory;
    use wiremock::matchers::{method, path as path_matcher};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    const TRACK: &str = "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10";
    const MODULE: &str = "018f3a02-4411-7f60-9c22-77b0a1e4cc90";
    const LESSON: &str = "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70";

    fn package_bytes(version: i64, body: &str) -> Vec<u8> {
        format!(
            r##"{{"body_markdown":"{body}","content_version":{version},"entity_id":"{LESSON}","entity_type":"LESSON","module_id":"{MODULE}","order":1,"slug":"signals","title":"Introduction to signals"}}"##
        )
        .into_bytes()
    }

    fn manifest_json(version: i64, digest: &str, size: i64) -> serde_json::Value {
        serde_json::json!({
            "track_id": TRACK,
            "slug": "angular-path",
            "content_version": 47,
            "title": "The Angular Path",
            "modules": [{
                "module_id": MODULE,
                "title": "Signals and reactivity",
                "order": 1,
                "lessons": [{
                    "lesson_id": LESSON,
                    "slug": "signals",
                    "title": "Introduction to signals",
                    "order": 1
                }]
            }],
            "entities": [{
                "entity_type": "LESSON",
                "entity_id": LESSON,
                "content_version": version,
                "sha256": digest,
                "size_bytes": size
            }]
        })
    }

    struct Harness {
        _dir: tempfile::TempDir,
        server: MockServer,
        engine: Arc<Engine>,
        sink: Arc<RecordingSink>,
        db: Arc<Db>,
    }

    impl Harness {
        async fn new() -> Self {
            let server = MockServer::start().await;
            let dir = tempfile::tempdir().expect("temp dir");
            let partials = dir.path().join("partials");
            std::fs::create_dir_all(&partials).expect("partials dir");

            let db = Arc::new(Db::new(open_in_memory().expect("store")));
            let sink = Arc::new(RecordingSink::default());
            let engine = Engine::new(
                Arc::clone(&db),
                ContentClient::new(server.uri()),
                partials,
                Arc::clone(&sink) as Arc<dyn EventSink>,
                EngineConfig {
                    max_attempts: 3,
                    // Zero so the tests measure the state machine, not the wait.
                    base_backoff: Duration::ZERO,
                    concurrency: 3,
                    progress_interval: Duration::from_millis(250),
                    max_offline_recoveries: 1,
                },
            );

            Self {
                _dir: dir,
                server,
                engine,
                sink,
                db,
            }
        }

        /// Puts the track structure in the store, so a package has a row to
        /// fill in.
        fn seed_structure(&self, version: i64, digest: &str, size: i64) {
            let manifest: crate::manifest::TrackManifest =
                serde_json::from_value(manifest_json(version, digest, size)).expect("manifest");
            self.db
                .transaction(|tx| replica::apply_track_manifest(tx, &manifest, None))
                .expect("apply manifest");
        }

        fn enqueue(&self, version: i64, digest: &str, size: i64) {
            self.db
                .with(|c| {
                    queue::enqueue(
                        c,
                        LESSON,
                        EntityType::Lesson,
                        Some(TRACK),
                        "batch-1",
                        version,
                        digest,
                        size,
                    )
                })
                .expect("enqueue");
        }

        fn row(&self) -> QueueRow {
            self.db
                .with(|c| queue::find(c, LESSON))
                .expect("find")
                .expect("row")
        }

        fn partial(&self) -> PathBuf {
            self.engine.partial_path(&self.row())
        }

        async fn run(&self) {
            let row = self.row();
            self.engine.process(row).await;
        }
    }

    async fn mount_package(server: &MockServer, bytes: Vec<u8>) {
        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(200).set_body_bytes(bytes))
            .mount(server)
            .await;
    }

    #[tokio::test]
    async fn a_verified_package_lands_in_the_replica_and_the_temp_file_goes() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        mount_package(&harness.server, bytes.clone()).await;

        harness.run().await;

        assert_eq!(harness.row().state, QueueState::Done);
        let body: Option<String> = harness
            .db
            .with(|c| {
                c.query_row(
                    "SELECT body_markdown FROM lessons WHERE lesson_id = ?1;",
                    [LESSON],
                    |row| row.get(0),
                )
            })
            .expect("query");
        assert_eq!(body.as_deref(), Some("# Signals"));
        assert!(
            !harness.partial().exists(),
            "there is no permanent package file"
        );
        assert_eq!(*harness.sink.library_updates.lock().expect("lock"), 1);
        assert_eq!(harness.sink.batches.lock().expect("lock").len(), 1);
    }

    #[tokio::test]
    async fn every_state_of_a_successful_transfer_is_published() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        mount_package(&harness.server, bytes).await;

        harness.run().await;

        // Collapsed, because the byte-progress ticks a transfer happens to
        // produce between two transitions depend on how long it ran, not on
        // the engine. What is being asserted is the transition sequence
        // itself: all three, in order, none lost to the throttle.
        assert_eq!(
            harness.sink.state_changes(),
            vec![
                QueueState::Downloading,
                QueueState::Verifying,
                QueueState::Done
            ],
            "a state change is never throttled away"
        );
    }

    #[tokio::test]
    async fn a_digest_mismatch_deletes_the_partial_and_restarts_from_zero() {
        let harness = Harness::new().await;
        let advertised = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&advertised);
        // Same length, different bytes: complete and wrong, which is the case
        // that must not resume.
        let served = package_bytes(12, "# Sign4ls");
        assert_eq!(advertised.len(), served.len());

        harness.seed_structure(12, &digest, advertised.len() as i64);
        harness.enqueue(12, &digest, advertised.len() as i64);
        mount_package(&harness.server, served).await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 1, "a wrong answer costs an attempt");
        assert_eq!(row.error_code.as_deref(), Some(codes::DIGEST_MISMATCH));
        assert_eq!(row.received_bytes, 0);
        assert!(!harness.partial().exists());
    }

    #[tokio::test]
    async fn three_wrong_answers_fail_the_entry_and_no_more() {
        let harness = Harness::new().await;
        let advertised = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&advertised);
        harness.seed_structure(12, &digest, advertised.len() as i64);
        harness.enqueue(12, &digest, advertised.len() as i64);
        mount_package(&harness.server, package_bytes(12, "# Sign4ls")).await;

        for _ in 0..3 {
            harness.run().await;
        }

        let row = harness.row();
        assert_eq!(row.state, QueueState::Failed);
        assert_eq!(row.attempt, 3);
        assert_eq!(
            row.error_code.as_deref(),
            Some(codes::DIGEST_MISMATCH),
            "a failed entry keeps its last error so the UI can explain it"
        );
    }

    #[tokio::test]
    async fn a_truncated_response_keeps_the_partial_for_a_resume() {
        let harness = Harness::new().await;
        let full = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&full);
        harness.seed_structure(12, &digest, full.len() as i64);
        harness.enqueue(12, &digest, full.len() as i64);
        mount_package(&harness.server, full[..20].to_vec()).await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 1);
        assert_eq!(row.received_bytes, 20);
        assert!(
            harness.partial().exists(),
            "an incomplete response is not a wrong one; the prefix is kept"
        );
    }

    #[tokio::test]
    async fn a_resume_completes_from_the_bytes_already_on_disk() {
        let harness = Harness::new().await;
        let full = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&full);
        harness.seed_structure(12, &digest, full.len() as i64);
        harness.enqueue(12, &digest, full.len() as i64);

        let prefix = 20usize;
        std::fs::write(harness.partial(), &full[..prefix]).expect("seed partial");

        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .and(wiremock::matchers::header(
                "range",
                format!("bytes={prefix}-").as_str(),
            ))
            .and(wiremock::matchers::header(
                "if-match",
                format!("\"{digest}\"").as_str(),
            ))
            .respond_with(
                ResponseTemplate::new(206)
                    .insert_header(
                        "content-range",
                        format!("bytes {prefix}-{}/{}", full.len() - 1, full.len()).as_str(),
                    )
                    .set_body_bytes(full[prefix..].to_vec()),
            )
            .mount(&harness.server)
            .await;

        harness.run().await;

        assert_eq!(harness.row().state, QueueState::Done);
    }

    #[tokio::test]
    async fn a_412_discards_the_partial_and_re_plans_against_the_manifest() {
        let harness = Harness::new().await;
        let old = package_bytes(12, "# Signals");
        let old_digest = verify::digest_of_bytes(&old);
        harness.seed_structure(12, &old_digest, old.len() as i64);
        harness.enqueue(12, &old_digest, old.len() as i64);
        std::fs::write(harness.partial(), &old[..10]).expect("seed partial");
        let stale_partial = harness.partial();

        let new = package_bytes(13, "# Signals, revised");
        let new_digest = verify::digest_of_bytes(&new);

        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(412).set_body_json(serde_json::json!({
                "code": "CONTENT_CHANGED_DURING_RESUME", "message": "republished"
            })))
            .mount(&harness.server)
            .await;
        Mock::given(method("GET"))
            .and(path_matcher(format!("/manifest/track/{TRACK}")))
            .respond_with(ResponseTemplate::new(200).set_body_json(manifest_json(
                13,
                &new_digest,
                new.len() as i64,
            )))
            .mount(&harness.server)
            .await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(
            row.content_version, 13,
            "re-planned against what is current"
        );
        assert_eq!(row.sha256, new_digest);
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(
            row.attempt, 0,
            "being told the content moved is not a failure"
        );
        assert!(
            !stale_partial.exists(),
            "bytes of a version that no longer exists must not be spliced onto the new one"
        );
    }

    #[tokio::test]
    async fn an_unreachable_server_spends_no_attempt_and_stops_scheduling() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);

        // Nothing is listening: the connection is refused before any response.
        let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind");
        let port = listener.local_addr().expect("addr").port();
        drop(listener);
        let engine = Engine::new(
            Arc::clone(&harness.db),
            ContentClient::new(format!("http://127.0.0.1:{port}")),
            harness.engine.partials.clone(),
            Arc::clone(&harness.sink) as Arc<dyn EventSink>,
            EngineConfig {
                base_backoff: Duration::ZERO,
                max_offline_recoveries: 1,
                ..EngineConfig::default()
            },
        );

        engine.process(harness.row()).await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(
            row.attempt, 0,
            "a week-old laptop must not exhaust its queue before the user signs in"
        );
        assert_eq!(row.error_code.as_deref(), Some(codes::NETWORK_UNAVAILABLE));
        assert!(engine.is_offline());

        // A pump in the offline state probes and then yields without failing
        // anything.
        engine.pump().await;
        assert_eq!(harness.row().state, QueueState::Queued);
        assert_eq!(harness.row().attempt, 0);
    }

    #[tokio::test]
    async fn a_5xx_consumes_an_attempt() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(503))
            .mount(&harness.server)
            .await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 1);
        assert_eq!(row.error_code.as_deref(), Some(codes::SERVICE_UNAVAILABLE));
    }

    #[tokio::test]
    async fn a_429_waits_without_consuming_an_attempt() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(429).insert_header("retry-after", "3"))
            .mount(&harness.server)
            .await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 0, "being asked to slow down is not a failure");
        assert_eq!(row.error_code.as_deref(), Some(codes::RATE_LIMITED));
        assert!(row.next_attempt_at.is_some());
    }

    #[tokio::test]
    async fn an_unexpected_4xx_fails_at_once() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(403).set_body_json(serde_json::json!({
                "code": "FORBIDDEN_ROLE", "message": "no"
            })))
            .mount(&harness.server)
            .await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(
            row.state,
            QueueState::Failed,
            "three attempts cannot fix a configuration error"
        );
        assert_eq!(row.error_code.as_deref(), Some("FORBIDDEN_ROLE"));
    }

    #[tokio::test]
    async fn a_404_on_a_manifested_entity_is_terminal() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        Mock::given(method("GET"))
            .and(path_matcher(format!("/content/lesson/{LESSON}")))
            .respond_with(ResponseTemplate::new(404))
            .mount(&harness.server)
            .await;

        harness.run().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Failed);
        assert_eq!(row.error_code.as_deref(), Some(codes::LESSON_NOT_FOUND));
    }

    #[tokio::test]
    async fn a_verifying_entry_is_re_verified_rather_than_re_downloaded() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        std::fs::write(harness.partial(), &bytes).expect("complete temp file");
        harness
            .db
            .with(|c| {
                queue::set_state(
                    c,
                    LESSON,
                    QueueState::Verifying,
                    0,
                    bytes.len() as i64,
                    None,
                    None,
                    None,
                    None,
                )
            })
            .expect("verifying");

        // No content route is mounted. Any HTTP request would answer 404 and
        // fail the entry, so reaching DONE proves the transfer was not repeated.
        harness.run().await;

        assert_eq!(harness.row().state, QueueState::Done);
        assert_eq!(
            harness
                .server
                .received_requests()
                .await
                .expect("requests")
                .len(),
            0
        );
    }

    #[tokio::test]
    async fn a_package_with_no_structure_behind_it_is_never_marked_done() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        // Deliberately no structure: the replica write will affect no rows.
        harness.enqueue(12, &digest, bytes.len() as i64);
        mount_package(&harness.server, bytes).await;

        harness.run().await;

        let row = harness.row();
        assert_ne!(
            row.state,
            QueueState::Done,
            "an entity marked done with no rows behind it would satisfy every future delta"
        );
        assert_eq!(row.state, QueueState::Failed);
        let lessons: i64 = harness
            .db
            .with(|c| c.query_row("SELECT count(*) FROM lessons;", [], |row| row.get(0)))
            .expect("count");
        assert_eq!(lessons, 0, "the transaction rolled back as one unit");
    }

    #[tokio::test]
    async fn the_pump_drains_a_batch_and_announces_it_once() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        mount_package(&harness.server, bytes).await;

        harness.engine.pump().await;

        assert_eq!(harness.row().state, QueueState::Done);
        let batches = harness.sink.batches.lock().expect("lock");
        assert_eq!(batches.len(), 1);
        assert_eq!(batches[0].completed_entities, 1);
        assert_eq!(batches[0].failed_entities, 0);
    }

    #[tokio::test]
    async fn the_pump_leaves_paused_entries_alone() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        harness
            .db
            .with(|c| queue::pause(c, None, PauseReason::User))
            .expect("pause");

        harness.engine.pump().await;

        let row = harness.row();
        assert_eq!(row.state, QueueState::Paused);
        assert_eq!(row.pause_reason, Some(PauseReason::User));
        assert_eq!(
            harness
                .server
                .received_requests()
                .await
                .expect("requests")
                .len(),
            0
        );
    }

    #[test]
    fn backoff_grows_and_stays_within_its_jitter() {
        let engine = Engine::new(
            Arc::new(Db::new(open_in_memory().expect("store"))),
            ContentClient::new("http://127.0.0.1:1"),
            std::env::temp_dir(),
            Arc::new(RecordingSink::default()) as Arc<dyn EventSink>,
            EngineConfig::default(),
        );

        let first = engine.backoff(1);
        let second = engine.backoff(2);
        assert!(first >= Duration::from_secs(2) && first < Duration::from_millis(2_500));
        assert!(second >= Duration::from_secs(4) && second < Duration::from_millis(5_000));
    }

    #[tokio::test]
    async fn byte_progress_is_throttled_but_state_changes_are_not() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        let row = harness.row();

        // Three byte updates in a row: only the first gets through, because a
        // fast connection would otherwise flood the WebView.
        harness.engine.emit(&row, false).expect("emit");
        harness.engine.emit(&row, false).expect("emit");
        harness.engine.emit(&row, false).expect("emit");
        assert_eq!(harness.sink.states().len(), 1);

        harness.engine.emit(&row, true).expect("emit");
        assert_eq!(
            harness.sink.states().len(),
            2,
            "a state change is published whatever the throttle says"
        );
    }

    #[tokio::test]
    async fn a_restart_requeues_downloading_entries_and_sweeps_orphan_partials() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        std::fs::write(harness.partial(), &bytes[..10]).expect("seed partial");
        harness
            .db
            .with(|c| {
                queue::set_state(
                    c,
                    LESSON,
                    QueueState::Downloading,
                    1,
                    10,
                    None,
                    None,
                    None,
                    None,
                )
            })
            .expect("downloading");

        let orphan = harness.engine.partials.join("someone-elses.v1.part");
        std::fs::write(&orphan, b"left behind").expect("orphan");

        harness.engine.recover().expect("recover");

        let row = harness.row();
        assert_eq!(row.state, QueueState::Queued);
        assert_eq!(row.attempt, 1, "recovery is not a retry");
        assert!(
            harness.partial().exists(),
            "the partial has to survive so the next attempt can resume"
        );
        assert!(
            !orphan.exists(),
            "a partial no live entry refers to is pure waste of disk"
        );
    }

    #[tokio::test]
    async fn the_pump_drains_more_entities_than_the_concurrency_cap() {
        let harness = Harness::new().await;
        let bytes = package_bytes(12, "# Signals");
        let digest = verify::digest_of_bytes(&bytes);
        harness.seed_structure(12, &digest, bytes.len() as i64);
        harness.enqueue(12, &digest, bytes.len() as i64);
        mount_package(&harness.server, bytes.clone()).await;

        // Four more entries than one round can carry, so the pump has to loop.
        // They have no replica row, so they fail rather than complete -- which
        // is exactly what makes the batch's terminal count observable.
        for index in 5..9 {
            harness
                .db
                .with(|c| {
                    queue::enqueue(
                        c,
                        &format!("018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b7{index}"),
                        EntityType::Lesson,
                        Some(TRACK),
                        "batch-1",
                        12,
                        &digest,
                        bytes.len() as i64,
                    )
                })
                .expect("enqueue");
        }

        harness.engine.pump().await;

        let open = harness.db.with(queue::has_open_work).expect("open work");
        assert!(!open, "the pump runs until nothing is left working");

        let batches = harness.sink.batches.lock().expect("lock");
        assert_eq!(batches.len(), 1, "one edge per batch, not one per entity");
        assert_eq!(
            batches[0].completed_entities + batches[0].failed_entities,
            5
        );
    }

    #[tokio::test]
    async fn a_stale_manifest_is_detected_before_the_bytes_are_hashed() {
        let harness = Harness::new().await;
        let full = package_bytes(12, "# Signals");
        let old_digest = verify::digest_of_bytes(&full);
        harness.seed_structure(12, &old_digest, full.len() as i64);
        harness.enqueue(12, &old_digest, full.len() as i64);

        // More bytes on disk than the manifest promised: the manifest is stale,
        // and nothing can be salvaged from the partial.
        let mut oversized = full.clone();
        oversized.extend_from_slice(b"and then some");
        std::fs::write(harness.partial(), &oversized).expect("seed partial");
        let stale_partial = harness.partial();

        let new = package_bytes(13, "# Signals, revised");
        let new_digest = verify::digest_of_bytes(&new);
        Mock::given(method("GET"))
            .and(path_matcher(format!("/manifest/track/{TRACK}")))
            .respond_with(ResponseTemplate::new(200).set_body_json(manifest_json(
                13,
                &new_digest,
                new.len() as i64,
            )))
            .mount(&harness.server)
            .await;

        harness.run().await;

        assert!(!stale_partial.exists());
        let row = harness.row();
        assert_eq!(row.content_version, 13);
        assert_eq!(row.attempt, 0);
        assert_eq!(
            harness
                .server
                .received_requests()
                .await
                .expect("requests")
                .iter()
                .filter(|request| request.url.path().starts_with("/content/"))
                .count(),
            0,
            "no transfer is started against a manifest already known to be stale"
        );
    }
}
