//! Integration tests for `bytelore_lib::update_check`.
//!
//! These live here, as a `tests/*.rs` binary, rather than as a
//! `#[cfg(test)]` module inside `src/update_check.rs`, for a build-mechanics
//! reason worth recording once instead of rediscovering: driving the updater
//! requires `tauri::test::MockRuntime`, which links Tauri's real windowing
//! backend into whatever binary uses it. That backend calls a
//! common-controls-v6 Windows API, and without a manifest declaring that
//! dependency the OS resolves `comctl32.dll` to its ancient v5 copy, so the
//! binary fails to even start (`STATUS_ENTRYPOINT_NOT_FOUND`) before a
//! single test runs. `build.rs` supplies that manifest with
//! `cargo:rustc-link-arg-tests`, which Cargo only wires up for a real
//! `tests/` integration binary -- there is no equivalent hook for a unit
//! test compiled from `src/`, and the alternative (an unscoped link-arg)
//! also reaches the shipped application binary and collides there with the
//! manifest `tauri_build::build()` already embeds.
//!
//! Every test here builds its own `MockRuntime` app with the real plugin
//! registered and then overrides the endpoint per call, so none of this
//! depends on the address in `tauri.conf.json` or on a live network -- the
//! point is to prove the failure paths are contained, not to reach GitHub.

use bytelore_lib::update_check::{
    check_and_download, check_in_background, install_pending_update_before_exit, run,
};
use tauri_plugin_updater::{Updater, UpdaterExt};
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

// A syntactically valid minisign public key is not required for any path
// exercised here: none of these responses ever reach signature
// verification, because none of them describe an available update. This
// string only has to be present, matching the placeholder shipped in
// `tauri.conf.json` until the real key is generated.
const PLACEHOLDER_PUBKEY: &str = "UNSET-REPLACE-BEFORE-FIRST-RELEASE";

/// A `MockRuntime` app with the real plugin registered, so
/// `app.updater_builder()` resolves exactly as it does at startup.
///
/// The plugin's configuration is a required struct, not an optional one --
/// `mock_context`'s default has no `plugins.updater` entry at all, and the
/// plugin's own config type has no `Default` implementation reachable during
/// deserialization, so registering it against the bare mock context fails
/// before any test body runs. The placeholder pubkey is inserted directly
/// into the context's config here to stand in for what `tauri.conf.json`
/// provides at runtime.
fn mock_app() -> tauri::App<tauri::test::MockRuntime> {
    let mut context = tauri::test::mock_context(tauri::test::noop_assets());
    context.config_mut().plugins.0.insert(
        "updater".to_string(),
        serde_json::json!({ "pubkey": PLACEHOLDER_PUBKEY, "endpoints": [] }),
    );

    tauri::test::mock_builder()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .build(context)
        .expect("mock app builds")
}

fn updater_pointed_at(app: &tauri::AppHandle<tauri::test::MockRuntime>, endpoint: &str) -> Updater {
    app.updater_builder()
        .endpoints(vec![endpoint.parse().expect("valid test url")])
        .expect("test endpoint accepted")
        .restart_after_install(false)
        .build()
        .expect("updater builds even with a placeholder key")
}

#[tokio::test]
async fn an_unreachable_server_fails_the_check_without_panicking() {
    // A bound-then-dropped listener yields a port nothing answers on, so the
    // connection is refused immediately rather than timing out.
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind");
    let port = listener.local_addr().expect("addr").port();
    drop(listener);

    let app = mock_app();
    let updater = updater_pointed_at(
        app.handle(),
        &format!("http://127.0.0.1:{port}/latest.json"),
    );

    let result = check_and_download(&updater).await;
    assert!(result.is_err(), "expected the check to fail, got an Ok");
}

#[tokio::test]
async fn a_404_from_an_endpoint_with_no_release_yet_fails_the_check_without_panicking() {
    // This is the endpoint's actual state today: the GitHub repository has
    // published no release, so `releases/latest/download/latest.json`
    // answers 404. This is the path that must be the well-tested one.
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/latest.json"))
        .respond_with(ResponseTemplate::new(404))
        .mount(&server)
        .await;

    let app = mock_app();
    let updater = updater_pointed_at(app.handle(), &format!("{}/latest.json", server.uri()));

    let result = check_and_download(&updater).await;
    assert!(result.is_err(), "expected the check to fail, got an Ok");
}

#[tokio::test]
async fn a_malformed_response_fails_the_check_without_panicking() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/latest.json"))
        .respond_with(ResponseTemplate::new(200).set_body_string("not json"))
        .mount(&server)
        .await;

    let app = mock_app();
    let updater = updater_pointed_at(app.handle(), &format!("{}/latest.json", server.uri()));

    let result = check_and_download(&updater).await;
    assert!(result.is_err(), "expected the check to fail, got an Ok");
}

#[tokio::test]
async fn a_204_response_reports_no_update_available_without_downloading_anything() {
    // A 204 is the convention some servers use for "nothing to report"; the
    // plugin turns it into `Ok(None)` directly. Whichever of the two this
    // resolves to, it must not panic -- and it must not report anything to
    // install either, since nothing was downloaded.
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/latest.json"))
        .respond_with(ResponseTemplate::new(204))
        .mount(&server)
        .await;

    let app = mock_app();
    let updater = updater_pointed_at(app.handle(), &format!("{}/latest.json", server.uri()));

    if let Ok(downloaded) = check_and_download(&updater).await {
        assert!(
            downloaded.is_none(),
            "a 204 must never be read as an update"
        );
    }
}

#[tokio::test]
async fn a_failed_check_leaves_nothing_pending_and_exit_stays_quiet() {
    // Exercises the exact function startup calls, with the placeholder
    // public key and no endpoint configured -- "the updater is unconfigured"
    // -- end to end through to the exit-time install step. `run` must
    // swallow the failure itself, and the later install call must find
    // nothing to do and cause no panic.
    let app = mock_app();
    check_in_background(app.handle().clone());

    // `check_in_background` only spawns the check; give the spawned task a
    // moment to actually run and finish (or fail) before asserting on its
    // effect. It fails immediately here -- no endpoint is configured -- so
    // this is not racing a real network call.
    tokio::time::sleep(std::time::Duration::from_millis(200)).await;

    // Nothing to install, and nothing panics either.
    install_pending_update_before_exit(app.handle());
}

#[tokio::test]
async fn run_and_the_exit_step_are_both_quiet_when_no_state_was_ever_managed() {
    // `run` and `install_pending_update_before_exit` are only ever called
    // after `check_in_background` has managed the `PendingUpdate` state, but
    // both are written to degrade rather than panic if that were somehow not
    // true -- a downloaded update quietly going nowhere is a far better
    // failure than a panic on a background task or at shutdown. Driving them
    // with no state managed at all, deterministically and without a spawn to
    // wait on, is what proves that defensive path rather than assuming it.
    let app = mock_app();
    run(app.handle().clone()).await;
    install_pending_update_before_exit(app.handle());
}
