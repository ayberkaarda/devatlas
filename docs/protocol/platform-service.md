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

  // Content, read
  abstract listTracks(): Promise<TrackSummary[]>;
  abstract getTrack(trackId: string): Promise<TrackDetail>;
  abstract getLesson(lessonId: string): Promise<Lesson>;
  abstract getMindMap(trackId: string): Promise<MindMap>;

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

  // Blog — read over HTTP on both platforms, see section 6
  abstract listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>>;
  abstract getBlogPost(slug: string): Promise<BlogPost>;

  // Preferences
  abstract getPreferences(): Promise<Preferences>;
  abstract setPreferences(patch: Partial<Preferences>): Promise<void>;
}
```

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

---

## 5. What each implementation does

| Method group | `TauriPlatformService` | `WebPlatformService` |
|---|---|---|
| Content reads | `invoke('library_*')` — local store only, never falls back to HTTP | `HttpClient` against the read API |
| Library management | `invoke('download_*')`, events from `download://progress` | Throws `UnsupportedOnWebError`; never reached, the UI does not render the controls |
| Progress | `invoke('progress_mark')`, local write, always succeeds offline | `HttpClient`, requires a session |
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
