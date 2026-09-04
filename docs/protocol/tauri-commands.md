# Tauri Command Contract

**Status: proposed.** This document is a design gate. It freezes the boundary
between the Angular UI and the Rust core of the desktop client so that both sides
can be built against it without asking each other questions.

Scope: every `invoke` command the Rust side exposes, every event it emits, and the
shapes that cross that boundary.

Not covered here: HTTP endpoint shapes (`rest-api.md`), the manifest and download
protocol the engine implements (`content-sync.md`), or the Angular abstraction
that consumes these commands (`platform-service.md`). Where those documents own a
shape, this one refers to it and does not restate it.

---

## 1. What Rust owns, and what it does not

The division is not arbitrary — it follows from one decision, and every command
below is downstream of it.

**Rust owns:** the local SQLite store, the download queue, HTTP transfers of
content packages, SHA-256 verification, resume, retry, and delta comparison
against a manifest.

**Rust does not own the session.** It never sends an `Authorization` header, never
holds a refresh token, and never calls an authenticated endpoint. Manifest and
package endpoints are anonymous, so the engine needs no credentials at all.

The reason is worth stating because it is not obvious. Refresh tokens are
single-use and rotated: whoever presents one invalidates it and receives a
successor. If both the Angular layer and the Rust engine held the same token, the
second one to refresh would present a rotated token, trip reuse detection, and the
server would revoke the whole family — signing the user out because their own
application raced itself. Two token stores would have to be kept in lockstep
across a process boundary to avoid that, which is a synchronisation problem with
no good solution.

So authenticated traffic — login, profile, progress sync — is Angular's, over
`HttpClient`, exactly as it is on the web. Rust's contribution to sync is storage:
it hands over the rows that need sending and writes back what the server said.

---

## 2. Conventions

- **Command names** are `snake_case` verbs, grouped by prefix: `library_*`,
  `download_*`, `progress_*`, `settings_*`, `session_*`.
- **Payload naming is `camelCase`**, because Tauri's `invoke` serializes JavaScript
  arguments through serde with `#[serde(rename_all = "camelCase")]` on the Rust
  structs. This differs deliberately from the REST API's `snake_case`; the two
  surfaces are separate, and forcing one convention on both would mean fighting
  one framework or the other. The mapping happens in exactly one place per shape,
  inside the platform service implementation.
- **Identifiers** are UUIDv7 rendered as canonical lowercase hyphenated strings,
  matching both other protocol documents. Rust parses them into `uuid::Uuid` at
  the boundary and rejects malformed input rather than passing strings inward.
- **Timestamps** are ISO-8601 UTC with milliseconds and a `Z` suffix, matching the
  REST API exactly, so a value can move between the two surfaces untouched.
- **Every command returns `Result<T, CommandError>`.** There is no command that
  signals failure by returning an empty or default value.

### 2.1 Error shape

```json
{
  "code": "STORE_UNAVAILABLE",
  "message": "The local store could not be opened",
  "details": { "path": "C:\\Users\\…\\devatlas.db" }
}
```

`code` is a stable `SCREAMING_SNAKE` identifier the UI maps to a translation key,
mirroring the REST error contract so the frontend has one error-handling path
rather than two. `message` is English developer-facing text and is never shown to
a user. `details` is optional and free-form, for diagnostics only.

Codes originating from the server during a download are passed through unchanged,
so a `CONTENT_VERSION_SUPERSEDED` looks the same whether the UI learned it from an
HTTP call or from a download event.

| Code | Raised by | Meaning |
|---|---|---|
| `STORE_UNAVAILABLE` | any command | The SQLite store could not be opened or is corrupt |
| `ENTITY_NOT_IN_LIBRARY` | `library_*`, `download_delete` | The requested entity is not stored locally |
| `ALREADY_QUEUED` | `download_enqueue` | Already queued or in flight at the same version |
| `NOTHING_TO_DO` | `download_enqueue` | Every requested entity is already stored at the manifest version |
| `INVALID_ARGUMENT` | any command | Malformed identifier, unknown enum value, out-of-range value |
| `NETWORK_UNAVAILABLE` | `library_refresh`, `download_*` | The server could not be reached |
| `UNEXPECTED_RESPONSE` | download engine | A response arrived that the protocol does not describe |

---

## 3. Library commands

These read the local store. They never touch the network, so they work with an
expired session and no connectivity.

### `library_list_tracks() -> Vec<TrackSummary>`

Every track the user has any content from, plus tracks seen in the catalog.

```json
[
  {
    "trackId": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
    "slug": "angular-path",
    "title": "The Angular Path",
    "description": "Signals, routing and the modern component model.",
    "icon": "angular",
    "contentVersion": 47,
    "lessonCount": 32,
    "downloadedLessonCount": 12,
    "totalSizeBytes": 1893441,
    "downloadedSizeBytes": 623104,
    "availability": "PARTIALLY_DOWNLOADED",
    "updateAvailableCount": 3,
    "withdrawnCount": 0
  }
]
```

`title` and `description` are already resolved to the active locale by the store,
using the translations carried in the track manifest, with English as the
fallback. Resolution happens in Rust rather than in the UI so that both platform
implementations return an identically shaped object.

### `library_get_track(trackId) -> TrackDetail`

The track with its modules and lessons, each carrying its own availability. This
is what the library screen renders, and it is the reason the track manifest
carries a structural summary: a user picks a lesson by its title, and the entity
list alone would only offer identifiers.

### `library_get_lesson(lessonId) -> Lesson`

The full lesson: body, code examples, resolved locale. Fails with
`ENTITY_NOT_IN_LIBRARY` if it has not been downloaded — this command reads the
replica and never falls back to the network, so a caller always knows which source
answered.

### `library_get_mind_map(trackId) -> MindMap`

The mind map node tree. Node shape is the one the REST API defines; the package
carries it verbatim, and this command returns it unchanged.

### `library_refresh(trackId?) -> DeltaSummary`

Fetches manifests and compares them against the store. **Reads the network but
downloads nothing** — it only reports what a download would do.

```json
{
  "checkedTracks": 3,
  "updatedEntities": 3,
  "withdrawnEntities": 1,
  "newEntitiesAvailable": 12,
  "anomalies": [
    {
      "entityId": "018f3b21-9d17-7c55-b104-6e3a0f92d84c",
      "entityType": "LESSON",
      "localVersion": 14,
      "manifestVersion": 12
    }
  ]
}
```

`newEntitiesAvailable` counts entities in the manifest the user has never
downloaded. It is reported separately from `updatedEntities` because updating a
library must not silently grow it; the badge the UI shows reads "3 lessons
updated", not "15 changes".

`anomalies` lists entities whose local version is *ahead* of the manifest, which
should be impossible. Nothing is deleted or overwritten; the situation is
surfaced, because a restored backup or a server rollback is not a reason to
discard a user's content.

---

## 4. Download commands

### `download_enqueue(scope) -> BatchHandle`

One command for all three granularities, matching the protocol's single-engine
rule.

```json
{ "scope": { "kind": "MODULE", "id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90" } }
```

`kind` is `LESSON`, `MODULE` or `TRACK`. The command resolves the scope against the
current manifest, skips entities already stored at the manifest version, and
enqueues the rest.

```json
{
  "batchId": "018f3c55-7b1e-7d02-a933-0e84f2617b59",
  "queuedEntities": 12,
  "skippedEntities": 20,
  "totalBytes": 498221
}
```

Returns `NOTHING_TO_DO` when everything requested is already present, so the UI can
say so instead of showing a progress bar that completes instantly. `queuedEntities`
counts only what actually entered the queue — a batch's denominator must match what
the user will watch happen.

### `download_pause(batchId?)`, `download_resume(batchId?)`

Without an argument, act on the whole queue. Pausing sets `pauseReason: "USER"`;
the engine's own pause on a full disk uses `INSUFFICIENT_STORAGE` and is cleared by
the same resume command.

### `download_cancel(batchId) -> CancelSummary`

Removes queued and in-flight entries, deletes their partial files. Already
completed entities stay — cancelling a download does not undo what already
arrived.

### `download_retry(entityId?)`

Moves `FAILED` entries back to `QUEUED` with their attempt counter reset. Without
an argument, retries every failed entry.

### `download_queue_state() -> Vec<QueueEntry>`

The full queue, for a UI that has just started and missed the events so far.

```json
[
  {
    "entityId": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
    "entityType": "LESSON",
    "title": "Introduction to signals",
    "batchId": "018f3c55-7b1e-7d02-a933-0e84f2617b59",
    "state": "DOWNLOADING",
    "receivedBytes": 20480,
    "totalBytes": 41233,
    "attempt": 1,
    "pauseReason": null,
    "errorCode": null
  }
]
```

The UI reconciles by calling this on startup and then following events. Events
alone are not enough: they describe changes, and a screen opened mid-download has
no history to replay.

### `download_delete(scope) -> DeleteSummary`

Removes downloaded content from the store. **Progress data is never touched** — a
user who deletes a track to reclaim space and downloads it again later finds their
completed lessons still marked. This is the one place where a command deliberately
does less than its name suggests, and it is documented here so nobody later
"fixes" it into a cascade.

---

## 5. Progress commands

Progress is written locally, always, whether or not a session is valid. Angular
performs the HTTP sync; Rust supplies and absorbs the rows.

### `progress_mark(lessonId, completed) -> ProgressEntry`

Records completion locally with `clientUpdatedAt` set to now. Works offline and
with an expired token, because it touches nothing but SQLite.

`completed: false` is a real state, not an absence: it records "explicitly marked
incomplete" and must survive sync as such.

**Which user the row belongs to.** Progress rows are keyed by `(userId, lessonId)`
— the replica is shared by everyone who uses the installation, but progress is
not. The command does not take a user: it reads the active one from the stored
session, which is why `session_store` carries a `userId` alongside the tokens.

With no stored session, the row is written under a nil UUID rather than rejected.
Refusing would make marking a lesson complete depend on being signed in, and the
platform contract promises progress writes work offline with an expired session;
a signed-out state is the same condition seen from further away. Those rows are
adopted by the next sign-in on the same installation.

### `progress_list() -> Vec<ProgressEntry>`

Every progress row for the active user, for rendering completion state in the UI.
Separate from `progress_pending`, which returns only what still needs sending.

### `progress_pending() -> Vec<PendingProgress>`

Rows not yet acknowledged by the server. Angular posts these to
`POST /api/v1/sync/progress`.

### `progress_apply_results(results) -> void`

Writes back what the server said, per row: `APPLIED`, `STALE` or `REJECTED`.

- `APPLIED` clears the pending flag. If the server clamped `clientUpdatedAt`, the
  clamped value is stored, so a device with a skewed clock converges instead of
  arguing forever.
- `STALE` means the server holds a newer record; the local row takes the server's
  value.
- `REJECTED` marks the row `ORPHANED` and **stops resending it**. Without this a
  desktop client that accumulated writes against a since-deleted lesson would
  carry the same rejected row in every future batch, forever.

Nothing here deletes a progress row.

---

## 6. Settings and session storage

### `settings_get() -> AppSettings`, `settings_set(patch) -> AppSettings`

```json
{
  "locale": "tr",
  "theme": "DARK",
  "preferencesDirtyAt": "2026-09-04T08:41:02.310Z",
  "lastSyncAt": "2026-09-03T21:14:55.002Z"
}
```

Theme and locale are stored locally so the window can be themed before it is
shown, and so a preference set offline is not lost.

The last two fields are sync bookkeeping rather than user preferences, and they
are here because the Angular layer owns the sync but has nowhere durable of its
own to record it. `preferencesDirtyAt` is set when a preference changes while
offline and cleared once `PATCH /auth/me` succeeds; several changes made offline
collapse into one push of the final state. `lastSyncAt` is what the UI shows as
"last synchronised".

Both are `null` when there is nothing outstanding. `settings_set` takes a partial
patch, so clearing one field does not require restating the others.

### `session_store(session)`, `session_load() -> StoredSession?`, `session_clear()`

```json
{
  "userId": "018f39ff-4c21-7a08-9b55-2d7e6a1c40b3",
  "accessToken": "eyJhbGciOiJIUzI1NiIs…",
  "refreshToken": "0c7f…",
  "accessTokenExpiresAt": "2026-09-04T09:27:33.000Z"
}
```

Angular owns the session but needs somewhere durable to keep it across restarts;
`localStorage` in a WebView is not an appropriate home for a refresh token. These
commands are a typed store and nothing more: **Rust never parses, validates,
refreshes or transmits the tokens.** It stores them, and reads `userId` — the one
field it does use, to know whose progress rows to write.

### `window_show()`

Reveals the main window. It starts with `visible: false` so that nothing is
painted before the theme is applied; the UI calls this once the theme is on the
document. The two halves are a pair — removing `visible: false` makes this command
pointless, and removing the call leaves a window that never appears.

---

## 7. Events

### `download://progress`

Payload as defined in the content sync protocol, in `camelCase` per section 2.
Throttled to at most one event per 250 ms per entity, plus one unthrottled event
on every state change, so a fast connection cannot flood the WebView.

Unlike a package document, this payload **may contain `null`** for `errorCode` and
`pauseReason`. The canonical-JSON rule that forbids `null` applies to hashed
package documents only; events are not hashed and omitting keys would force the UI
to distinguish "absent" from "null" for no benefit.

### `download://batch-complete`

Emitted once per batch when no entry remains in a non-terminal state, carrying
counts of completed and failed entities. The UI needs a single edge to react to;
deriving completion by watching per-entity events means reimplementing the queue's
bookkeeping in TypeScript.

### `library://updated`

Emitted when the replica changes in a way a rendered screen would need to reflect:
an entity finished downloading, was deleted, or was marked withdrawn.

---

## 8. Rules that hold across every command

1. **No command performs authenticated HTTP.** If one ever needs to, it takes an
   access token as a parameter; the refresh token stays on the Angular side.
2. **Reads never fall back to the network.** `library_*` answers from the store or
   fails. A caller always knows which source answered, and an offline screen
   cannot silently become an online one.
3. **The store path comes from the runtime's application-data directory**, never
   from the working directory or a path relative to the executable.
4. **Verification, replica write and the `DONE` transition are one SQLite
   transaction.** A crash between verifying and inserting must not produce an
   entity marked complete with no rows behind it — that state would satisfy every
   future delta comparison while the lesson does not exist locally.
5. **Deleting content never deletes progress.**

---

## 9. Deliberate omissions

**There is no blog command surface, and the local store gains no blog tables.**
Blog posts are not replica content: the Angular layer reads them over HTTP on both
platforms, and on the desktop that is the one screen that needs a connection.

This is a choice, not an oversight. A blog post's value is its currency — the
automated pipeline exists to publish "this framework released a version today" —
so a three-week-old offline snapshot would carry almost none of it, while adding a
third entity type would reopen the manifest shape, the entity enumeration and the
determinism tests all at once. The blog read endpoint is anonymous, so serving it
over HTTP costs the engine nothing and keeps the session where it already lives.

The abstraction still declares blog methods, so if this is ever revisited only the
Tauri implementation changes and no component does.

