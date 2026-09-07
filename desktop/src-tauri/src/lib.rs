mod commands;
mod db;
mod delta;
mod engine;
mod error;
mod events;
mod http;
#[cfg(test)]
mod live_tests;
mod manifest;
mod model;
mod queue;
mod replica;
mod settings;
mod store;
// `pub`, not private: `tests/update_check.rs` is an integration test that
// links against this crate from the outside, and needs `run` and
// `check_and_download` (see that module's doc comment for why the tests live
// there rather than in a `#[cfg(test)]` block here).
#[cfg(any(target_os = "macos", windows, target_os = "linux"))]
pub mod update_check;
mod verify;

use std::sync::Arc;

use tauri::{Manager, WindowEvent};

use crate::commands::AppState;
use crate::db::Db;
use crate::engine::{Engine, EngineConfig};
use crate::events::TauriEventSink;
use crate::http::{configured_base_url, ContentClient};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    #[allow(unused_mut)]
    let mut builder = tauri::Builder::default();
    // Not registered on a hypothetical mobile build: the plugin does not
    // support mobile targets at all (see the target-gated dependency in
    // Cargo.toml), and this project packages Windows and Linux only.
    #[cfg(any(target_os = "macos", windows, target_os = "linux"))]
    {
        builder = builder.plugin(tauri_plugin_updater::Builder::new().build());
    }

    builder
        .invoke_handler(tauri::generate_handler![
            commands::library_list_tracks,
            commands::library_get_track,
            commands::library_get_lesson,
            commands::library_get_mind_map,
            commands::library_refresh,
            commands::download_enqueue,
            commands::download_pause,
            commands::download_resume,
            commands::download_cancel,
            commands::download_retry,
            commands::download_queue_state,
            commands::download_delete,
            commands::progress_mark,
            commands::progress_pending,
            commands::progress_list,
            commands::progress_apply_results,
            commands::progress_absorb,
            commands::settings_get,
            commands::settings_set,
            commands::session_store,
            commands::session_load,
            commands::session_clear,
            commands::window_show,
        ])
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            let path = store::database_path(app.handle())?;
            let partials = store::partial_directory(app.handle())?;
            let connection = store::open(&path)?;
            log::info!(
                "local store ready at {} (schema version {})",
                path.display(),
                store::schema_version(&connection)?
            );

            let db = Arc::new(Db::new(connection));
            let client = ContentClient::new(configured_base_url());
            log::info!("content endpoints at {}", client.base_url());

            let engine = Engine::new(
                db,
                client,
                partials,
                Arc::new(TauriEventSink::new(app.handle().clone())),
                EngineConfig::default(),
            );

            // A restart finds entries mid-transfer. Anything that was
            // downloading goes back to the queue with its partial intact, and
            // anything that was verifying keeps that state so its complete file
            // is re-hashed rather than fetched again.
            if let Err(error) = engine.recover() {
                log::warn!("the download queue could not be recovered: {error}");
            }

            let resume = Arc::clone(&engine);
            tauri::async_runtime::spawn(async move { resume.pump().await });

            app.manage(AppState { engine });

            // Spawned, not awaited: the window is already created by the time
            // `setup` runs, and this must never be on the path that decides
            // when it is shown. Every failure this can have -- offline, no
            // release published yet, a bad response, an unset public key --
            // is logged inside `update_check::run` and never propagated here.
            #[cfg(any(target_os = "macos", windows, target_os = "linux"))]
            update_check::check_in_background(app.handle().clone());

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { .. } = event {
                log::info!("window {} closing", window.label());
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app_handle, event| {
            // The one point an update is installed. Windows exits the process
            // as part of installing regardless of when that happens, so
            // doing it here -- as the run loop is already ending, not while
            // someone is mid-lesson -- is the point where that cost is free
            // rather than a surprise. See `update_check` for what was
            // downloaded and stashed while the application was running.
            #[cfg(any(target_os = "macos", windows, target_os = "linux"))]
            if let tauri::RunEvent::Exit = event {
                update_check::install_pending_update_before_exit(app_handle);
            }
        });
}
