//! Build-time wiring for values that must not be typed twice.
//!
//! The API base address is the case in point: the Angular bundle and this
//! engine are two independent HTTP clients configured by two different build
//! systems, and nothing keeps them pointed at the same server except reading
//! the same file. `config/api-endpoints.json`, one directory above the
//! monorepo's `desktop/` and `frontend/` trees, is that file. The Angular
//! side resolves it at bundle time through its environment files; this side
//! resolves it here, at compile time, and bakes the result into the binary as
//! `env!("BYTELORE_COMPILED_API_BASE_URL")` (see `src/http.rs`).
//!
//! Failing loudly is the point. A silent fallback here — compiling a stale or
//! placeholder address when the shared file cannot be read — would reproduce
//! exactly the bug this file exists to close: a packaged build whose engine
//! and interface quietly talk to different hosts.

use std::path::{Path, PathBuf};

fn main() {
    let manifest_dir =
        PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR is not set"));
    let config_path = manifest_dir.join("../../config/api-endpoints.json");

    // Re-run only when the shared file changes, not on every build.rs
    // invocation.
    println!("cargo:rerun-if-changed={}", config_path.display());

    // Cargo always sets `DEBUG` for build scripts to exactly "true" or
    // "false", mirroring whether the profile being compiled enables
    // `debug_assertions` for the crate itself -- so reading it here selects
    // the same key the crate's own `cfg!(debug_assertions)` would select.
    let debug_assertions = std::env::var("DEBUG").unwrap_or_else(|error| {
        panic!("Cargo did not set the DEBUG build-script variable: {error}")
    });
    let key = match debug_assertions.as_str() {
        "true" => "development",
        "false" => "production",
        other => panic!(
            "Cargo's DEBUG build-script variable was \"{other}\", expected \"true\" or \"false\""
        ),
    };

    let base_url = read_endpoint(&config_path, key);
    println!("cargo:rustc-env=BYTELORE_COMPILED_API_BASE_URL={base_url}");

    // `tauri_build::build()` below embeds a manifest into the shipped
    // application binary. The updater's tests need Tauri's windowing backend
    // (through `tauri::test`), which calls a common-controls-v6 API; without
    // a manifest declaring that dependency, Windows resolves "comctl32.dll"
    // to its ancient v5 copy and whatever links that backend fails to even
    // start with STATUS_ENTRYPOINT_NOT_FOUND. `rustc-link-arg-tests` reaches
    // only `tests/*.rs` integration binaries, never the shipped binary, which
    // is exactly the scope wanted here -- it is why those tests live in
    // `tests/update_check.rs` rather than as a `#[cfg(test)]` module in
    // `src/`, where nothing but the unscoped instruction (which also touches
    // the shipped binary, and collides with its own manifest) would reach
    // them.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let test_manifest = manifest_dir.join("windows-test-harness.manifest");
        println!("cargo:rerun-if-changed={}", test_manifest.display());
        println!("cargo:rustc-link-arg-tests=/MANIFEST:EMBED");
        println!(
            "cargo:rustc-link-arg-tests=/MANIFESTINPUT:{}",
            test_manifest.display()
        );
    }

    tauri_build::build()
}

/// Reads `key` out of the shared endpoint configuration, panicking with a
/// message that names the file, the reason, and what depends on it — this
/// runs at compile time, so a terse `unwrap()` would leave whoever hits it
/// staring at a line number in a file they did not write.
fn read_endpoint(config_path: &Path, key: &str) -> String {
    let raw = std::fs::read_to_string(config_path).unwrap_or_else(|error| {
        panic!(
            "could not read the shared API endpoint configuration at {}: {error}\n\
             This file is the single source of the API base address shared with the \
             Angular bundle (see config/README.md). The desktop engine refuses to \
             compile without it rather than silently falling back to a guessed \
             address that could point at the wrong server.",
            config_path.display()
        )
    });

    let parsed: serde_json::Value = serde_json::from_str(&raw).unwrap_or_else(|error| {
        panic!(
            "{} does not contain valid JSON: {error}",
            config_path.display()
        )
    });

    let value = parsed.get(key).unwrap_or_else(|| {
        panic!(
            "{} has no \"{key}\" key; the compiled default cannot be selected for this build \
             profile",
            config_path.display()
        )
    });

    value
        .as_str()
        .unwrap_or_else(|| {
            panic!(
                "{}'s \"{key}\" value must be a string, found: {value}",
                config_path.display()
            )
        })
        .to_string()
}
