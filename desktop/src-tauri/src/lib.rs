mod store;

use tauri::{Manager, WindowEvent};

/// Reveals the main window.
///
/// The window is configured with `visible: false` so that nothing is painted
/// before the UI has resolved and applied the colour theme; showing it earlier
/// produces a flash of the wrong theme on every launch. The frontend calls this
/// once the theme is on the document, and the two halves of that arrangement
/// have to change together: removing `visible: false` from the window config
/// makes this command pointless, and removing the call leaves a window that
/// never appears.
#[tauri::command]
fn show_main_window(app: tauri::AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("main")
        .ok_or_else(|| "main window not found".to_string())?;
    window.show().map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![show_main_window])
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            let path = store::database_path(app.handle())?;
            let connection = store::open(&path)?;
            log::info!(
                "local store ready at {} (schema version {})",
                path.display(),
                store::schema_version(&connection)?
            );

            // Placeholder until the UI owns the reveal: the frontend does not
            // call show_main_window yet, and without this the window would stay
            // hidden forever. Remove this block in the same change that makes
            // the theme code call the command.
            if let Some(window) = app.get_webview_window("main") {
                window.show()?;
            }

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
