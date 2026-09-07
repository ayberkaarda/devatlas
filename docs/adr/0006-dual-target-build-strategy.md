# 0006. Dual-target build strategy: one Angular codebase, two platforms

## Status

Accepted

## Context

One Angular codebase ships as two products: a public web site talking to the
REST API over HTTP, and the UI layer of a desktop application talking to a
local SQLite replica and a Rust download engine through Tauri `invoke`
(`docs/protocol/platform-service.md` §1). The rule the whole abstraction
exists to enforce is stated as the document's opening line: "A component
never knows which platform it is running on. Not 'rarely', not 'except for
downloads'. Never."

The mechanics that make two targets buildable from one source tree already
exist and are frozen: `frontend/angular.json` defines four build
configurations — `production` (implicitly the web default), `web`, `tauri`
and `tauri-development` — and the two platform-specific ones each carry a
`fileReplacements` entry that swaps `src/environments/environment.ts` for
`environment.tauri.ts` or `environment.tauri-development.ts` at build time.
`frontend/src/environments/environment.model.ts` defines the `AppEnvironment`
shape (`platform: 'web' | 'tauri'`, `production`, `apiBaseUrl`) that every
concrete environment file implements.

## Options considered

### A. `PlatformService` as an abstract class with two full implementations, versus conditional imports or a runtime `if (isTauri)`

- **Rejected: a runtime `if (isTauri)` scattered through components.**
  `platform-service.md` §1 names this directly as "the temptation this rule
  resists," and states why it does not scale: "Every occurrence of it is a
  missing method on the abstraction. When behaviour genuinely differs, the
  difference is expressed as data — a capability flag or a view-model field
  — and the component branches on the data, not on the platform." A
  component that checks a platform flag has already learned something it was
  never supposed to know, and that knowledge has to be re-verified in every
  future component that touches the same behaviour.
- **Rejected: conditional imports selecting a module per build.** This would
  still require every call site to import "the right one" correctly, with
  no compiler check that both sides of a build actually implement the same
  surface — a method added to the web path and forgotten on the desktop
  path would only be caught by testing the omitted build, not by the type
  checker.
- **Chosen: `PlatformService` as an `abstract class`, with `TauriPlatformService`
  and `WebPlatformService` as two complete implementations, selected once
  by dependency injection** (`platform.providers.ts`:
  `useClass: environment.platform === 'tauri' ? TauriPlatformService :
  WebPlatformService`). Every member is `abstract`, never optional
  (`platform-service.md` §2: "Adding a method is therefore a change to
  three files at once — the abstraction and both implementations — and the
  compiler refuses anything less. An optional method would let one
  implementation quietly lack a feature and fail at runtime instead.")
  This converts "did you remember the other platform" from a code-review
  question into a compiler error.

### B. Unsupported operations: throw, or return an empty success

A method that only makes sense on one platform — `enqueueDownload` on the
web, `pendingProgress` where there is no local queue to drain — has to answer
somehow when called on the platform where it does not apply.

- **Rejected: return an empty array / a no-op success.** `platform-service.md`
  §5 and §8 rule 3 state the reasoning identically: "Unsupported methods
  throw rather than return empty... A silent empty array is indistinguishable
  from 'there is genuinely nothing,' and it would let a bug ship looking
  like a legitimate empty state." Section 10.2 makes the same case for
  `pendingProgress()`/`applyProgressResults()` on the web: returning `[]`
  there "would be the silent empty success rule 3 exists to forbid," because
  on the web `markProgress` already *is* the sync — there is no queue whose
  emptiness `[]` could honestly represent.
- **Chosen: throw `UnsupportedOnWebError`.** Combined with rule 4 ("Controls
  for unavailable capabilities are not rendered at all, rather than
  rendered and failing on click"), the throw is a backstop against a bug —
  a control that should have been hidden by `capabilities.canDownload` but
  was not — rather than a path a correct UI is expected to hit in normal
  operation.

### C. Typecheck scope: whatever the CLI's own `tsconfig.app.json` reaches, or every file under `src/`

`platform-service.md` §5.1 states this explicitly as a consequence of
option A, not a separate stylistic choice: "Both implementations are
compiled in both builds; only one is reachable. This is why the typecheck
gate covers every file under `src/` rather than only what the entry point
reaches — in each build one implementation is dead code, and a narrower
check would let the unused half rot while the gate stayed green." The same
sentence, nearly verbatim, is repeated as the rationale comment inside
`providePlatform()` in `frontend/src/app/core/platform/platform.providers.ts`.

- **Rejected: rely on the Angular CLI's own build-time type checking**,
  scoped to what `src/main.ts` transitively imports. Whichever
  implementation a given build does not select — `TauriPlatformService`
  compiled into the web build, `WebPlatformService` compiled into the
  desktop build — is live code that ships in the same source tree but is
  never the one that DI resolves to at runtime for that target. A
  type-checking pass that only follows reachable imports from the entry
  point can, depending on the exact scope the CLI's generated config uses,
  either still see this code (because both classes are registered,
  unconditionally, in the same `providePlatform()` function that *is* on
  the entry path) or fail to see something adjacent that only the unused
  half references. Scoping the gate to "whatever the build reaches" ties
  the correctness check to a boundary that is a CLI implementation detail,
  not a boundary this project chose.
- **Chosen: a typecheck configuration that covers every file under `src/**`
  unconditionally**, independent of what either build's entry point
  imports. This is the direct, structural fix for the shape of the risk
  section 5.1 names: whichever implementation is *not* selected for a given
  build is dead code for that build specifically, so any check whose scope
  is tied to reachability from that build's own entry point is, by
  construction, blind to exactly the half it did not select.

### D. The shared environment type: import it from `environment.ts`, or from a file the build never replaces

`environment.model.ts` exists because of a trap that was actually hit: a
type shared by every environment file cannot itself live in
`environment.ts`, because that is precisely the file each platform-specific
build configuration replaces. The module's own doc comment states the
failure mode directly: "The `tauri` build swaps `environment.ts` for
`environment.tauri.ts` through `angular.json` `fileReplacements`, so a type
imported from `./environment` would resolve to the replacement file itself
during that build and the module would import its own declaration."

- **Rejected: declare `AppEnvironment` inside `environment.ts` and have the
  other environment files import it from there.** This works for the web
  build, where `environment.ts` is the file actually used, and breaks
  specifically for the target whose `fileReplacements` entry substitutes
  that same file — the tauri build — because at that point the file
  importing from `./environment` *is* `environment.tauri.ts` standing in
  for `environment.ts`, and a module cannot import a declaration from
  itself. The defect is asymmetric in exactly the way that makes it easy to
  miss: it passes for the build configuration a developer is most likely to
  run first (`ng serve`, the web development target) and fails only for the
  one `fileReplacements` swaps.
- **Chosen: `environment.model.ts`, a module none of the four build
  configurations ever replaces**, holding only the `AppEnvironment`
  interface. Every concrete environment file — `environment.ts`,
  `environment.development.ts`, `environment.tauri.ts`,
  `environment.tauri-development.ts` — imports the type from there and
  never from each other.

### E. Proving the replacement actually happened: trust the build configuration, or make the running application read the value it swaps

A `fileReplacements` entry that silently fails to apply — a typo in the
configuration name, a build invoked with the wrong `--configuration` flag —
is not something a passing compile catches: TypeScript sees a valid
`AppEnvironment` object either way. The measured failure mode is sharper
than that: if no code path actually *reads* `environment.platform` (or any
other field that differs between the concrete files) at runtime, a
bundler's tree-shaking pass has no reason to keep the `environment` module
distinguishable in the two outputs, and both the `web` and `tauri` builds
can end up emitting byte-for-byte identical JavaScript — a broken or
misconfigured file replacement would then produce two working-looking
builds with no observable difference, rather than a visible failure.

- **Rejected: trust that the `fileReplacements` block in `angular.json` is
  correct and move on.** A configuration error here degrades silently.
  Nothing short of actually reading the platform flag at runtime forces the
  two bundles to differ in a way that can be checked.
- **Chosen: the application reads `environment.platform` from executed,
  unconditional startup code, not from a path only one build exercises.**
  `providePlatform()` (`frontend/src/app/core/platform/platform.providers.ts`)
  evaluates `environment.platform === 'tauri' ? TauriPlatformService :
  WebPlatformService` and `environment.platform === 'tauri' ? 'BODY' :
  'COOKIE'` as part of the application's DI provider setup, which every
  build's bootstrap runs — so the literal string `'tauri'` or `'web'` is
  live, reachable code in both bundles, not something a bundler is free to
  fold away. `frontend/src/app/app.spec.ts` additionally asserts the value
  directly ("runs under the web target, which is the environment Jest
  resolves": `expect(environment.platform).toBe('web')`), with a comment
  stating the point in the same terms: "this asserts the default rather
  than the replacement... It guards against the platform flag being dropped
  or hard-coded." The proof that a given `fileReplacements` configuration
  took effect is that its build's own bundle contains its own platform
  string — not a shared, tree-shaken-away constant both builds could
  plausibly agree on by accident.

### F. Single source of truth for the API base address (decided 2026-09-07)

Two independent HTTP clients inside the same packaged desktop application
talk to the same server: the Angular bundle running in the webview, and the
Rust download engine, compiled and configured separately. `config/README.md`
states the failure this is written to prevent, and it is a failure with no
built-in detector: "a packaged build whose environment variable was never
set had the interface talking to one host and the engine to another. Nothing
catches that — the content security policy governs the webview and says
nothing about the engine's own requests, and both halves look healthy in
isolation." A CSP failure is loud — a blocked request, a console error — and
would have caught the Angular side alone drifting from an intended host; it
has nothing to say about a Rust process making its own outbound HTTP calls
with `reqwest`, which is exactly why this particular disagreement had no
built-in alarm.

- **Rejected (the prior, implicit state): each side keeps its own
  configuration.** The frontend's `apiBaseUrl` came from the environment
  files; the desktop engine's base URL is read at runtime from a
  `BYTELORE_API_BASE_URL` environment variable, falling back to a hardcoded
  default (`desktop/src-tauri/src/http.rs`:
  `const DEFAULT_API_BASE_URL: &str = "http://localhost:8080/api/v1"` when
  the variable is unset). Two independently maintained values for the same
  fact is precisely the shape of bug `config/README.md` describes.
- **Chosen: `config/api-endpoints.json` at the repository root as the one
  place the address is written**, keyed by build profile
  (`production`, `development`), with two consumers reading the same file
  in two different ways: `frontend/src/environments/*.ts` imports it
  directly and resolves it at bundle time (`environment.ts` and
  `environment.tauri.ts` both read `endpoints.production`;
  `environment.development.ts` and `environment.tauri-development.ts` both
  read `endpoints.development`, which is `http://localhost:18080/api/v1` —
  port 18080, not the framework default 8080, because, per `config/README.md`,
  "another service already holds 8080 on the development machine"); and
  `desktop/src-tauri/build.rs`, per the same README, is intended to read the
  file at compile time and emit the selected value as a compile-time
  constant, chosen by `debug_assertions`.

## Decision

**A: abstract class, two full implementations, selected once at build time.**
**B: unsupported operations throw.** **C: the typecheck gate covers every
file under `src/` regardless of either build's own entry point.** **D: the
shared environment type lives in `environment.model.ts`, a file no build
configuration ever replaces.** **E: the running application reads
`environment.platform` from unconditional startup code, so a broken file
replacement produces two bundles that visibly disagree rather than two that
silently match.** **F: `config/api-endpoints.json` is the single written
source for the API base address, read by the frontend at bundle time and
intended to be read by the desktop engine's build script at compile time.**

Each decision is a direct, structural answer to a specific, previously
observed way the alternative fails silently: a component branching on a
platform flag, a method that lies by returning an empty success, a
typecheck gate whose scope tracks a CLI detail instead of this project's own
two-implementation shape, a type that imports its own replacement, a build
configuration whose effect nothing forces to be observable, and a value
duplicated across two languages and two build systems with no shared
source. The pattern across all six is the same: prefer a design where the
wrong outcome is structurally prevented or loudly visible over one where it
is merely expected not to happen.

## Consequences

### Positive

- A missing platform method is a compile error in whichever implementation
  omitted it, not a runtime `undefined` discovered by whoever happens to
  click the right button on the right platform first.
- `capabilities.canDownload` / `capabilities.hasLocalStore` plus "throw on
  unsupported" together mean a component either renders a control that
  works, or does not render it at all — there is no third state where a
  control is visible and silently does nothing.
- The typecheck gate holds both platform implementations to the same
  standard on every run, regardless of which build happens to be the one a
  developer is actively iterating on.
- `environment.model.ts` removes an entire class of self-import bugs that
  would otherwise be specific to exactly one of the four build
  configurations and invisible in the other three.
- A misconfigured or accidentally-omitted `fileReplacements` entry produces
  a build that fails a direct, existing assertion
  (`environment.platform`) rather than one that merely looks identical to a
  correct build until the wrong host is called in production.

### Negative

- Both platform implementations ship inside both bundles. The unused half
  is genuine dead weight for that build — real bytes the typecheck gate's
  own justification (§5.1) already names as the cost of not being able to
  omit it from compilation.
- `UnsupportedOnWebError` is a runtime exception path that a correct UI
  should never reach; if capability gating (`capabilities.canDownload`,
  `capabilities.hasLocalStore`) is ever done inconsistently, the failure
  surfaces as a thrown error at the moment of the call rather than earlier,
  at the point the control was rendered.
- `config/api-endpoints.json` is a fifth place a build depends on beyond
  the frontend's own `environments/` directory and `angular.json`, and it
  sits outside both the `frontend/` and `desktop/` subtrees — a location a
  contributor working in only one of those directories has to know to look
  at.

### Follow-up — decision F is not yet fully wired

`config/api-endpoints.json` and the four frontend environment files were
verified directly: all four (`environment.ts`, `environment.development.ts`,
`environment.tauri.ts`, `environment.tauri-development.ts`) already import
`../../../config/api-endpoints.json` and select `production` or
`development` by profile, exactly as decided. The desktop side is not yet
consistent with this decision as implemented:

- `desktop/src-tauri/build.rs` is presently `fn main() { tauri_build::build()
  }` — it does not read `config/api-endpoints.json`, and emits no
  compile-time constant from it.
- `desktop/src-tauri/src/http.rs` still resolves its base URL at *runtime*
  from the `BYTELORE_API_BASE_URL` environment variable
  (`configured_base_url()`), falling back to a hardcoded
  `http://localhost:8080/api/v1` when that variable is unset — a value that
  matches neither `config/api-endpoints.json`'s `"development"` entry
  (`http://localhost:18080/api/v1`, port 18080) nor its `"production"` entry.

So the specific failure `config/README.md` was written to prevent — the
interface and the engine silently disagreeing on the host because the
engine still depends on an environment variable nobody is required to set —
is not yet closed on the desktop side; only the frontend half of decision F
is implemented. Wiring `build.rs` to read `config/api-endpoints.json` and
emit the selected value as a compile-time constant, and updating
`http.rs` to use that constant as its default (or its only value) instead
of `BYTELORE_API_BASE_URL`'s hardcoded fallback, is outstanding work this
ADR records but does not perform.
