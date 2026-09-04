mod commands;
mod db;
mod delta;
mod engine;
mod error;
mod events;
mod http;
mod manifest;
mod model;
mod queue;
mod replica;
mod settings;
mod store;
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
    tauri::Builder::default()
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

            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { .. } = event {
                log::info!("window {} closing", window.label());
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
