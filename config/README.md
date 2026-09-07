# Shared build-time configuration

Values that more than one subproject has to agree on, and that therefore cannot
live inside any one of them.

## `api-endpoints.json`

The absolute base of the REST API, including the `/api/v1` prefix and no
trailing slash — one entry per build profile.

Two independent HTTP clients talk to this API from the desktop application: the
Angular bundle inside the webview, and the Rust download engine. They are
configured in different languages by different build systems, and nothing makes
them agree except reading the same file:

| Consumer | How it reads this file | Which key |
|---|---|---|
| `frontend/src/environments/*.ts` | imported directly, resolved at bundle time | by build configuration |
| `desktop/src-tauri/build.rs` | read at compile time, emitted as a compile-time constant | `debug_assertions` selects it |

The failure this prevents is silent and shipped: with the value written twice, a
packaged build whose environment variable was never set had the interface
talking to one host and the engine to another. Nothing catches that — the
content security policy governs the webview and says nothing about the engine's
own requests, and both halves look healthy in isolation.

`development` points at port 18080 rather than the framework default because
another service already holds 8080 on the development machine.
