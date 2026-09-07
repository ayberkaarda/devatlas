# Platform Service Contract

**Status: proposed.** This document is a design gate. It freezes the abstraction
that lets one Angular codebase serve a desktop application backed by a local
SQLite replica and a public web site backed by a REST API.

Not covered here: HTTP endpoint shapes (`rest-api.md`), the download protocol
(`content-sync.md`), or the Rust command surface (`tauri-commands.md`). This
document consumes all three.

---

## 1. The rule the whole abstraction exists to enforce

> A component never knows which platform it is running on.

Not "rarely", not "except for downloads". Never. A component that asks receives the
same answer in both builds — that there is a `PlatformService`, and it has the data.

This is enforceable and enforced: `@tauri-apps/*` imports, `window.__TAURI__` and
bare `invoke(` calls appear in exactly one file, the Tauri implementation. A guard
flags them anywhere else, and the web build failing is the real proof.

The temptation this rule resists is `if (isTauri)`. Every occurrence of it is a
missing method on the abstraction. When behaviour genuinely differs, the difference
is expressed as data — a capability flag or a view-model field — and the component
branches on the data, not on the platform.

---

## 2. Shape of the contract

```typescript
export abstract class PlatformService {
  abstract readonly capabilities: PlatformCapabilities;

  // Content, read — addressed by slug, see below
  abstract listTracks(): Promise<TrackSummary[]>;
  abstract getTrack(trackSlug: string): Promise<TrackDetail>;
  abstract getLesson(lessonSlug: string): Promise<Lesson>;
  abstract getMindMap(trackSlug: string): Promise<MindMap>;

  // Library management — meaningful only where capabilities.canDownload
  abstract refreshLibrary(trackId?: string): Promise<DeltaSummary>;
  abstract enqueueDownload(scope: DownloadScope): Promise<BatchHandle>;
  abstract pauseDownloads(batchId?: string): Promise<void>;
  abstract resumeDownloads(batchId?: string): Promise<void>;
  abstract cancelDownload(batchId: string): Promise<void>;
  abstract retryDownload(entityId?: string): Promise<void>;
  abstract queueState(): Promise<QueueEntry[]>;
  abstract deleteLocal(scope: DownloadScope): Promise<void>;
  abstract downloadProgress(): Observable<DownloadProgress>;

  // Progress
  abstract markProgress(lessonId: string, completed: boolean): Promise<void>;
  abstract listProgress(): Promise<ProgressEntry[]>;   // desktop: progress_list

  // Progress sync — meaningful only where capabilities.hasLocalStore, see section 10
  abstract pendingProgress(): Promise<PendingProgress[]>;
  abstract applyProgressResults(results: readonly ProgressSyncResult[]): Promise<void>;
  abstract absorbProgress(entries: readonly ProgressEntry[]): Promise<void>;

  // Session persistence — what this device remembers across a restart, see section 10
  abstract loadSession(): Promise<RememberedSession | null>;
  abstract storeSession(session: RememberedSession): Promise<void>;
  abstract forgetSession(): Promise<void>;

  // Sync bookkeeping — durable, and not a preference, see section 10
  abstract getSyncState(): Promise<SyncBookkeeping>;
  abstract setSyncState(patch: Partial<SyncBookkeeping>): Promise<void>;

  // Blog — read over HTTP on both platforms, see section 6
  abstract listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>>;
  abstract getBlogPost(slug: string): Promise<BlogPost>;

  // Preferences
  abstract getPreferences(): Promise<Preferences>;
  abstract setPreferences(patch: Partial<Preferences>): Promise<void>;

  // Startup
  abstract revealApplication(): Promise<void>;
}
```

**Reads are addressed by slug, not by identifier.** The public read API exposes
`GET /lessons/{slug}` and no route that takes a UUID, so a signature taking an
identifier is implementable on the desktop and not on the web — and a method that
only one implementation can honour is not an abstraction. Slug works on both: the
Tauri implementation resolves a slug against its replica before calling the
identifier-keyed commands underneath. Downloads keep using identifiers, because
the manifest is keyed by them.

**`revealApplication()`** exists because the desktop window starts hidden until the
theme is applied, and something has to reveal it. A component calling
`window_show` directly is exactly the leak this abstraction prevents; by rule 1 of
section 8, a case that seems to need one is a missing method. On the web it is a
no-op — the document is already visible and the theme was applied by a pre-paint
script.

Every member is `abstract`, never optional. Adding a method is therefore a change
to three files at once — the abstraction and both implementations — and the
compiler refuses anything less. An optional method would let one implementation
quietly lack a feature and fail at runtime instead.

---

## 3. Capabilities

```typescript
export interface PlatformCapabilities {
  readonly canDownload: boolean;   // Tauri: true  · Web: false
  readonly hasLocalStore: boolean; // Tauri: true  · Web: false
}
```

Two flags, and the bar for a third is high: **a capability must have a different
value in the two implementations.** A flag that is `true` everywhere is dead
configuration, and worse, it invites a component to branch on something that never
varies — which is how platform knowledge leaks back in through a side door.

That is why there is no `blogAvailable`. The blog is present on both platforms, so
there is nothing to flag.

**Connectivity is not a capability.** Whether the network is reachable is runtime
state, it changes second to second, and it behaves identically in both builds. It
lives in a shared `ConnectivityService` that observes HTTP outcomes and browser
online/offline events. `navigator.onLine` alone is not sufficient — it reports the
presence of an interface, not of a route, and gives false positives inside a
WebView.

---

## 4. Availability is two axes, not one

The obvious model is a single enum. It is wrong, and the reason is worth spelling
out: "what is stored" and "what the queue is doing" are independent. A lesson can
be downloaded *and* have a newer version *and* be downloading that version right
now. One enum forces a choice between three true statements.

```typescript
export type Availability =
  | 'REMOTE'            // served live; never stored locally
  | 'NOT_DOWNLOADED'    // exists upstream, not held locally
  | 'DOWNLOADED'        // held locally at the manifest version
  | 'UPDATE_AVAILABLE'  // held locally, a newer version exists
  | 'WITHDRAWN'         // held locally, no longer in the manifest
  | 'LOCAL_AHEAD';      // local version exceeds the manifest — an anomaly

export interface Transfer {
  readonly state: 'QUEUED' | 'DOWNLOADING' | 'VERIFYING' | 'PAUSED' | 'FAILED';
  readonly bytesDone: number;
  readonly bytesTotal: number;
  readonly attempts: number;
  readonly lastError: string | null;
}

export interface ContentAvailability {
  readonly availability: Availability;
  readonly transfer: Transfer | null;  // web: always null
  readonly readable: boolean;          // availability !== 'NOT_DOWNLOADED'
}

// A track is a container, not a downloadable entity, so it needs its own set:
// "some of it" is a normal state for a track and has no meaning for a lesson.
export type TrackAvailability =
  | 'REMOTE'
  | 'NOT_DOWNLOADED'
  | 'PARTIALLY_DOWNLOADED'
  | 'DOWNLOADED'
  | 'UPDATE_AVAILABLE';
```

`TrackAvailability` is deliberately a separate type rather than a sixth member of
`Availability`. Adding `PARTIALLY_DOWNLOADED` to the entity enum would make it
representable for a lesson, where it means nothing — a lesson is one package and
is either held or not — and every exhaustive switch over a lesson's state would
then carry a branch that can never run.

`transfer.state` mirrors the download queue's state machine exactly, minus `DONE` —
completion is expressed by `availability`, not by a finished transfer that lingers.

**On the web, availability is `REMOTE`, not `DOWNLOADED`.** Claiming content is
downloaded when nothing is stored would be a lie that some component eventually
believes. `REMOTE` is also what the desktop reports for blog posts, so "served
live" is one concept with one value across both platforms rather than two
platform-specific special cases.

`LOCAL_AHEAD` exists because the download protocol requires the client to flag,
rather than silently repair, a local version that exceeds the manifest. Without a
view-model value for it, that requirement would have nowhere to surface.

Components use `readable` and `capabilities.canDownload`. They do not compare the
enum: a component that special-cases `REMOTE` has learned it is on the web.

**A track's mind map is a unit of content, not a boolean.** `TrackDetail`
carries

```typescript
export interface MindMapSummary {
  readonly id: string | null;          // web: null, nothing addresses it there
  readonly availability: ContentAvailability;
  readonly sizeBytes: number | null;
}
```

rather than `hasMindMap: boolean`. The boolean answers "is there one", which is
the question the navigation link asks and the only question it can answer; every
control that offers to *acquire* content needs the second axis as well. Reducing
the summary the store already returns to one bit is what leaves a track whose
lessons are all downloaded with a mind map it cannot be asked to fetch: the
container control aggregates over lessons, finds them complete, and hides the
only button. A container that includes a mind map counts it as one more unit, so
"4 of 5" is what a track with four downloaded lessons and a missing mind map
reports.

On the web `availability` is `REMOTE` with a `null` transfer, the same value the
platform reports for anything it serves live, and `id` is `null` because the read
API addresses a mind map by track slug and never exposes its identifier.

**Removal is offered at the level the user thinks in.** A queue row carries its
`trackId` and `trackTitle`, and the downloads screen groups by them so a track
can be removed in one confirmed action. Without it the screen can only delete
one lesson at a time, which leaves any entity that has no scope of its own --
a mind map is acquired with its track and can only be released with it --
downloadable but not removable. Content the interface can acquire and cannot
release is a one-way door, and there should be none.

**A downloaded package is multilingual, so `QueueEntry` reports a set.**
`locales` lists what a stored entity actually holds -- base locale first, then
alphabetically -- and is empty until something is stored. A screen showing "this
lesson is in Turkish" would be wrong about a package that also carries English
and French.

**An empty local read is a screen's job, not the service's.** Where reads are
served from a local replica, that replica starts out empty and no read fills it:
a track list with nothing in it and a track with no modules are both legitimate
answers meaning "never fetched". A screen that receives one and stops there
renders an application that can never acquire content, so a screen that can
download -- `capabilities.canDownload` -- follows an empty read with a single
`refreshLibrary()` for the list, or `refreshLibrary(trackId)` for the track, and
reads again. Once, not in a loop: the second empty answer is an answer.

A rejected refresh does not replace what the local read returned. Being offline
is an ordinary condition here, and a screen that trades a correct empty list for
an error state has made it look like a fault.

---

## 5. What each implementation does

| Method group | `TauriPlatformService` | `WebPlatformService` |
|---|---|---|
| Content reads | `invoke('library_*')` — local store only, never falls back to HTTP | `HttpClient` against the read API |
| Library management | `invoke('download_*')`, events from `download://progress` | Throws `UnsupportedOnWebError`; never reached, the UI does not render the controls |
| Progress | `invoke('progress_mark')`, local write, always succeeds offline | `HttpClient`, requires a session |
| A lesson's own completion | read from the local progress table alongside the lesson | already inline in the lesson response |
| Progress sync | `invoke('progress_pending' / 'progress_apply_results' / 'progress_absorb')` | Throws `UnsupportedOnWebError`; there is no local queue and no replica to absorb into, the write already went to the server |
| Session persistence | `invoke('session_store' / 'session_load' / 'session_clear')` | `localStorage`, and it holds no token — see section 10 |
| Sync bookkeeping | `invoke('settings_get' / 'settings_set')`, the two non-preference fields | `localStorage` |
| Blog | shared `BlogApiClient` over `HttpClient` | the same shared `BlogApiClient` |
| Preferences | `invoke('settings_get' / 'settings_set')` | `localStorage` |

The blog row is the interesting one: both implementations delegate to the same
client and neither adds anything. It stays on the abstraction anyway, because that
is what makes the decision reversible — if blog posts ever become downloadable,
only `TauriPlatformService` changes and no component notices.

Unsupported methods **throw rather than return empty**. A silent empty array is
indistinguishable from "there is genuinely nothing", and it would let a bug ship
looking like a legitimate empty state.

### 5.1 Selection happens at build time

```typescript
{
  provide: PlatformService,
  useClass: environment.platform === 'tauri' ? TauriPlatformService : WebPlatformService,
}
```

Both implementations are compiled in both builds; only one is reachable. This is
why the typecheck gate covers every file under `src/` rather than only what the
entry point reaches — in each build one implementation is dead code, and a
narrower check would let the unused half rot while the gate stayed green.

---

## 6. Blog: the one screen that needs a network

Blog posts are not replica content. They are not in any manifest, they are never
downloaded, and the local store has no table for them. On the desktop the blog
screen reads live over HTTP, and it is the only screen that does.

The read endpoint is anonymous, so this costs nothing in session terms: an expired
access token does not block it, and the download engine is unaffected.

### 6.1 The offline state

- **Trigger:** a transport-level failure — status 0, or a timeout of roughly eight
  seconds. `navigator.onLine === false` is a hint that may skip the wait; it never
  substitutes for attempting the request.
- **A `5xx` or a rate limit is a different state.** Showing "you are offline" when
  the server is failing is its own bug, and it teaches users to distrust the
  message. Server errors go through the normal error path, mapping `code` to a
  translation key.
- **What is shown:** an icon, a heading, an explanation that the rest of the
  application works offline and blog posts are loaded live, a retry button, and a
  link to the library. Every string is a translation key present in all four
  locales.
- **What is not done:** the blog navigation entry is never hidden while offline. A
  menu item that disappears is a question; an empty screen that explains itself is
  an answer. It gets a small offline indicator instead.
- **No infinite spinner.** A timeout is mandatory, because an indeterminate
  spinner is how an offline state looks like a hang.
- The last successful list is kept in memory for the session and shown with a
  "last loaded at HH:MM" band. It is not written to SQLite; that would be the first
  step toward making the blog replica content by accident.

---

## 7. Preferences: seeding, not conflict resolution

Theme and locale are stored on the device and also on the server profile. Framing
that as a conflict leads to synchronisation machinery that is not needed.

1. **The device is authoritative.** A change is written locally first and applied
   immediately. If online, it is pushed with `PATCH /auth/me`; if offline, a dirty
   flag is set and one push happens after the next successful refresh — several
   changes collapse into the final state.
2. **Reconnecting never pulls the server value over a local one.** A theme
   flipping under the user's eyes is worse than a preference that failed to travel.
3. **The server value is read exactly once:** at first sign-in on a device with no
   local preference — a new machine, a new browser, cleared storage. Before
   sign-in, the FOUC script uses `prefers-color-scheme` and whatever is stored.
4. **Two devices need not agree.** A laptop can be dark and a desktop light; in
   practice theme is a device preference. The server value is simply whoever
   pushed last, and it only ever seeds a fresh device.

The sync service therefore writes upward only, and reads downward only into an
empty store.

---

## 8. Rules

1. No `if (isTauri)` in a component. Ever. If one seems necessary, the abstraction
   is missing a method.
2. Tauri APIs appear only in `TauriPlatformService`.
3. Unsupported operations throw; they never return an empty success.
4. Controls for unavailable capabilities are not rendered at all, rather than
   rendered and failing on click.
5. Both implementations are unit tested. Component tests use a fake
   `PlatformService` and never touch either real one.
6. Every view model is identical in both implementations. A field that only one
   can populate is either derived, or given an honest value like `REMOTE`.

## 9. What stays outside the abstraction

The abstraction exists to hide one difference: whether the data lives in a local
replica or arrives over HTTP. Where no such difference exists, routing a call
through it adds two identical implementations and a third place to keep them in
step.

Session-bearing administration endpoints are the clear case. They are online by
definition — a draft awaiting review is not replicated to anyone's machine, and
an approval is not something a client can decide alone. Both platforms would
issue the same request with the same headers and parse the same response, so
the abstract method would be answered twice with one body of code.

These calls therefore go through a plain HTTP client injected directly into the
screens that need them, the same way the blog client already works underneath
`TauriPlatformService`. A component that calls one still learns nothing about
the platform: it sees a typed client, not a transport.

The boundary this draws is worth stating plainly, because it is easy to read the
rule in section 1 as "everything goes through the abstraction":

- A capability whose value would be identical in both implementations does not
  belong in `PlatformCapabilities` — it is dead configuration that invites a
  component to branch on something that never varies.
- A method whose two implementations would be the same code does not belong on
  `PlatformService` for the same reason.
- Role, on this reading, is not a platform fact. What a person may do comes from
  their session, and the answer is the same whichever build they are running.

---

## 10. Session persistence and sync bookkeeping

Added for phase 8. Everything above this section is unchanged; these are the
three method groups the sync layer needs and the abstraction did not yet have.

### 10.1 What a device remembers about a session

```typescript
export interface RememberedSession {
  readonly userId: string;
  readonly accessToken: string | null;
  readonly refreshToken: string | null;
  readonly accessTokenExpiresAt: string | null;
}
```

The three nullable fields are the difference between the platforms, expressed as
data rather than as a branch:

- **Desktop** signs in on the `BODY` channel, so the refresh token arrives as a
  value the application holds. It has to survive a restart, and `localStorage` in
  a WebView is not an appropriate home for it, so it goes to the local store
  through `session_store`. All four fields are populated.
- **Web** signs in on the `COOKIE` channel. The refresh token is already durable
  and already out of reach: the browser attaches it and no script can read it.
  There is nothing to store and nothing to hand back, so both token fields are
  `null` and `accessTokenExpiresAt` is `null` with them.

What the web implementation does store is `userId`, and only `userId`. That row
is not a credential — it is the answer to "has anyone ever signed in on this
browser", which is what decides whether a refresh is worth attempting at all.
Without it every first page load by an anonymous visitor spends a `POST
/auth/refresh` learning that there was never a session; with it, the request is
made only where it can succeed.

**The web implementation must never write a token into `localStorage`.** Storing
one there would undo the entire reason the cookie channel exists. A stored
session whose `accessToken` is non-null on the web is a defect, not a variation.

`forgetSession()` is deliberately not called `clearSession()`: `AuthSession`
already has a method by that name for its in-memory state, and the two are not
the same act — signing out clears both, but a rejected refresh clears memory
while a re-login is still possible from what the device remembers.

### 10.2 Progress sync

```typescript
export interface PendingProgress {
  readonly lessonId: string;
  readonly completedAt: string | null;
  readonly clientUpdatedAt: string;
}

export type ProgressSyncStatus = 'APPLIED' | 'STALE' | 'REJECTED';

export interface ProgressSyncResult {
  readonly lessonId: string;
  readonly status: ProgressSyncStatus;
  readonly code: string | null;
  readonly serverClientUpdatedAt: string | null;
  readonly completedAt?: string | null;
}
```

`pendingProgress()` returns the rows the server has not acknowledged;
`applyProgressResults()` writes back what it said, per row. Both map onto
`progress_pending` and `progress_apply_results`, whose per-status behaviour is
defined in the Rust command contract and is not restated here.

`absorbProgress()` is the pull direction's landing place, and it was added in
phase 8 because there was not one. The REST contract requires the client to
apply the same last-write-wins rule locally to what `GET /sync/progress`
returns, but every local write path available before this either stamped the
current time (`progress_mark`, which is what a person clicking "complete"
means) or only updated a row that already existed (`progress_apply_results`).
A pulled row for a lesson this installation has never completed had nowhere to
go, so a completion made on one device was invisible on another until that
device happened to complete the lesson itself. It takes `ProgressEntry` values
rather than a type of its own: a row pulled from the server is a progress
entry, and inventing a second identical shape for it would only invite the two
to drift.

The conflict rule it applies is stated once, in the Rust command contract, and
not restated here — the point of a single statement is that the two sides
cannot disagree.

`completedAt` is optional on a result and nullable inside it, and the two mean
different things: absent leaves the local completion state alone, `null` sets it
to "explicitly marked incomplete". A result that carried `undefined` and `null`
interchangeably would silently un-complete lessons on every `STALE` row, which is
why the field is written as `completedAt?: string | null` and never as
`completedAt: string | null | undefined`.

**Both methods throw `UnsupportedOnWebError` on the web**, following rule 3 of
section 8 and the precedent the library methods set. This is not an oversight to
be patched later with an empty array: on the web `markProgress` *is* the sync —
it posts a single-item batch and returns when the server has it. There is no
local queue, so "what is pending" has no honest answer, and returning `[]` would
be the silent empty success rule 3 exists to forbid. The service that drains the
queue therefore runs only where `capabilities.hasLocalStore`, branching on data
exactly as the download controls already do.

### 10.2.1 A lesson carries its own completion

`Lesson` has a `completedAt: string | null` — the moment this reader completed
it, or `null` for no recorded completion. A timestamp rather than a boolean,
because the value already exists as one everywhere else and reducing it here
would mean two shapes for one fact.

The two implementations source it differently, and that difference is the
abstraction earning its keep rather than a wart:

- **Web** already receives it. The read API returns `progress` inline with the
  lesson, and the field was being parsed and then dropped.
- **Desktop** reads it from the local progress table beside the lesson, because
  a lesson package is the same bytes for everybody while a completion belongs
  to one person — separate tables, separate commands. Reading it locally is
  also what makes the promise hold: completion has to be visible with no
  network and with an expired access token, which is exactly the condition the
  replica exists for.

`null` is returned for a signed-out reader too. The server draws the same blank
— it answers `null` both for an anonymous caller and for a signed-in one who
has not completed the lesson — so no client can distinguish the two, and a
screen that needs to must ask the session, not the lesson.

**A lesson whose progress could not be read still renders.** Failing to read
one row is not a reason to withhold the article; the completion falls back to
`null` and the reader keeps the thing they came for.

### 10.3 Sync bookkeeping

```typescript
export interface SyncBookkeeping {
  readonly preferencesDirtyAt: string | null;
  readonly lastSyncAt: string | null;
}
```

These are the two fields the Rust settings record carries that are not
preferences — the command contract says so in as many words — and they are kept
off `Preferences` for that reason. A preference is something a person chose;
these are things the sync layer knows and has nowhere else durable to write.

- `preferencesDirtyAt` is stamped when a preference changes with no connectivity
  and cleared once `PATCH /auth/me` succeeds. Several offline changes collapse
  into one push of the final state, which is the seeding model of section 7 seen
  from the write side.
- `lastSyncAt` is what the interface shows as "last synchronised". It is the last
  time a batch was accepted, not the last time one was attempted; a failed sync
  must not make the interface claim otherwise.

Both are honoured on both platforms. The web has offline preference changes too —
a laptop that loses its network does not stop having a theme — and two timestamps
are exactly the kind of small, replaceable state `localStorage` is right for.

### 10.4 What is still not on the abstraction

`POST /sync/progress` and `GET /sync/progress` are not platform methods. They are
one HTTP call each, identical in both builds, issued by the Angular layer that
owns the session — the same reasoning section 9 applies to the administration
endpoints. The platform supplies the rows and absorbs the answer; it does not
carry them over the wire.
