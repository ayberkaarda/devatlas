//! A single, quiet check for a newer release, run once per launch -- and the
//! matching install, deferred to the moment the application is already
//! leaving.
//!
//! The specification asks for automatic updates through
//! `tauri-plugin-updater` with no interface of its own: the Angular layer
//! never asks whether an update exists and is never told the answer, so
//! there is nothing to add to the frozen `invoke` surface or the event list
//! here.
//!
//! **Checking and downloading** happen in [`check_in_background`], spawned
//! from `setup()` after the window already exists, never awaited by
//! startup. Every way this can fail -- no network, an endpoint with no
//! release behind it yet, a response that does not parse, a public key that
//! has not been set -- ends the same way, in a log line, never in a panic
//! and never in a delay the person opening the application can feel.
//!
//! **Installing is deliberately not part of that same step.** It was,
//! originally, and that turned out to be wrong: `Update::download_and_install`
//! installs immediately, and on Windows, installing exits the running
//! process there and then -- "on Windows the application is automatically
//! exited when the install step is executed" is the plugin's own
//! documentation of it, and it holds regardless of `restart_after_install`,
//! which only controls whether the installer reopens the application
//! afterwards, not whether it closes it first. A background task that can
//! finish at an arbitrary point after startup is exactly the wrong place for
//! that: it would mean the application can vanish out from under someone
//! reading a lesson, with no dialog and nothing to explain it. Downloading
//! and installing are therefore split along the boundary the plugin already
//! has -- [`Update::download`] and [`Update::install`] -- and only the first
//! runs in the background. The second runs from the `RunEvent::Exit` handler
//! in `lib.rs`, the point where the run loop is already ending on every
//! platform, so Windows' forced exit costs nothing there: the process was
//! leaving anyway. On macOS and Linux nothing forces an exit either way; the
//! new version simply is not picked up until the next launch, which is the
//! outcome wanted everywhere.
//!
//! **Where the downloaded bytes live in between:** in memory, in
//! [`PendingUpdate`], application-managed state set once when a download
//! finishes and taken at most once, at exit. Update packages here are this
//! application's own installer artifacts (NSIS/MSI on Windows, an AppImage
//! or a Debian package on Linux) -- tens of megabytes at the very most,
//! nowhere near a size that matters against what a desktop machine has free,
//! and held only for the remaining lifetime of a session that was going to
//! hold much larger things anyway (downloaded lesson content). Spilling to a
//! file under the application's cache directory was the alternative
//! considered; it was not taken because it buys nothing at this size and
//! costs a file lifecycle -- a path to resolve, a write to fail, a stale
//! leftover to clean up after a crash -- for no benefit, since durability
//! across a crash is not a goal here in the first place: if the process is
//! killed before `RunEvent::Exit` runs, the pending update is simply gone
//! with it, nothing is installed, and the next launch's background check
//! downloads again. That is acceptable and converges; it is not a case
//! worth engineering around.
//!
//! Relaunching is deliberately not attempted anywhere in this module. If an
//! update installs, the new version is picked up the next time someone
//! opens the application, not by the application reopening itself.

use std::sync::Mutex;

use tauri::Manager;
use tauri_plugin_updater::{Update, Updater, UpdaterExt};

/// A downloaded, not-yet-installed update, along with the bytes
/// `Update::install` needs.
///
/// `pub` only so [`check_and_download`] can be `pub` for
/// `tests/update_check.rs` -- its fields stay private, so nothing outside
/// this module can construct or inspect one, only hold it and hand it back.
pub struct DownloadedUpdate {
    update: Update,
    bytes: Vec<u8>,
}

/// Application-managed state: at most one update, downloaded and waiting for
/// the application to leave. `None` until a background check finds one and
/// downloads it cleanly; taken (and installed) at most once, at exit.
type PendingUpdate = Mutex<Option<DownloadedUpdate>>;

/// Spawns the check-and-download as a background task and returns
/// immediately. Called once from `setup()`, after the window has already
/// been created, so it can never be on the path that decides when the
/// window appears.
///
/// Generic over the Tauri runtime so tests can drive it against
/// `tauri::test::MockRuntime` instead of the real `Wry` webview runtime that
/// `setup()` passes in.
pub fn check_in_background<R: tauri::Runtime>(app: tauri::AppHandle<R>) {
    app.manage(PendingUpdate::new(None));
    tauri::async_runtime::spawn(async move {
        run(app).await;
    });
}

/// Builds the updater from the application's configuration, checks once, and
/// stashes what it downloads for [`install_pending_update_before_exit`] to
/// install later. Every error is logged and swallowed -- there is no caller
/// left to hand an error to once this task starts running on its own.
pub async fn run<R: tauri::Runtime>(app: tauri::AppHandle<R>) {
    let updater = match app.updater_builder().restart_after_install(false).build() {
        Ok(updater) => updater,
        Err(error) => {
            log::warn!("the updater could not be configured, continuing without it: {error}");
            return;
        }
    };

    match check_and_download(&updater).await {
        Ok(Some(downloaded)) => {
            log::info!("update downloaded; it will be installed when the application exits");
            if let Some(state) = app.try_state::<PendingUpdate>() {
                let mut guard = state
                    .lock()
                    .unwrap_or_else(|poisoned| poisoned.into_inner());
                *guard = Some(downloaded);
            } else {
                // `check_in_background` always calls `app.manage` first, so
                // this is unreachable in practice; it is handled rather than
                // unwrapped because a downloaded update quietly going
                // nowhere is a poor trade for a panic on a background task.
                log::warn!("no place to stash the downloaded update; discarding it");
            }
        }
        Ok(None) => log::debug!("no update is available"),
        Err(error) => {
            log::warn!("the update check did not complete, continuing without it: {error}");
        }
    }
}

/// Checks once and, if an update is available, downloads it. Returns
/// `Ok(None)` when the running version is already current, and `Err` for
/// every failure this can have -- no network, a `404` from an endpoint with
/// no release published yet, a response that fails to parse, or (once a real
/// update exists) a signature that does not verify against the configured
/// public key.
///
/// Split out from [`run`] so a test can drive it against a deliberately
/// unreachable or misbehaving endpoint without needing a real update server.
/// Installing is not part of this function -- see the module documentation
/// for why.
pub async fn check_and_download(
    updater: &Updater,
) -> tauri_plugin_updater::Result<Option<DownloadedUpdate>> {
    let Some(update) = updater.check().await? else {
        return Ok(None);
    };

    log::info!("update {} is available, downloading", update.version);
    let bytes = update.download(|_, _| {}, || {}).await?;
    Ok(Some(DownloadedUpdate { update, bytes }))
}

/// Installs whatever [`run`] downloaded and stashed, if anything. Called
/// exactly once, from the `RunEvent::Exit` handler in `lib.rs` -- the point
/// where the run loop is already ending on every platform, which is what
/// makes it safe to install here even on Windows, where installing forces
/// the process to exit.
///
/// A missing or already-empty [`PendingUpdate`] is not an error: it is the
/// ordinary case where the check found nothing, is still running, or already
/// failed. Nothing here can delay shutdown beyond running the installer
/// itself, which `Update::install` already limits to launching it.
pub fn install_pending_update_before_exit<R: tauri::Runtime>(app: &tauri::AppHandle<R>) {
    let Some(state) = app.try_state::<PendingUpdate>() else {
        return;
    };
    let pending = {
        let mut guard = state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        guard.take()
    };
    let Some(pending) = pending else {
        return;
    };

    match pending.update.install(pending.bytes) {
        Ok(()) => {
            log::info!("update installed; it will run the next time the application starts");
        }
        Err(error) => log::warn!("the downloaded update could not be installed: {error}"),
    }
}
