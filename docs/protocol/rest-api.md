# ByteLore REST API Contract

**Status:** frozen contract, v1.1 — every question left open in v1.0 is now decided (§8)
**Base path:** `/api/v1`
**Implements:** Spring Boot (Java 21) + PostgreSQL 16 server; consumed by the Angular web build and by the Tauri desktop client.

This document is the single source of truth for the HTTP surface listed below. Server, desktop and web work are implemented against it in parallel, so it is written to be read without follow-up questions: every request field is marked required/optional, every response field is marked nullable or not, and every endpoint lists the error codes it can produce.

Changing a shape in this document is a breaking change for three codebases at once. Error codes in particular are public API surface: **once a code ships it is never renamed**, only deprecated alongside a successor.

---

## 1. Purpose and scope

### 1.1 Covered here

| Area | Summary |
|---|---|
| Authentication | Registration, login, JWT access tokens, opaque refresh tokens with rotation, password change, current-user profile |
| Authorization | Role matrix for `ADMIN`, `EDITOR`, `USER` and anonymous callers |
| Content read API | Published tracks, modules, lessons, code examples, mind maps and blog posts, as consumed by the web build |
| Admin/editor CRUD | Authoring and lifecycle of tracks, modules, lessons, code examples, mind maps and blog posts |
| Translations | Reading and writing `ContentTranslation` rows; English fallback behaviour |
| Blog pipeline surface | Review queue, source updates, whitelist source administration, pipeline audit log |
| Progress sync | Batch upload and pull of `UserProgress` with last-write-wins reconciliation |
| Error contract | The complete `{code, message}` catalogue with HTTP status mapping |

### 1.2 Not covered here

The **manifest and content-package download protocol** is specified separately in `docs/protocol/content-sync.md` — hereafter "the content sync protocol". That document owns:

- the manifest endpoints and the content-package endpoints,
- package JSON shapes, `sha256` computation and determinism rules,
- `ETag`/conditional-request behaviour on package responses,
- delta ("update my library") comparison and the client download queue.

Where an endpoint in this document borders that protocol — for example, a lesson update that bumps `content_version` — this document states *that* the boundary is crossed and stops. It never restates a shape owned by the content sync protocol.

**Those endpoints are anonymous.** `GET /api/v1/manifest/**` and `GET /api/v1/content/**` require no credentials; §3.7 explains why, and what follows from it for the desktop client. Their rate limits are in §3.6 because throttling is an authentication-layer concern shared by both documents.

Also out of scope: local SQLite schema, Tauri `invoke` signatures, and desktop event payloads. Those live in their own protocol documents.

---

## 2. Conventions

### 2.1 Transport, media types, encoding

- All endpoints live under `/api/v1`. There is no unversioned alias.
- Request and response bodies are `application/json;charset=UTF-8`. A request with a body and a different `Content-Type` is rejected with `415 UNSUPPORTED_MEDIA_TYPE`.
- Bodies are UTF-8. Markdown fields are stored with **LF line endings**: the server normalizes `\r\n` and `\r` to `\n` and strips a leading BOM before persisting. Clients may send CRLF; they must not assume it survives a round trip. This normalization is load-bearing for the content hashes defined in the content sync protocol.
- `GET` responses on this surface carry no `ETag`. Conditional requests are a feature of the content-package endpoints only.
- If a request carries an `X-Request-Id` header, the server echoes it on the response and includes it in server logs. It never appears in the response body.

### 2.2 JSON naming

All JSON property names — request and response, including error bodies — are **`snake_case`**. Java DTOs use `camelCase` fields with a global `PropertyNamingStrategies.SNAKE_CASE` on the object mapper; no per-field `@JsonProperty` overrides. This matches the persisted column names and removes an entire class of "which casing did that field use" bugs across the three clients.

Enum values are `SCREAMING_SNAKE_CASE` strings (`PUBLISHED`, `PENDING_REVIEW`, `AUTO`). Locale codes are the lowercase two-letter forms `en`, `tr`, `fr`, `de`.

### 2.3 Identifiers

Every entity exposes a **UUID (version 7)** primary key, rendered as the canonical lowercase hyphenated string:

```
"id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70"
```

UUIDv7 is time-ordered, so it indexes like a sequence while staying non-enumerable in a public API and stable across environments (seed data, dev, CI, production all agree). Slugs are the human-facing identifier and are used in public read paths; UUIDs are used in admin paths and in every cross-entity reference.

> The content sync protocol references entities by `entity_id`. That identifier is the same UUID defined here — the two documents must not diverge on identifier type.

### 2.4 Date and time

Every timestamp this API emits matches exactly this pattern — **normative, not illustrative**:

```
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'      →  "updated_at": "2026-09-04T09:12:33.482Z"
```

Rules, in force for every response field and every serialized timestamp:

- UTC only. The `Z` suffix is literal; a numeric offset is never emitted.
- **Exactly three fractional digits, always present.** A whole second serializes as `.000`, not as an omitted fraction. Java's default `Instant` serializer drops trailing zeros and emits nanoseconds; both behaviours are wrong here and must be overridden with an explicit formatter.
- Sub-millisecond precision is truncated (not rounded) on write, so a value read back is byte-identical to what comparisons will use.

This is normative because timestamps appear inside content payloads whose bytes are hashed: a formatter that emits `.48Z` in one code path and `.480Z` in another produces two hashes for one logical value. The content sync protocol aligns to this pattern rather than defining its own.

On input the server also accepts an explicit numeric offset (`+03:00`) and converts to UTC; a naive local timestamp with no offset is rejected with `VALIDATION_FAILED`. Java side: `Instant` fields, `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS` disabled, explicit `DateTimeFormatter` as above.

`client_updated_at` values in progress sync are client clocks and are treated as untrusted — see §5.8.1.

### 2.5 Pagination

Collection endpoints use **offset pagination** with a fixed envelope. Offset paging is chosen over cursors because the admin lists need a total count for their UI and the data sets are small (hundreds of rows, not millions).

Query parameters:

| Param | Type | Default | Constraint |
|---|---|---|---|
| `page` | int | `0` | `>= 0` |
| `size` | int | `20` | `1..100`; larger values → `400 PAGE_SIZE_EXCEEDED` |
| `sort` | string | endpoint-specific | `field,asc\|desc`; may repeat; unknown field → `400 INVALID_SORT_FIELD` |

Envelope:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total_elements": 137,
  "total_pages": 7
}
```

`items` is never `null`; an empty page returns `[]` with `200`. Each endpoint documents its allowed `sort` fields — sorting is whitelisted per endpoint, never passed through to JPA verbatim.

### 2.6 Idempotency

There is no `Idempotency-Key` header on this surface. Idempotency is achieved structurally:

- `POST /sync/progress` is naturally idempotent: replaying the same batch produces the same state, because the reconciliation rule is last-write-wins on `client_updated_at`.
- `PUT` endpoints (translation upsert, mind map upsert, reorder) are idempotent by definition.
- `POST /auth/logout` returns `204` whether or not the presented refresh token was still active.
- Mutating **content** operations use **optimistic concurrency**: every mutable content resource exposes a `version` integer (JPA `@Version`), and `PATCH`/`PUT` bodies must echo the `version` they read. A stale value returns `409 VERSION_CONFLICT`. This applies to tracks, modules, lessons, code examples, mind maps, translations, blog posts and whitelist sources — resources where a lost update destroys someone's authoring work.
- **User preferences are exempt.** `PATCH /auth/me` carries no `version` and resolves concurrently with last-write-wins. A theme or locale preference has one writer in practice, and a `409` on a theme toggle is a worse outcome than the write it prevents.
- Blog lifecycle transitions additionally carry `expected_status`, so two reviewers acting at once cannot both "approve" — the loser gets `409 INVALID_STATE_TRANSITION`.

> **`version` is not `content_version`.** `version` is the row's optimistic-lock counter and has no meaning to clients beyond conflict detection. `content_version` is the content revision counter used by the content sync protocol and increases only when the *content* of an entity changes (see §5.4.1).

### 2.7 Locale negotiation and translation fallback

English (`en`) is the canonical locale: every entity's own columns hold the English text. Other locales live in `ContentTranslation` rows and are optional.

Resolution order for a read request:

1. `?locale=` query parameter, if present. Value must be one of `en|tr|fr|de`, otherwise `400 UNSUPPORTED_LOCALE`.
2. `Accept-Language` header, parsed with q-values, first supported match wins (`tr-TR` matches `tr`).
3. `en`.

Every translatable object in a response carries its own resolution result, because a single payload can mix translated and untranslated entities (a translated track containing an untranslated lesson):

```json
{
  "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "slug": "signals-and-reactivity",
  "title": "Sinyaller ve Reaktivite",
  "locale": "tr",
  "requested_locale": "tr",
  "is_fallback": false
}
```

When no translation exists for the requested locale, the English text is returned with `locale: "en"`, `requested_locale: "tr"`, `is_fallback: true`. Clients render the "not yet translated" badge from `is_fallback` — they never compare locale strings themselves.

The response carries `Content-Language` set to the locale used for the **root** entity. `Vary: Accept-Language` is set on every content read endpoint.

Locale negotiation applies to *content*. Interface strings are a client concern and never travel over this API.

### 2.8 Markdown safety at the write boundary

Markdown bodies and mind-map labels are checked **on write, before persistence**, against a server-side allow-list. The check is a **predicate, not a rewrite**: what you `POST`, normalized (§3.1), is byte for byte what you `GET` back, or the write is refused with `422 UNSAFE_HTML`.

This matters because the stored form is markdown *source*, and it is the form that is hashed and shipped to the desktop client. Running an HTML sanitizer over markdown source and storing its output rewrites the text: the OWASP serializer entity-encodes nine printable ASCII characters unconditionally (`"`, `&`, `'`, `+`, `<`, `=`, `>`, `@`, `` ` ``), so a code fence, an apostrophe, an e-mail address or a `>` opening a blockquote could not survive a save. The server therefore asks the sanitizer only *whether it would discard anything*, and stores the author's own bytes.

The check has three parts, and a body must clear all three:

1. **No raw markup declarations.** Any raw-HTML node containing `<!` or `<?` — an HTML comment, a CDATA section, a doctype, a processing instruction — is refused. A content body has no use for them, and the sanitizer's comment lexer does not agree with the HTML5 parser about where a comment ends, so a construct like `<!-->` can hide markup from the allow-list entirely.
2. **Link and image destinations.** The destination is resolved the way a browser resolves an attribute value — character references decoded whether or not they end in a semicolon, ignored characters stripped — and its scheme must be `http`, `https`, `mailto`, or absent (a relative reference). `[x](javascript:…)` and `[x](&#106avascript:…)` are refused alike.
3. **The element and attribute allow-list**, applied to the HTML the body renders to.

**The allow-list.** Block structure (`p`, `div`, `h1`–`h6`, `ul`, `ol`, `li`, `blockquote`, `pre`, `hr`), inline formatting (`b`, `i`, `em`, `strong`, `code`, `br`, `span`, `sub`, `sup`, …), links (`a[href]`, restricted to the schemes above), images (`img[src|alt|title]`), tables, and `details`/`summary`. Attributes outside this list — including presentational ones such as `class` on anything but a `code` element carrying a `language-*` value, `style`, and `data-*` — are not admitted.

Consequences the clients must know:

- A write echoes exactly what was sent, after normalization. An editor never has to reload to discover what was stored.
- A refusal names the offending elements and attributes in `message`, which is the information needed to fix a paste. Content pasted from a rich-text editor or a web page will often hit this.
- Because the boundary refuses rather than strips, a body can no longer become empty by being sanitized; blankness is ordinary field validation.
- **Automatically sourced (`AUTO`) blog drafts are the one exception.** Feed content genuinely is HTML, so the pipeline's excerpt is passed *through* the sanitizer rather than checked by it, and the resulting entity encoding is visible in the draft body. Such a draft is reviewed by a human before it can be published.
- Client-side sanitization at render time (DOMPurify) remains a second layer, and is load-bearing: the server's CommonMark implementation and the client's are two implementations of one specification.

### 2.9 CORS and cookies

The web build is served from a different origin than the API. CORS is configured from an explicit allow-list of origins (environment-provided, never `*` when credentials are involved), allowing `GET, POST, PATCH, PUT, DELETE, OPTIONS`, headers `Authorization, Content-Type, Accept-Language, X-Request-Id`, and exposing `Content-Language, Retry-After, X-Request-Id`. `allowCredentials` is `true`, which is what makes the wildcard origin impossible.

**The desktop client is also subject to CORS.** The same Angular application runs inside the Tauri webview and issues its HTTP calls through the browser stack, so its requests carry an `Origin` header and are preflighted like any other. The allow-list must therefore include the webview origins as well:

| Origin | Where it comes from |
|---|---|
| `https://app.bytelore.invalid` (example) | Deployed web build; environment-provided |
| `http://localhost:4200` | Local Angular dev server; only in the `dev` profile, environment-provided |
| `http://tauri.localhost` | Tauri webview on Windows |
| `tauri://localhost` | Tauri webview on Linux and macOS |

Omitting the two webview origins produces a failure mode that is easy to misdiagnose: every desktop API call fails at the preflight with no response body to inspect, while the identical code works in the browser. The exact origin strings are platform-dependent — verify them against the running webview rather than trusting this table.

Note that the anonymous manifest and content endpoints (§3.7) are unauthenticated and are read by Rust, not by the webview, so they need no credentialed CORS treatment.

### 2.10 Deletion policy

Content deletes are conservative, because deleting content that clients have already downloaded is a destructive act whose client-side consequences are the content sync protocol's problem:

- A container (track, module, lesson) with children cannot be deleted → `409 PARENT_NOT_EMPTY`. Delete children first; there is no `?cascade=true`.
- A track with `published = true` cannot be deleted → `409 PUBLISHED_DELETE_BLOCKED`. Unpublish first.
- A `PUBLISHED` blog post cannot be deleted → `409 PUBLISHED_DELETE_BLOCKED`. Unpublish it first (§5.5.2).
- Deletes return `204 No Content` with an empty body. Deleting an already-absent resource returns `404`, not `204` (the caller's model of the world is wrong and should be corrected).

**Lessons are soft-deleted.** `DELETE /admin/lessons/{id}` sets `lessons.deleted_at` and does not remove the row:

- A soft-deleted lesson disappears from every read endpoint in this document and from the manifests described by the content sync protocol. `404 LESSON_NOT_FOUND` is the correct answer for it everywhere.
- **`UserProgress` rows are never deleted**, by any operation on this API. A completion is a fact about a person's history, and an editorial decision must not erase it. If the lesson is later restored, the progress is still there and still correct.
- The slug uniqueness index is therefore **partial**: `CREATE UNIQUE INDEX … ON lessons (slug) WHERE deleted_at IS NULL`. A slug freed by a deletion is reusable; the deleted row keeps its old slug without colliding.
- Every read query carries `deleted_at IS NULL`. This is the cost of the decision and the one place it can go wrong: a single query that forgets the predicate leaks deleted content. Enforce it with a repository-level default rather than by hand at each call site.
- There is no restore endpoint in v1. Undeleting is a database operation performed by an operator.
- Tracks, modules, code examples, mind maps and blog posts are hard-deleted; the soft-delete rule exists specifically because of the `UserProgress` foreign key, which no other entity has.

### 2.11 Payload limits

| Limit | Value | Violation |
|---|---|---|
| Max request body | 1 MiB | `413 PAYLOAD_TOO_LARGE` |
| Max markdown body (lesson, blog post) | 200 000 characters | `400 VALIDATION_FAILED` |
| Max code example source | 20 000 characters | `400 VALIDATION_FAILED` |
| Max progress sync batch | 500 items | `413 SYNC_BATCH_TOO_LARGE` |
| Max mind map nodes / depth | 500 nodes / depth 8 | `422 MIND_MAP_INVALID` |

### 2.12 OpenAPI

springdoc generates the OpenAPI document at `/v3/api-docs` with Swagger UI at `/swagger-ui.html`, **enabled only under the `dev` profile**. Neither path exists in any other profile. The generated document is a convenience view of this contract, not a replacement for it: where they disagree, this document is correct and the annotations are the bug.

---

## 3. Authentication

### 3.1 Token shapes

**Access token — JWT, sent on every authenticated request.**

```
Authorization: Bearer <jwt>
```

Claims:

| Claim | Example | Notes |
|---|---|---|
| `sub` | `018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70` | User UUID |
| `role` | `USER` | One of `ADMIN`, `EDITOR`, `USER` |
| `typ` | `access` | Guards against a refresh token being presented as an access token |
| `iat` / `exp` | epoch seconds | |
| `jti` | UUID | Log correlation |

Signature: HMAC-SHA256 (`HS256`) with a secret supplied through the environment. Only this server issues and verifies these tokens, so an asymmetric key pair would add operational cost without a consumer. The token carries no email address and no personally identifying data.

Clock skew tolerance on verification: **60 seconds**.

**Refresh token — opaque, not a JWT.**

A 256-bit random value, base64url-encoded, stored server-side only as a SHA-256 digest, alongside `user_id`, `family_id`, `issued_at`, `expires_at`, `rotated_at`, `revoked_at`, `successor_id` (the token this one rotated into, nullable) and a client-supplied `device_label`. Opaque-and-stored is chosen over a self-contained JWT refresh token precisely because it must be revocable and reuse-detectable — a stolen JWT refresh token cannot be invalidated before its expiry.

The server holds **only the digest**. It cannot reproduce the plaintext of any token it has issued, including a token it issued one second ago. §3.4 depends on this fact.

### 3.2 Lifetimes (recommended values)

| Token | Lifetime | Rationale |
|---|---|---|
| Access | **15 minutes** | Short enough that a leaked token has small value; long enough that a normal reading session makes few refresh calls. |
| Refresh | **60 days**, and **every rotation issues a full fresh 60 days** | The desktop client may be offline for weeks; a shorter window would force a re-login on a user who did nothing wrong. Rotation on every use keeps the *effective* exposure of any single value short. |

**The refresh lifetime is per token, not per family.** Each rotation mints a successor with `expires_at = now + 60 days`; the successor does **not** inherit the remaining lifetime of its predecessor. Without this rule an active daily user would be signed out every 60 days for no reason — the very outcome a 60-day window was chosen to avoid — while an idle user would be signed out on exactly the same schedule. Regular use must extend a session; only absence must end it.

A family therefore lives as long as it is used, with no upper bound by default. If an absolute session cap is later required (a compliance rule, or a policy that no session may outlive a password change by more than N days), it is added as a separate `family_max_age` checked against the family's `created_at` at refresh time, not by shortening the per-token TTL. That knob is deliberately absent in v1: it has no requirement behind it, and a wrong value silently signs everyone out.

These are configuration values (`bytelore.auth.access-ttl`, `bytelore.auth.refresh-ttl`), not compile-time constants.

### 3.3 Refresh rotation

`POST /auth/refresh` with a valid, unexpired, unrotated refresh token:

1. Marks the presented token `rotated_at = now`.
2. Issues a new access token and a **new** refresh token in the same `family_id`, with a fresh full lifetime (§3.2), and records the successor on the presented row (`successor_id`).
3. Returns both, through the delivery channel the request arrived on (§3.8).

A refresh token is therefore single-use. The `family_id` links the whole chain descended from one login, which is what makes reuse detection possible.

### 3.4 Reuse of a rotated refresh token

Presenting a token that has already been rotated is the signature of a stolen token being replayed (the legitimate client and the attacker cannot both hold the newest value). Response:

- The **entire family** is revoked immediately: every non-revoked token with that `family_id`, including the current valid one.
- The response is `401` with code `REFRESH_TOKEN_REUSED`.
- The event is logged with `user_id`, `family_id`, request IP and `X-Request-Id`.
- The client must treat this as a hard sign-out (see §3.5).

**Grace window for lost responses.** A rotation whose response never reaches the client (dropped connection, laptop suspended mid-request) would otherwise look identical to theft and lock out an innocent user. The grace rule:

> If the presented token was rotated **less than 30 seconds ago** and its successor has **never been used** (the successor is itself unrotated and unrevoked), the server **revokes that unused successor, issues a brand-new pair in the same family, and returns it**. The family survives.

The obvious formulation — "return the successor again" — is not implementable and must not be written into any code review checklist as the expected behaviour: the server stores only a SHA-256 digest of each token (§3.1) and cannot reproduce the plaintext of the successor it issued 5 seconds ago. Re-minting is the only way to answer the request at all.

Conditions and consequences:

- The unused successor is revoked in the same transaction, so the grace path never leaves two live tokens in the family. If the lost response did reach a second consumer after all, that consumer's token is now dead and its next call trips reuse detection — which is the correct outcome, not a bug.
- If the successor **has** been used, this is not a lost response: someone else already advanced the chain. Full reuse handling applies — the family is revoked.
- Outside the 30-second window, reuse is reuse, regardless of successor state.
- Each grace re-mint is logged with `family_id` and the count of grace events on that family. Repeated grace events on one family are a signal worth alerting on: a healthy client produces at most a handful over a session's lifetime.

This is a deliberate, bounded weakening of reuse detection — a 30-second window in which a stolen token can be exchanged once — accepted so that flaky networks do not sign users out.

### 3.5 Expired access token, offline desktop client

This is a hard requirement of the product: the desktop application is fully usable while its access token is expired and no network is available.

The design that makes it possible:

0. **Downloading is not an authenticated operation.** Manifest and content-package requests carry no credentials at all (§3.7), so an expired — or entirely absent — access token cannot block a download, a library update, or a resume after restart. This removes the largest category of offline failure before the remaining rules are even needed.
1. **Replica reads never touch the server on desktop.** After the first sign-in, the desktop client reads replicated content from its local SQLite copy. Nothing in the local read path consults token validity — an expired access token is not an application state, it is a property of one HTTP client. The replica covers lessons, code examples, mind maps and progress; blog posts are not replicated — they are not in any manifest, are never downloaded, and have no local table — so the desktop client reads them live from the public blog endpoints (§5.2.5, §5.2.6), which are anonymous, meaning an expired access token does not block those reads either.
2. **Progress writes are local first.** Lesson completion is written to the local `user_progress` table with a `client_updated_at` stamp, unconditionally, with no token check. The server is never in the write path of a local completion.
3. **The token is only needed to talk to the server.** The client attaches the access token when — and only when — it makes an API call. If it is expired, the call is not attempted with a stale token; the client refreshes first.
4. **Reconciliation on reconnect.** When connectivity returns, the Angular layer calls `POST /auth/refresh` with its stored refresh token. On success it silently obtains a new pair and flushes the queued progress batch to `POST /sync/progress`. The user sees nothing. The Rust layer is not involved and holds no tokens (§3.7).
5. **Never sign out on an access-token error.** Clients must distinguish the codes:

   | Code | Client behaviour |
   |---|---|
   | `ACCESS_TOKEN_EXPIRED` | Refresh once, retry the original request once. Never surface to the user. Never clear local data. |
   | `ACCESS_TOKEN_INVALID` | Same as expired: refresh once, retry once. If it recurs, treat as the refresh-failure row below. |
   | `AUTH_REQUIRED` | The request carried no token — a client bug, or a call made before sign-in. |
   | `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED` | Enter **local-only mode**: the app keeps working against local content and keeps recording progress locally; a non-blocking "sign in to sync" affordance is shown. Local content and local progress are **not** deleted. |

6. **Re-authentication preserves pending progress.** Local progress rows are keyed by `user_id`. After a re-login as the same user, the pending batch is uploaded unchanged, with its original `client_updated_at` values — which is exactly why the reconciliation rule is timestamp-based rather than arrival-order-based. If a *different* user signs in on the same machine, the previous user's rows stay keyed to the previous `user_id` and are neither uploaded nor destroyed.
7. **Network failure is not an auth failure.** A connection error, DNS failure or timeout must never be interpreted as a sign-out. Only the explicit refresh-failure codes above end a session.

Consequence for the server: `POST /auth/refresh` must accept a refresh token that is weeks older than the access token it accompanies, and must not require a valid access token to be presented alongside it. The refresh endpoint takes the refresh token in the body and **ignores** the `Authorization` header entirely.

### 3.6 Rate limiting

Enforced in Postgres with a fixed-window counter table (no external cache — the platform runs on Postgres alone). Limits are per window, and every rejection is `429 RATE_LIMITED` with a `Retry-After` header in seconds.

| Endpoint | Key | Limit |
|---|---|---|
| `POST /auth/login` | email + client IP | 10 / 15 min |
| `POST /auth/register` | client IP | 5 / hour |
| `POST /auth/refresh` | user | 60 / hour |
| `POST /auth/logout` | client IP | 60 / hour |
| `POST /auth/me/password` | user | 5 / hour |
| `POST /sync/progress` | user | 120 / hour |
| `POST /admin/whitelist-sources/{id}/fetch` | whitelist source | 6 / hour |
| `GET /manifest/**` | client IP | 60 / minute |
| `GET /content/**` | client IP | 600 / minute |

The last two rows are the anonymous endpoints owned by the content sync protocol. They are throttled by **client IP**, because there is no principal to throttle by (§3.7). The ratio between them is deliberate: a client reads a handful of manifests and then downloads many packages, and the download engine runs up to three concurrent transfers with resume, so a limit that comfortably covers manifest polling would strangle a legitimate library download. Both limits are configuration values, and a `429` on these endpoints must be handled by the download engine as a retryable, backoff-worthy condition — never as a package failure.

Login responses are deliberately uniform: an unknown email and a wrong password both return `401 INVALID_CREDENTIALS` with the same message and comparable timing.

### 3.7 Session ownership: the desktop client never authenticates over HTTP

**The manifest and content-package endpoints are anonymous.** `GET /api/v1/manifest/**` and `GET /api/v1/content/**` accept no credentials and require none.

Why, in the order the reasoning actually runs:

1. **Authentication there would protect nothing.** §4 already grants anonymous callers `GET /tracks`, `GET /lessons/{slug}` and `GET /blog/posts/{slug}`, and those responses contain the complete `body_markdown` and every code example. The same bytes that a package delivers are already readable without a token. A gate on the package endpoints would be a gate on a door standing beside an open wall.
2. **Two processes cannot share one single-use token.** Refresh tokens rotate and are single-use (§3.3). If both the Angular layer and the Rust download engine held the session, each refresh would invalidate the other's copy, and every concurrent refresh would look exactly like the theft pattern in §3.4. The failure would be intermittent, timing-dependent, and would present to users as random sign-outs during downloads — the worst class of bug this contract can create.
3. **Therefore the session lives in exactly one place: the Angular layer.** The Rust side issues plain anonymous `GET` requests and stores no token, no user id and no credential of any kind.

Consequences that implementations must honour:

- The download engine sends **no** `Authorization` header. If one appears there, the design has been misunderstood.
- Progress sync, profile reads and every admin call are made by Angular, which owns the token pair.
- Published content is public by definition. Any future requirement to restrict content to signed-in users is not a change to the package endpoints alone — it is a change to the public read API in §5.2 first, and only then to the packages. Bolting a token onto the download path while §5.2 stays open would be theatre.
- Abuse control on the anonymous endpoints is the IP-keyed rate limit above, not authentication.

### 3.8 Token delivery: the response follows the request channel

Refresh tokens reach the two clients differently — the web build receives a cookie, the desktop client receives a value in the response body — and the mechanism that selects between them is the one place this could go wrong.

**The choice is made once, at sign-in.** `POST /auth/login` and `POST /auth/register` accept a `token_delivery` field:

| Value | Behaviour | Used by |
|---|---|---|
| `COOKIE` (default) | The refresh token is set as `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` and is **omitted from the response body** | Web build |
| `BODY` | The refresh token is returned in the response body as `refresh_token`; no cookie is set | Desktop client |

**`POST /auth/refresh` and `POST /auth/logout` do not accept `token_delivery`.** They answer through the channel the request arrived on:

- Refresh token presented in the cookie → the new token is set in a cookie, and the response body's `refresh_token` is `null`.
- Refresh token presented in the request body → the new token is returned in the body, and no cookie is set.
- Both present → `400 AMBIGUOUS_TOKEN_DELIVERY`. Guessing here would be guessing about a credential.

This rule exists because the alternative is an exfiltration primitive. If `refresh` honoured a client-chosen `token_delivery`, script injected into the web build could call `refresh` with `{"token_delivery": "BODY"}`; the browser would attach the `HttpOnly` cookie automatically, and the server would obligingly hand the token's plaintext back to JavaScript — converting an `HttpOnly` cookie into a readable one on request, and turning a transient XSS into a 60-day session theft. Channel-follows-request closes that path with no cost to either client.

Deployment constraint that comes with `SameSite=Strict`: the web build and the API must be **same-site** — `app.example.com` and `api.example.com` share the registrable domain `example.com` and satisfy it; `bytelore-app.invalid` calling `bytelore-api.invalid` does not, and the cookie will simply not be sent. This constrains DNS and must be settled before the first deployment, not discovered from an empty cookie jar afterwards. Cross-site hosting would force `SameSite=None`, which reopens CSRF considerations and would require a separate decision.

The cookie path is `/api/v1/auth` rather than `/api/v1/auth/refresh` so that `POST /auth/logout` receives it too; logout must be able to revoke the token it is being asked to revoke.

---

## 4. Authorization matrix

Roles: `ADMIN` ⊃ `EDITOR` ⊃ `USER` in privilege, but the checks are explicit per endpoint, not inherited by ordinal comparison.

Legend: ● allowed · ○ allowed with restriction (see note) · — denied (`403 FORBIDDEN_ROLE`, or `401 AUTH_REQUIRED` when anonymous)

| Endpoint group | Anonymous | `USER` | `EDITOR` | `ADMIN` |
|---|:--:|:--:|:--:|:--:|
| `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | ● ⁰ | ● | ● | ● |
| `GET /auth/me`, `PATCH /auth/me`, `POST /auth/me/password` | — | ● | ● | ● |
| `GET` tracks / lessons / mind maps (published only) | ● | ● | ● | ● |
| `GET` blog posts (`PUBLISHED` only) | ● | ● | ● | ● |
| `GET /manifest/**`, `GET /content/**` (content sync protocol) | ● ⁴ | ● | ● | ● |
| `POST /sync/progress`, `GET /sync/progress` | — | ● | ● | ● |
| `/admin/tracks`, `/admin/modules`, `/admin/lessons`, `/admin/code-examples`, `/admin/**/mindmap` (all verbs) | — | — | ● | ● |
| `/admin/translations` (all verbs) | — | — | ● | ● |
| `POST /admin/blog/posts`, `PATCH`, `DELETE`, `POST …/submit` | — | — | ○ ¹ | ● |
| `POST /admin/blog/posts/{id}/publish` (manual posts) | — | — | ○ ² | ● |
| `POST /admin/blog/posts/{id}/approve`, `…/reject`, `…/unpublish` | — | — | — | ● |
| `GET /admin/review-queue`, `GET /admin/review-queue/{id}` | — | — | ● ³ | ● |
| `GET /admin/blog/posts/{id}/audit-log`, `GET /admin/source-updates/{id}` | — | — | ● | ● |
| `/admin/whitelist-sources` (all verbs, including `…/fetch`) | — | — | — | ● |

⁰ These four are anonymous by necessity, not by omission. `logout` in particular takes no access token: the credential it acts on is the refresh token, and a user whose access token expired while offline must still be able to sign out. Requiring a valid access token there would make signing out the one action an expired session cannot perform.
¹ An `EDITOR` may create and edit posts with `source = MANUAL`. Posts with `source = AUTO` are pipeline-owned: an editor may read them and submit them for review, but may not edit their body, and **no role may edit the source link of an `AUTO` post** (§5.5.1, rule 7).
² Manual posts only. A post with `source = AUTO` can never reach `PUBLISHED` through `publish`; it must go through `approve`, which is `ADMIN`-only. See §5.5.1.
³ Read-only access to the review queue, so an editor can prepare a recommendation. The approve/reject decision stays with `ADMIN`.
⁴ Anonymous for every caller, including signed-in ones — a token presented there is ignored. See §3.7 for the reasoning; these endpoints' shapes belong to the content sync protocol, and only their authorization posture is stated here.

**Resolving an ambiguity in the product brief:** it describes the review screen as an admin screen *and* describes a manual authoring flow available to admin and editor that ends in publication. Both are honoured by splitting the paths: `MANUAL` posts are publishable by an `EDITOR` without review; `AUTO` posts always require an `ADMIN` approval step. Automatically sourced content therefore has exactly one route to publication, and it passes through a human with the highest privilege level.

Every non-public endpoint requires a valid access token. Missing/malformed credentials → `401`; valid credentials with insufficient role → `403`. The server never returns `404` to hide the existence of an admin resource from an authenticated non-admin; role failures are reported honestly as `403`.

---

## 5. Endpoints

**58 endpoints:** 7 auth (§5.1), 6 public content read (§5.2), 45 admin, pipeline and sync endpoints (§5.4–§5.8, numbered 1–45 in the tables below for cross-reference). The manifest and content-package endpoints are not counted here; they belong to the content sync protocol. Unless stated otherwise, `2xx` bodies are JSON objects and `4xx`/`5xx` bodies follow §6.

Every endpoint may additionally return `500 INTERNAL_ERROR`, `503 SERVICE_UNAVAILABLE`, `405 METHOD_NOT_ALLOWED` and `415 UNSUPPORTED_MEDIA_TYPE`; these are not repeated in the per-endpoint error lists.

### 5.1 Auth

#### 5.1.1 `POST /api/v1/auth/register`

Public. Creates a `USER`. Role assignment is never accepted from the client.

Request:

```json
{
  "email": "eda.demir@example.com",
  "password": "kayseri-uzun-parola-2026",
  "locale": "tr",
  "theme": "SYSTEM",
  "device_label": "eda-thinkpad",
  "token_delivery": "BODY"
}
```

| Field | Required | Notes |
|---|---|---|
| `email` | yes | Case-insensitive, stored lowercased |
| `password` | yes | 12–128 characters |
| `locale` | no | `en\|tr\|fr\|de`, default `en` |
| `theme` | no | `LIGHT\|DARK\|SYSTEM`, default `SYSTEM` |
| `device_label` | no | ≤ 64 chars; labels the refresh token so a user can tell devices apart |
| `token_delivery` | no | `COOKIE` (default) or `BODY` — see §3.8. Accepted **only** here and on login |

`201 Created` — same body as login (§5.1.2). Registration signs the user in; there is no separate confirmation step.

Errors: `VALIDATION_FAILED`, `EMAIL_ALREADY_REGISTERED` (409), `RATE_LIMITED`.

#### 5.1.2 `POST /api/v1/auth/login`

Public.

```json
{
  "email": "eda.demir@example.com",
  "password": "kayseri-uzun-parola-2026",
  "device_label": "eda-thinkpad",
  "token_delivery": "BODY"
}
```

`200 OK` (this example is the `BODY` delivery used by the desktop client):

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMThmM2IyMS02YzRhLTdiMGUtOWQzMS00YTJmOGM1ZTFiNzAiLCJyb2xlIjoiVVNFUiIsInR5cCI6ImFjY2VzcyIsImlhdCI6MTc4ODUxOTk1MywiZXhwIjoxNzg4NTIwODUzLCJqdGkiOiIwMThmM2IyMi1hMWIwLTdjMzMtOGVmMS1kNGMyYTkwZTVmMTIifQ.7pQm3d2xN0S4qA1cVbHl9pJk3rFhWm2sTgYc8dXeZ0k",
  "access_token_expires_at": "2026-09-04T09:27:33.000Z",
  "refresh_token": "0m3Yb1S8vQ4pKcTfRZ2xHn7LdEwA9jUgVrMoNbXqIyc",
  "refresh_token_expires_at": "2026-11-03T09:12:33.000Z",
  "token_type": "Bearer",
  "user": {
    "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
    "email": "eda.demir@example.com",
    "role": "USER",
    "locale": "tr",
    "theme": "SYSTEM",
    "created_at": "2026-08-11T14:02:59.117Z"
  }
}
```

With `token_delivery: "COOKIE"` the response is identical except that `refresh_token` is `null` and the value arrives in a `Set-Cookie` header (§3.8). `refresh_token_expires_at` is still returned in both modes, so a client can schedule ahead without reading the token.

Errors: `VALIDATION_FAILED`, `INVALID_CREDENTIALS` (401), `ACCOUNT_DISABLED` (403), `RATE_LIMITED`.

#### 5.1.3 `POST /api/v1/auth/refresh`

Public — the `Authorization` header is ignored (§3.5). The refresh token is read from the request body **or** from the cookie, and the response answers on the same channel (§3.8). `token_delivery` is not accepted here.

```json
{ "refresh_token": "0m3Yb1S8vQ4pKcTfRZ2xHn7LdEwA9jUgVrMoNbXqIyc" }
```

`200 OK` — identical body to login, with a **new** `refresh_token` (or a new cookie and `refresh_token: null`). The old value is dead the moment this returns; the 30-second grace window of §3.4 does not resurrect it, it mints another new pair.

Errors: `VALIDATION_FAILED`, `AMBIGUOUS_TOKEN_DELIVERY` (400 — token supplied in both body and cookie), `REFRESH_TOKEN_INVALID` (401), `REFRESH_TOKEN_EXPIRED` (401), `REFRESH_TOKEN_REUSED` (401), `ACCOUNT_DISABLED` (403), `RATE_LIMITED`.

#### 5.1.4 `POST /api/v1/auth/logout`

**Public — no access token required.** The credential logout acts on is the refresh token itself, presented in the body or the cookie. Requiring a valid access token would make sign-out impossible for exactly the user who most wants it: one whose access token expired while the machine was offline.

Revokes the presented refresh token and its whole family. `?all_devices=true` revokes every family belonging to the token's user.

```json
{ "refresh_token": "0m3Yb1S8vQ4pKcTfRZ2xHn7LdEwA9jUgVrMoNbXqIyc" }
```

`204 No Content`, including when the token was already revoked, expired or entirely unknown — logout must not become an oracle for token validity. When the token arrived by cookie, the response also clears that cookie (`Max-Age=0`, same path).

Because it is unauthenticated and idempotent, logout takes no other action and reveals nothing: it never returns a body, never distinguishes outcomes, and is rate-limited by IP alongside the other anonymous auth endpoints.

Errors: `VALIDATION_FAILED` (no token supplied on either channel), `AMBIGUOUS_TOKEN_DELIVERY` (400), `RATE_LIMITED`.

#### 5.1.5 `GET /api/v1/auth/me`

Authenticated. Returns the `user` object shown in §5.1.2, plus:

```json
{
  "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "email": "eda.demir@example.com",
  "role": "USER",
  "locale": "tr",
  "theme": "SYSTEM",
  "created_at": "2026-08-11T14:02:59.117Z",
  "updated_at": "2026-09-01T06:44:10.220Z"
}
```

There is no `version` field on the user resource — preferences use last-write-wins (§2.6).

Errors: `AUTH_REQUIRED`, `ACCESS_TOKEN_EXPIRED`.

#### 5.1.6 `PATCH /api/v1/auth/me`

Authenticated. Updates preferences only. `email` and `role` are not patchable here.

```json
{ "locale": "de", "theme": "DARK" }
```

All fields optional; an empty object is a no-op returning the current state. **No `version` and no optimistic locking**: a preference has one writer in practice, the loss window is a theme toggle, and a `409` on a theme toggle is a worse user experience than the lost update it prevents. Last write wins.

`200 OK` with the §5.1.5 body.

Errors: `AUTH_REQUIRED`, `VALIDATION_FAILED`, `UNSUPPORTED_LOCALE`.

#### 5.1.7 `POST /api/v1/auth/me/password`

Authenticated. Changes the caller's password. There is **no password reset flow** in v1 — see §8.1 for the operator procedure that replaces it.

```json
{
  "current_password": "kayseri-uzun-parola-2026",
  "new_password": "yeni-daha-uzun-parola-2027"
}
```

Both fields required; `new_password` follows the rules in §7.1 and must differ from `current_password`.

`204 No Content`.

On success the server revokes **every refresh-token family belonging to the user except the one the caller is currently using**, so a password change signs out every other device without signing out the device that performed it. The caller's access token remains valid until it expires; its short lifetime bounds the exposure.

**A wrong `current_password` returns `422 CURRENT_PASSWORD_INCORRECT`, not `401`.** This is deliberate and load-bearing. Clients run a global interceptor that treats `401` as "the access token needs refreshing" and retries (§3.5); a `401` here would send that interceptor into a refresh-and-retry loop against a request that will never succeed, burning the refresh rate limit and eventually presenting the user with a spurious sign-out instead of "your current password is wrong". The failure is a fact about the request body, not about the session, and the status code must say so.

Errors: `AUTH_REQUIRED`, `ACCESS_TOKEN_EXPIRED`, `VALIDATION_FAILED`, `CURRENT_PASSWORD_INCORRECT` (422), `RATE_LIMITED`.

### 5.2 Content read (public)

These endpoints expose **published** content only. `published = false` tracks, and everything beneath them, are invisible here regardless of the caller's role — an authoring preview uses the admin endpoints (§5.4).

#### 5.2.1 `GET /api/v1/tracks`

Query: `page`, `size`, `sort` (allowed: `order`, `title`, `updated_at`; default `order,asc`).

`200 OK`:

```json
{
  "items": [
    {
      "id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
      "slug": "angular-path",
      "title": "Angular Yolu",
      "description": "Standalone bileşenlerden sinyallere kadar modern Angular.",
      "icon": "angular",
      "order": 1,
      "locale": "tr",
      "requested_locale": "tr",
      "is_fallback": false,
      "module_count": 6,
      "lesson_count": 28,
      "content_version": 14,
      "updated_at": "2026-08-29T11:20:04.771Z"
    },
    {
      "id": "018f3a01-9d44-7c02-b6a8-1e77f0c2bb31",
      "slug": "spring-boot-path",
      "title": "Spring Boot Path",
      "description": "Building production services with Spring Boot and PostgreSQL.",
      "icon": "spring",
      "order": 2,
      "locale": "en",
      "requested_locale": "tr",
      "is_fallback": true,
      "module_count": 4,
      "lesson_count": 19,
      "content_version": 9,
      "updated_at": "2026-08-14T08:03:41.006Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 2,
  "total_pages": 1
}
```

`content_version` here is the track's aggregate content revision; its computation and its role in downloads belong to the content sync protocol.

Errors: `PAGE_SIZE_EXCEEDED`, `INVALID_SORT_FIELD`, `UNSUPPORTED_LOCALE`.

#### 5.2.2 `GET /api/v1/tracks/{slug}`

Returns the track with its modules and lesson summaries. Lesson bodies are **not** included — a track with 28 lessons would otherwise be a multi-megabyte response.

`200 OK`:

```json
{
  "id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
  "slug": "angular-path",
  "title": "Angular Yolu",
  "description": "Standalone bileşenlerden sinyallere kadar modern Angular.",
  "icon": "angular",
  "order": 1,
  "locale": "tr",
  "requested_locale": "tr",
  "is_fallback": false,
  "content_version": 14,
  "has_mind_map": true,
  "updated_at": "2026-08-29T11:20:04.771Z",
  "modules": [
    {
      "id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
      "title": "Reaktivite Temelleri",
      "order": 1,
      "estimated_minutes": 95,
      "locale": "tr",
      "requested_locale": "tr",
      "is_fallback": false,
      "lessons": [
        {
          "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
          "slug": "signals-and-reactivity",
          "title": "Sinyaller ve Reaktivite",
          "difficulty": "BEGINNER",
          "estimated_minutes": 25,
          "order": 1,
          "content_version": 4,
          "locale": "tr",
          "requested_locale": "tr",
          "is_fallback": false,
          "updated_at": "2026-08-29T11:20:04.771Z"
        },
        {
          "id": "018f3b21-88f2-7d15-b3ac-9e10d4f2c001",
          "slug": "computed-and-effects",
          "title": "Computed and Effects",
          "difficulty": "INTERMEDIATE",
          "estimated_minutes": 35,
          "order": 2,
          "content_version": 2,
          "locale": "en",
          "requested_locale": "tr",
          "is_fallback": true,
          "updated_at": "2026-08-22T19:41:12.330Z"
        }
      ]
    }
  ]
}
```

`difficulty` ∈ `BEGINNER | INTERMEDIATE | ADVANCED`.

Errors: `TRACK_NOT_FOUND` (404), `UNSUPPORTED_LOCALE`.

#### 5.2.3 `GET /api/v1/lessons/{slug}`

Full lesson: sanitized markdown plus ordered code examples.

**Lesson slugs are globally unique**, not unique-within-track. This keeps lesson deep links (`/lessons/signals-and-reactivity`) stable when a lesson is moved between modules or tracks during content reorganisation, which is the common editorial operation.

`200 OK`:

```json
{
  "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "slug": "signals-and-reactivity",
  "title": "Sinyaller ve Reaktivite",
  "body_markdown": "## Sinyal nedir?\n\nBir sinyal, okundugunda kendisini okuyan hesaplamaya bagimlilik kaydeden bir deger sarmalayicisidir.\n\n```ts\nconst count = signal(0);\n```\n",
  "difficulty": "BEGINNER",
  "estimated_minutes": 25,
  "order": 1,
  "content_version": 4,
  "locale": "tr",
  "requested_locale": "tr",
  "is_fallback": false,
  "updated_at": "2026-08-29T11:20:04.771Z",
  "module": {
    "id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
    "title": "Reaktivite Temelleri",
    "order": 1
  },
  "track": {
    "id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
    "slug": "angular-path",
    "title": "Angular Yolu"
  },
  "code_examples": [
    {
      "id": "018f3b22-1c09-7aa8-9f34-2b6e0d51a4f7",
      "language": "typescript",
      "code": "const count = signal(0);\nconst double = computed(() => count() * 2);\ncount.set(21);\nconsole.log(double()); // 42",
      "caption": "Sinyal ve turetilmis deger",
      "order": 1
    }
  ],
  "progress": {
    "completed_at": "2026-09-02T20:11:07.400Z"
  }
}
```

`progress` is `null` for anonymous callers and for an authenticated user who has not completed the lesson (`completed_at: null`). `code_examples` is never `null`; it may be `[]`.

Code example `code` is **not** markdown and is **not** HTML-sanitized — it is opaque source text stored and returned verbatim (with LF normalization). Clients must render it inside a syntax highlighter that escapes text, never as HTML.

Errors: `LESSON_NOT_FOUND` (404), `UNSUPPORTED_LOCALE`.

#### 5.2.4 `GET /api/v1/tracks/{slug}/mindmap`

`200 OK`:

```json
{
  "id": "018f3a05-77b1-7e2c-9a40-3d81cc60f5a2",
  "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
  "content_version": 3,
  "updated_at": "2026-08-27T15:31:09.512Z",
  "locale": "en",
  "requested_locale": "tr",
  "is_fallback": true,
  "root": {
    "id": "root",
    "label": "Angular Path",
    "lesson_id": null,
    "children": [
      {
        "id": "reactivity",
        "label": "Reactivity",
        "lesson_id": null,
        "children": [
          {
            "id": "signals",
            "label": "Signals",
            "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
            "children": []
          }
        ]
      }
    ]
  }
}
```

`id` is the mind map's own UUID, distinct from `track_id`. It is the identifier the content sync protocol uses as the mind map's `entity_id`; a client that keyed its local copy by `track_id` would be unable to address the entity the manifest names.

**Mind map labels are not translated in v1.** The response therefore always reports `locale: "en"` and, for any requested locale other than `en`, `is_fallback: true` — the same shape as an untranslated lesson, so clients need no special case and the existing "not yet translated" badge appears without extra work. `MIND_MAP` is not a member of the translatable entity set (§5.6); attempting to store a translation for it returns `400 ENTITY_TYPE_UNSUPPORTED`.

The reason is that a mind map is a graph of short labels whose value is the structure, not the prose; translating it means translating every node of every map for four locales and keeping node identity aligned across all of them as the graph is edited. That is a content-operations commitment, not a schema change, and it is not being made in v1. Adding it later is additive: `MIND_MAP` joins the translatable set, `is_fallback` starts returning `false` where a translation exists, and no client contract changes.

Node shape: `id` (string, unique within the map, `^[a-z0-9][a-z0-9-]{0,63}$`), `label` (1–120 chars, sanitized as plain text), `lesson_id` (UUID or `null`), `children` (array, never `null`). Depth ≤ 8, total nodes ≤ 500. A `lesson_id` must reference a lesson belonging to the same track.

The content sync protocol transports this `root` tree verbatim inside the mind-map package; its shape is defined here and must not be redefined there.

Errors: `TRACK_NOT_FOUND` (404), `MIND_MAP_NOT_FOUND` (404), `UNSUPPORTED_LOCALE`.

#### 5.2.5 `GET /api/v1/blog/posts`

Only `PUBLISHED` posts. Query: `page`, `size`, `sort` (allowed: `published_at`, `title`; default `published_at,desc`), `source` (`MANUAL|AUTO`, optional filter).

`200 OK`:

```json
{
  "items": [
    {
      "id": "018f4c10-77a3-7e51-8b02-3f9c1d6e2200",
      "slug": "spring-boot-4-1-1-released",
      "title": "Spring Boot 4.1.1 Released",
      "excerpt": "Spring Boot 4.1.1 ships 43 fixes and upgrades to Spring Framework 7.1.2.",
      "source": "AUTO",
      "source_url": "https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now",
      "published_at": "2026-08-20T13:05:00.000Z",
      "locale": "en",
      "requested_locale": "tr",
      "is_fallback": true
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 41,
  "total_pages": 3
}
```

`excerpt` is derived server-side from the first 280 characters of the sanitized markdown with formatting stripped; it is not an authored field.

Errors: `PAGE_SIZE_EXCEEDED`, `INVALID_SORT_FIELD`, `INVALID_PARAMETER`, `UNSUPPORTED_LOCALE`.

#### 5.2.6 `GET /api/v1/blog/posts/{slug}`

`200 OK`:

```json
{
  "id": "018f4c10-77a3-7e51-8b02-3f9c1d6e2200",
  "slug": "spring-boot-4-1-1-released",
  "title": "Spring Boot 4.1.1 Released",
  "body_markdown": "Spring Boot **4.1.1** is available.\n\n## Highlights\n\n- 43 bug fixes\n- Upgrade to Spring Framework 7.1.2\n\n[Release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.1)\n",
  "source": "AUTO",
  "source_url": "https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now",
  "published_at": "2026-08-20T13:05:00.000Z",
  "updated_at": "2026-08-20T13:05:00.000Z",
  "locale": "en",
  "requested_locale": "tr",
  "is_fallback": true
}
```

`source_url` is non-null whenever `source` is `AUTO` (enforced at the pipeline boundary, §5.5.1); it is nullable for `MANUAL` posts.

A `DRAFT`, `PENDING_REVIEW` or `REJECTED` post returns `404 BLOG_POST_NOT_FOUND` here — unpublished content does not exist on the public surface.

Errors: `BLOG_POST_NOT_FOUND` (404), `UNSUPPORTED_LOCALE`.

> **Full-text search is not part of v1.** `GET /api/v1/search` does not exist on this surface. A sketch is kept in Appendix A as reserved, non-frozen material; nothing in v1 may be built against it.

### 5.3 Progress (read side of a lesson)

There is no separate "mark complete" endpoint. The web client marks a lesson complete by posting a single-item batch to `POST /sync/progress` (§5.7). One write path, one conflict rule, on both clients.

### 5.4 Admin content CRUD

All paths below are prefixed `/api/v1/admin`. `EDITOR` and `ADMIN` only. Admin reads return unpublished content and never apply translation fallback: they return the **canonical English columns** plus, where requested, the raw translation rows (§5.6). Authoring must see what is actually stored, not a fallback.

#### 5.4.1 Content versioning at the write boundary

##### The governing rule

> **Every field that appears in a content package is on the bump list.** If a write can change a byte that the content sync protocol will ship and hash, that write increments `content_version` and recomputes the hash. There is no such thing as a cosmetic edit to a packaged field.

The rule is stated before the table because the table is the fallible part. A field can be added to a package months after this document is written; the invariant is what keeps the two in step, and it is the invariant — not the enumeration — that implementations are held to.

The failure this prevents is silent and total. Suppose a lesson's `slug` is edited and `content_version` is left alone: the package a client already holds contains the old slug, the manifest still advertises the version the client already has, so the client never re-downloads — and every subsequent integrity check compares its stored bytes against a hash computed from the new slug and fails. The user sees repeated verification failures on content that will never repair itself, on every installation, forever. The whole defect is one missing row in this table.

##### Test requirement

This rule is verified, not merely documented. A test enumerates the fields serialized into each package type — by reflecting over the package DTO rather than by a hand-written list, so a newly added field cannot be forgotten — and asserts that mutating each one through the admin API produces a `content_version` increment and a changed hash. A field present in a package with no corresponding bump fails the build. Without this test the rule is a comment, and comments do not survive refactoring.

##### The table

| Change | Bumps `content_version` |
|---|---|
| Lesson `slug` | **yes (lesson)** — the slug is carried in the package |
| Lesson `body_markdown`, `title`, `difficulty`, `estimated_minutes` | yes (lesson) |
| Lesson `order`, module membership (move) | yes (lesson) |
| Lesson soft delete / restore | yes (owning track) |
| Any code example create/update/delete/reorder | yes (owning lesson) |
| Code example `language`, `code`, `caption`, `order` | yes (owning lesson) |
| Mind map `root` tree create/update/delete | yes (mind map, and owning track) |
| Translation row create/update/delete | yes (translated entity, and owning track for `TRACK`/`MODULE` rows) |
| Track `slug`, `title`, `description`, `icon`, `published` | yes (track) |
| Module create/update/delete/reorder, `title`, `estimated_minutes` | yes (track) |
| Any entity bump beneath a track | yes (track — see below) |
| User preferences, progress, blog posts, whitelist sources | no — not packaged content |
| Any admin read | no |

##### Track `content_version` is a stored column, not a computed maximum

`tracks.content_version` is its own integer column, incremented by the service layer like any other. It is **not** derived as the maximum of its descendants' versions, and no implementation may compute it that way.

A maximum cannot decrease, so it cannot represent removal. Delete a lesson from a track and the maximum over the surviving entities is unchanged: the track advertises the same version it had a moment ago, every client concludes its library is current, and the deleted lesson stays on disk and in the interface indefinitely. The same blindness applies to reordering modules and to moving a lesson between them — operations that change what the track *is* while changing no descendant's own version.

The track counter therefore increments on every one of these:

- any change to the track's own fields (`slug`, `title`, `description`, `icon`, `published`),
- module create, update, delete, or reorder,
- lesson create, delete (including soft delete), move between modules, or reorder,
- any bump of any entity belonging to the track — lesson, code example, mind map,
- mind map create, update, or delete,
- creation, update or deletion of a `TRACK` or `MODULE` translation row (these have no packaged entity of their own; the track's counter is the only signal that they changed).

That last group is the one an implementation is most likely to miss: a `MODULE` translation belongs to no downloadable entity, so if the track's counter does not move, a client that has already downloaded the track will never learn the module title was translated.

##### Where the computation lives

The version counter is incremented and the content hash recomputed **in the service layer, in the same transaction as the write** — never by a database trigger or generated column, so the computation is unit-testable in isolation and identical between the write path and the manifest path. The hash algorithm itself, and the manifest that carries it, belong to the content sync protocol.

Every content-bumping response echoes the new `content_version` so a client can tell immediately whether its local copy is stale. A response that bumps a track indirectly (a lesson edit, say) echoes the lesson's version; the track's new version is observed through the manifest, not through the write response.

#### 5.4.2 Tracks

| # | Method | Path | Notes |
|---|---|---|---|
| 1 | `POST` | `/admin/tracks` | Create |
| 2 | `GET` | `/admin/tracks` | List including unpublished; query `published` (`true\|false`), `page`, `size`, `sort` (`order`, `title`, `updated_at`) |
| 3 | `GET` | `/admin/tracks/{id}` | Full detail with modules and lessons, canonical locale |
| 4 | `PATCH` | `/admin/tracks/{id}` | Partial update; `version` required |
| 5 | `DELETE` | `/admin/tracks/{id}` | §2.10 rules apply |
| 6 | `PUT` | `/admin/tracks/{id}/modules/order` | Bulk reorder |

`POST /admin/tracks`:

```json
{
  "slug": "rust-path",
  "title": "Rust Path",
  "description": "Ownership, borrowing and async Rust for service authors.",
  "icon": "rust",
  "order": 3,
  "published": false
}
```

`slug`, `title` required; `description`, `icon` optional (nullable); `order` optional (appended to the end when omitted); `published` optional, default `false`.

`201 Created`:

```json
{
  "id": "018f5d70-31aa-7c88-9012-6ab4f7e91c55",
  "slug": "rust-path",
  "title": "Rust Path",
  "description": "Ownership, borrowing and async Rust for service authors.",
  "icon": "rust",
  "order": 3,
  "published": false,
  "content_version": 1,
  "module_count": 0,
  "lesson_count": 0,
  "created_at": "2026-09-04T09:31:00.118Z",
  "updated_at": "2026-09-04T09:31:00.118Z",
  "version": 0
}
```

`PUT /admin/tracks/{id}/modules/order` — the reorder payload must list **every** module of the track exactly once; a partial list is rejected with `409 ORDER_SET_INCOMPLETE`. This makes reordering a single atomic call instead of N racing `PATCH`es.

```json
{
  "ordered_ids": [
    "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
    "018f3a02-9c31-7b12-a044-58e1b7d3ff02"
  ]
}
```

`200 OK` returns the modules with their new `order` values (1-based, contiguous, server-normalized).

Errors across the group: `VALIDATION_FAILED`, `SLUG_ALREADY_EXISTS` (409), `TRACK_NOT_FOUND` (404), `VERSION_CONFLICT` (409), `PARENT_NOT_EMPTY` (409), `PUBLISHED_DELETE_BLOCKED` (409), `ORDER_SET_INCOMPLETE` (409), `FORBIDDEN_ROLE`.

#### 5.4.3 Modules

| # | Method | Path |
|---|---|---|
| 7 | `POST` | `/admin/tracks/{trackId}/modules` |
| 8 | `PATCH` | `/admin/modules/{id}` |
| 9 | `DELETE` | `/admin/modules/{id}` |
| 10 | `PUT` | `/admin/modules/{id}/lessons/order` |

`POST`:

```json
{
  "title": "Ownership and Borrowing",
  "order": 1,
  "estimated_minutes": 120
}
```

`title` required; `order` optional (appended); `estimated_minutes` optional and nullable — when null, the client displays the sum of the module's lesson estimates.

`201 Created`:

```json
{
  "id": "018f5d71-6f02-7a3d-b7c9-0e1122ab3344",
  "track_id": "018f5d70-31aa-7c88-9012-6ab4f7e91c55",
  "title": "Ownership and Borrowing",
  "order": 1,
  "estimated_minutes": 120,
  "lesson_count": 0,
  "created_at": "2026-09-04T09:33:12.900Z",
  "updated_at": "2026-09-04T09:33:12.900Z",
  "version": 0
}
```

Moving a module to another track is done with `PATCH … {"track_id": "…", "version": n}`; the lessons move with it and each gets a `content_version` bump.

Errors: `VALIDATION_FAILED`, `TRACK_NOT_FOUND` (404), `MODULE_NOT_FOUND` (404), `VERSION_CONFLICT`, `PARENT_NOT_EMPTY`, `ORDER_SET_INCOMPLETE`, `FORBIDDEN_ROLE`.

#### 5.4.4 Lessons

| # | Method | Path |
|---|---|---|
| 11 | `POST` | `/admin/modules/{moduleId}/lessons` |
| 12 | `GET` | `/admin/lessons/{id}` |
| 13 | `PATCH` | `/admin/lessons/{id}` |
| 14 | `DELETE` | `/admin/lessons/{id}` — **soft delete** (§2.10); `UserProgress` is preserved |
| 15 | `PUT` | `/admin/lessons/{id}/code-examples/order` |

`POST`:

```json
{
  "slug": "ownership-rules",
  "title": "The Three Ownership Rules",
  "body_markdown": "## Rules\n\n1. Each value has one owner.\n2. There is one owner at a time.\n3. When the owner goes out of scope, the value is dropped.\n",
  "difficulty": "BEGINNER",
  "estimated_minutes": 20,
  "order": 1
}
```

`slug`, `title`, `body_markdown`, `difficulty` required; `estimated_minutes` optional (nullable); `order` optional (appended).

`201 Created`:

```json
{
  "id": "018f5d72-b910-7fe4-8ac0-71d5093e6a12",
  "module_id": "018f5d71-6f02-7a3d-b7c9-0e1122ab3344",
  "track_id": "018f5d70-31aa-7c88-9012-6ab4f7e91c55",
  "slug": "ownership-rules",
  "title": "The Three Ownership Rules",
  "body_markdown": "## Rules\n\n1. Each value has one owner.\n2. There is one owner at a time.\n3. When the owner goes out of scope, the value is dropped.\n",
  "difficulty": "BEGINNER",
  "estimated_minutes": 20,
  "order": 1,
  "content_version": 1,
  "code_examples": [],
  "created_at": "2026-09-04T09:35:44.201Z",
  "updated_at": "2026-09-04T09:35:44.201Z",
  "version": 0
}
```

`body_markdown` in the response is the stored text, which is byte for byte what was sent after normalization (§2.8) — a write that would have had to alter it is refused instead.

`PATCH` accepts any subset of the create fields plus `module_id` (to move the lesson) and the mandatory `version`.

Errors: `VALIDATION_FAILED`, `SLUG_ALREADY_EXISTS` (409), `MODULE_NOT_FOUND` (404), `LESSON_NOT_FOUND` (404), `UNSAFE_HTML` (422), `VERSION_CONFLICT`, `ORDER_SET_INCOMPLETE`, `PAYLOAD_TOO_LARGE`, `FORBIDDEN_ROLE`.

#### 5.4.5 Code examples

| # | Method | Path |
|---|---|---|
| 16 | `POST` | `/admin/lessons/{lessonId}/code-examples` |
| 17 | `PATCH` | `/admin/code-examples/{id}` |
| 18 | `DELETE` | `/admin/code-examples/{id}` |

```json
{
  "language": "rust",
  "code": "fn main() {\n    let s = String::from(\"bytelore\");\n    takes_ownership(s);\n}",
  "caption": "Ownership moves into the callee",
  "order": 1
}
```

`language` required, from a closed list of highlighter-supported identifiers (`typescript`, `javascript`, `java`, `rust`, `sql`, `bash`, `json`, `yaml`, `html`, `css`, `xml`, `kotlin`, `python`, `csharp`, `go`, `php`, `ruby`, `text`); an unknown value is `400 UNSUPPORTED_LANGUAGE`. `code` required. `caption` optional/nullable. `order` optional.

`201 Created` returns the code example plus the owning lesson's new `content_version`:

```json
{
  "id": "018f5d73-04c7-7d90-91ab-c3e8f2107744",
  "lesson_id": "018f5d72-b910-7fe4-8ac0-71d5093e6a12",
  "language": "rust",
  "code": "fn main() {\n    let s = String::from(\"bytelore\");\n    takes_ownership(s);\n}",
  "caption": "Ownership moves into the callee",
  "order": 1,
  "lesson_content_version": 2,
  "version": 0
}
```

Errors: `VALIDATION_FAILED`, `UNSUPPORTED_LANGUAGE` (400), `LESSON_NOT_FOUND` (404), `CODE_EXAMPLE_NOT_FOUND` (404), `VERSION_CONFLICT`, `FORBIDDEN_ROLE`.

#### 5.4.6 Mind maps

| # | Method | Path |
|---|---|---|
| 19 | `GET` | `/admin/tracks/{trackId}/mindmap` |
| 20 | `PUT` | `/admin/tracks/{trackId}/mindmap` |
| 21 | `DELETE` | `/admin/tracks/{trackId}/mindmap` |

`PUT` is an upsert of the whole tree — there is no per-node API. The body is `{ "root": <node>, "version": n }`, where `version` is omitted on first creation and required afterwards. Node shape and limits are defined in §5.2.4.

`200 OK` returns the stored tree with the new `content_version`. `201 Created` on first creation.

Errors: `VALIDATION_FAILED`, `TRACK_NOT_FOUND` (404), `MIND_MAP_NOT_FOUND` (404), `MIND_MAP_INVALID` (422 — depth/node limits, duplicate node id, `lesson_id` referencing a lesson outside the track), `VERSION_CONFLICT`, `FORBIDDEN_ROLE`.

### 5.5 Blog

All paths prefixed `/api/v1/admin`.

#### 5.5.1 Lifecycle rules

```
                     submit                  approve
    DRAFT ──────────────────► PENDING_REVIEW ────────► PUBLISHED
      ▲ │                          │                       │
      │ │ publish (MANUAL only)    │ reject                │ unpublish
      │ └──────────────────────────┼───────────────────────┤   (ADMIN)
      │                            ▼                       │
      │                        REJECTED                    │
      │                            │                       │
      └────────────────────────────┴───────────────────────┘
                        (edit, or unpublish, returns to DRAFT)
```

Four statuses, no fifth. `unpublish` returns a post to `DRAFT` rather than introducing a `WITHDRAWN` state, because every added status multiplies the cases three clients must switch on, and `DRAFT` already means exactly "not visible to the public, editable, re-submittable".

Invariants enforced by the server, not by client discipline:

1. A post with `source = AUTO` **cannot** transition directly to `PUBLISHED`. The only path is `PENDING_REVIEW → approve → PUBLISHED`, and `approve` is `ADMIN`-only. Attempting `publish` on an auto post returns `409 AUTO_POST_APPROVAL_REQUIRED`.
2. A post with `source = AUTO` and a null/blank `source_url` cannot leave `DRAFT` → `422 AUTO_POST_SOURCE_LINK_REQUIRED`.
3. A post whose originating `SourceUpdate` did not pass the verification chain cannot be approved → `409 SOURCE_UPDATE_NOT_VERIFIED`.
4. **`PUBLISHED` is reversible.** `POST …/unpublish` (`ADMIN`, mandatory `reason`) returns the post to `DRAFT` and sets `published_at` back to `null`. A published post can also be edited in place; edits alone do not change status. Withdrawal exists because the pipeline's entire purpose is preventing wrong information from reaching readers, and the answer to "it got through anyway" cannot be "edit the body and hope" — a factually wrong post must be removable by the same people who approved it, in seconds, without a database session.
5. An unpublished post republishes through the normal route for its source: `publish` for `MANUAL`, `submit` → `approve` for `AUTO`. Unpublishing an auto post therefore sends it back through human approval, which is the correct treatment for content that was just found to be wrong.
6. Every transition writes a `PipelineAuditLog` row (`actor_user_id`, `from_status`, `to_status`, `reason`, `occurred_at`) in the same transaction as the status change. A transition that cannot be audited does not happen. `UNPUBLISH` is an audited step like any other, with a mandatory reason — a withdrawal without a recorded justification is the one audit gap that would matter most later.
7. `source` is set at creation and is immutable, and **when `source = AUTO`, `source_url` is immutable for every role, `ADMIN` included.** No `PATCH` may change it at any point in the lifecycle. The link is the provenance of automatically generated text: it is what a reader clicks to check the claim, and what an auditor follows to reconstruct where the content came from. A body that says one thing while its source link points somewhere else is worse than no citation, and permitting an administrator to repoint it turns the audit trail into something that must itself be audited. Attempts return `403 AUTO_POST_NOT_EDITABLE`. Correcting a genuinely wrong link means rejecting the post and letting the pipeline re-derive it from the source update.

#### 5.5.2 Endpoints

| # | Method | Path | Notes |
|---|---|---|---|
| 22 | `POST` | `/admin/blog/posts` | Creates a `DRAFT` with `source = MANUAL`; `source` is not accepted from the client |
| 23 | `GET` | `/admin/blog/posts` | Query `status`, `source`, `q`, `page`, `size`, `sort` (`created_at`, `updated_at`, `published_at`, `title`) |
| 24 | `GET` | `/admin/blog/posts/{id}` | Any status |
| 25 | `PATCH` | `/admin/blog/posts/{id}` | Body edits |
| 26 | `DELETE` | `/admin/blog/posts/{id}` | Blocked when `PUBLISHED` |
| 27 | `POST` | `/admin/blog/posts/{id}/submit` | `DRAFT`/`REJECTED` → `PENDING_REVIEW` |
| 28 | `POST` | `/admin/blog/posts/{id}/approve` | `PENDING_REVIEW` → `PUBLISHED` · `ADMIN` |
| 29 | `POST` | `/admin/blog/posts/{id}/reject` | `PENDING_REVIEW` → `REJECTED` · `ADMIN` |
| 30 | `POST` | `/admin/blog/posts/{id}/publish` | `DRAFT`/`PENDING_REVIEW` → `PUBLISHED`, **manual posts only** |
| 31 | `POST` | `/admin/blog/posts/{id}/unpublish` | `PUBLISHED` → `DRAFT` · `ADMIN` · `reason` required |

`POST /admin/blog/posts`:

```json
{
  "slug": "why-we-hash-in-the-service-layer",
  "title": "Why We Hash in the Service Layer",
  "body_markdown": "Computing content hashes in application code instead of a database trigger keeps the rule testable.\n",
  "source_url": null
}
```

`slug`, `title`, `body_markdown` required; `source_url` optional and nullable for manual posts.

`201 Created`:

```json
{
  "id": "018f5e02-c1d4-7b77-8ee0-9a3b5c6d7e81",
  "slug": "why-we-hash-in-the-service-layer",
  "title": "Why We Hash in the Service Layer",
  "body_markdown": "Computing content hashes in application code instead of a database trigger keeps the rule testable.\n",
  "status": "DRAFT",
  "source": "MANUAL",
  "source_url": null,
  "source_update_id": null,
  "published_at": null,
  "created_by": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "created_at": "2026-09-04T09:41:02.775Z",
  "updated_at": "2026-09-04T09:41:02.775Z",
  "version": 0
}
```

Transition endpoints share one request body:

```json
{ "expected_status": "PENDING_REVIEW", "reason": "Version string verified against the GitHub release tag." }
```

`expected_status` required; `reason` required for `reject` **and `unpublish`** (10–500 chars), optional elsewhere, and always stored in the audit log.

`200 OK` returns the post in its new status, `published_at` set on publication and reset to `null` on unpublication.

An unpublish makes the post `404` on the public endpoints immediately. A URL that resolved yesterday returns `404` today — the accepted cost of the simple design, and the reason unpublishing is `ADMIN`-only and audited rather than routine. If published posts are ever linked externally at volume, revisit this with a tombstone response instead of a bare `404`.

Errors: `VALIDATION_FAILED`, `SLUG_ALREADY_EXISTS` (409), `BLOG_POST_NOT_FOUND` (404), `INVALID_STATE_TRANSITION` (409), `AUTO_POST_APPROVAL_REQUIRED` (409), `AUTO_POST_SOURCE_LINK_REQUIRED` (422), `SOURCE_UPDATE_NOT_VERIFIED` (409), `AUTO_POST_NOT_EDITABLE` (403), `UNSAFE_HTML` (422), `PUBLISHED_DELETE_BLOCKED` (409), `VERSION_CONFLICT`, `FORBIDDEN_ROLE`.

### 5.6 Translations

All paths prefixed `/api/v1/admin`. `entity_type` ∈ **`TRACK | MODULE | LESSON | BLOG_POST`**.

`MIND_MAP` is **not** translatable in v1 (§5.2.4); passing it returns `400 ENTITY_TYPE_UNSUPPORTED`.

| # | Method | Path |
|---|---|---|
| 32 | `GET` | `/admin/translations/{entityType}/{entityId}` |
| 33 | `PUT` | `/admin/translations/{entityType}/{entityId}/{locale}` |
| 34 | `DELETE` | `/admin/translations/{entityType}/{entityId}/{locale}` |

`GET` returns every stored translation plus the canonical English text, so a translation editor can show source and target side by side:

```json
{
  "entity_type": "LESSON",
  "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "canonical": {
    "locale": "en",
    "title": "Signals and Reactivity",
    "body": "## What is a signal?\n\nA signal is a value wrapper that records a dependency on whatever reads it.\n"
  },
  "translations": [
    {
      "locale": "tr",
      "title": "Sinyaller ve Reaktivite",
      "body": "## Sinyal nedir?\n\nBir sinyal, okundugunda kendisini okuyan hesaplamaya bagimlilik kaydeden bir deger sarmalayicisidir.\n",
      "updated_at": "2026-08-29T11:20:04.771Z",
      "version": 2
    }
  ],
  "missing_locales": ["fr", "de"]
}
```

`PUT` upserts one locale:

```json
{
  "title": "Signale und Reaktivität",
  "body": "## Was ist ein Signal?\n\nEin Signal ist ein Wertcontainer, der eine Abhängigkeit für jeden Leser registriert.\n",
  "version": 0
}
```

`title` is always required. `version` is omitted on first creation and required on update. `200 OK` (or `201 Created` on first write) returns the stored row with the translated entity's new `content_version`.

**`body` requirement depends on the entity type**, because "translated" means different things for a heading and for an article:

| `entity_type` | `body` | Reasoning |
|---|---|---|
| `LESSON` | **required**, `@NotBlank` | The lesson body *is* the lesson. A row with a translated title and an English body renders as a Turkish heading above an English article while `is_fallback` reports `false` — the reader is told the page is translated and it visibly is not. |
| `BLOG_POST` | **required**, `@NotBlank` | Same reasoning; a post is its body. |
| `TRACK` | optional, nullable | `body` carries the track description, which is a subtitle. A translated title with no description is a coherent, useful partial state. |
| `MODULE` | optional, nullable | A module has a title and no body of its own; `body` is almost always `null` here. |

Violations return `VALIDATION_FAILED` with `errors[].field = "body"`. The constraint is class-level (it depends on the path's `entity_type`), not a field annotation.

The alternative — accepting a title-only lesson translation — was rejected because it makes `is_fallback: false` a lie, and `is_fallback` is the single signal every client uses to decide whether to show the "not yet translated" badge.

`locale` = `en` is rejected with `422 CANONICAL_LOCALE_NOT_ALLOWED`: English lives in the entity's own columns, and allowing a second English copy would create two answers to "what is the English title".

Errors: `VALIDATION_FAILED`, `UNSUPPORTED_LOCALE` (400), `CANONICAL_LOCALE_NOT_ALLOWED` (422), `ENTITY_TYPE_UNSUPPORTED` (400 — includes `MIND_MAP`), `TRANSLATION_NOT_FOUND` (404), `TRACK_NOT_FOUND` / `MODULE_NOT_FOUND` / `LESSON_NOT_FOUND` / `BLOG_POST_NOT_FOUND` (404), `UNSAFE_HTML` (422), `VERSION_CONFLICT`, `FORBIDDEN_ROLE`.

### 5.7 Review queue, sources, audit

All paths prefixed `/api/v1/admin`.

| # | Method | Path | Role |
|---|---|---|---|
| 35 | `GET` | `/admin/review-queue` | `EDITOR` (read-only), `ADMIN` |
| 36 | `GET` | `/admin/review-queue/{postId}` | `EDITOR` (read-only), `ADMIN` |
| 37 | `GET` | `/admin/source-updates/{id}` | `EDITOR`, `ADMIN` |
| 38 | `GET` | `/admin/blog/posts/{id}/audit-log` | `EDITOR`, `ADMIN` |
| 39 | `GET` | `/admin/whitelist-sources` | `ADMIN` |
| 39a | `GET` | `/admin/whitelist-sources/{id}` | `ADMIN` |
| 40 | `POST` | `/admin/whitelist-sources` | `ADMIN` |
| 41 | `PATCH` | `/admin/whitelist-sources/{id}` | `ADMIN` |
| 42 | `DELETE` | `/admin/whitelist-sources/{id}` | `ADMIN` |
| 43 | `POST` | `/admin/whitelist-sources/{id}/fetch` | `ADMIN` |

`GET /admin/review-queue` — `PENDING_REVIEW` posts, oldest first by default. Query: `source`, `page`, `size`, `sort` (`created_at`, `updated_at`).

`GET /admin/review-queue/{postId}` returns everything the side-by-side review screen needs in one call: the generated draft, the raw fetched source, and the verification record. The screen computes the diff client-side; the server does not ship a diff format.

```json
{
  "post": {
    "id": "018f4c10-77a3-7e51-8b02-3f9c1d6e2200",
    "slug": "spring-boot-4-1-1-released",
    "title": "Spring Boot 4.1.1 Released",
    "body_markdown": "Spring Boot **4.1.1** is available.\n\n## Highlights\n\n- 43 bug fixes\n- Upgrade to Spring Framework 7.1.2\n\n[Release notes](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.1)\n",
    "status": "PENDING_REVIEW",
    "source": "AUTO",
    "source_url": "https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now",
    "created_at": "2026-08-20T12:00:11.004Z",
    "version": 1
  },
  "source_update": {
    "id": "018f4c0f-2a55-7d19-83b4-6c7e9f001122",
    "whitelist_source": {
      "id": "018f2100-4d31-7a09-8b77-0f1e2d3c4b5a",
      "name": "Spring Blog",
      "feed_url": "https://spring.io/blog.atom"
    },
    "version_string": "4.1.1",
    "content_hash": "9f2c1b7d5a3e08c4b6d1f0a29e7c48b53d61fa0c8e29d7b4a51c30f6e8d92b17",
    "fetched_at": "2026-08-20T11:58:42.310Z",
    "verify_status": "VERIFIED",
    "verify_checks": [
      { "check": "SOURCE_WHITELISTED", "passed": true, "detail": "enabled=true" },
      { "check": "ITEM_RECENT", "passed": true, "detail": "published=2026-08-20T11:00:00Z cutoff=2026-08-06T11:58:42Z" },
      { "check": "STABLE_RELEASE", "passed": true, "detail": null },
      { "check": "VERSION_CONFIRMED", "passed": true, "detail": "https://api.github.com/repos/spring-projects/spring-boot/releases/tags/v4.1.1 → 4.1.1" },
      { "check": "HASH_NOT_SEEN", "passed": true, "detail": null },
      { "check": "CONTENT_SANITY", "passed": true, "detail": "length=4182" },
      { "check": "DRAFT_VALIDATION", "passed": true, "detail": null }
    ],
    "raw_content": "Spring Boot 4.1.1 has been released and is available from Maven Central. This release includes 43 bug fixes, documentation improvements and dependency upgrades..."
  }
}
```

**`source_update` is `null` for a manually written post.** A post reaches `PENDING_REVIEW` from either direction: the pipeline drafted it from a fetched source, or a person wrote it and submitted it. Only the first has a fetch to show. A review screen therefore renders one panel rather than two in that case, and must not treat the absence as an error.

`verify_status` ∈ `PENDING | VERIFIED | REJECTED`. `verify_checks` is ordered as executed — `SOURCE_WHITELISTED`, `ITEM_RECENT`, `STABLE_RELEASE`, `VERSION_CONFIRMED`, `HASH_NOT_SEEN`, `CONTENT_SANITY`, `DRAFT_VALIDATION` — and the first failing entry is the reason a `REJECTED` update never became a draft. `raw_content` is the feed item's text as fetched and normalized.

**`ITEM_RECENT` and `STABLE_RELEASE` run before `VERSION_CONFIRMED`, at zero network cost.** Both are pure checks over the feed item and the already-extracted version string, deliberately placed ahead of the one check that makes a request, so an item that would fail for a reason visible without any network call never causes one.

- **`ITEM_RECENT`** passes if the feed item's own `published`/`updated` timestamp is no older than `bytelore.pipeline.max-item-age` (default 14 days), or if the item carries no such timestamp at all — a missing date is not evidence the release is old, and some whitelisted feeds (the announcement-style ones, unlike GitHub Releases) do not reliably carry one.
- **`STABLE_RELEASE`** passes if the extracted version string is exactly its numeric core, with nothing trailing it: no `-`/`+`-separated pre-release tag (`-rc1`, `-beta.1`, `-M1`, `+14`), and no bare-letter suffix directly attached with no separator either (`3.15.0b2`). The version extractor captures such a suffix whole rather than truncating at the numeric core, specifically so this check can see it — a truncated `"3.15.0"` extracted from `"3.15.0b2"` could otherwise be confirmed by `VERSION_CONFIRMED` against a verify endpoint whose body legitimately contains `"3.15.0"` as a substring of its real announcement, and reach the review queue mislabeled as the stable release it only previews.

**`VERSION_CONFIRMED` — the definition.** This check is the load-bearing one in the chain, and "we made a second request" is not a definition anyone can implement twice the same way. It passes if and only if **both** hold:

1. The independent request to the source's `verify_url_pattern`, with `{version}` substituted, returns HTTP **200**.
2. The **response body contains the version string as a literal substring**, compared case-sensitively after Unicode NFC normalization.

Both halves are required. Status alone is worthless: an API that answers `200` with `{"message": "not found"}`, a site that serves a soft-404 page, or a CDN that returns a generic landing page all satisfy a status check while confirming nothing. A body match alone is equally worthless — a `404` page that echoes the requested version back in its error text would "confirm" any string a caller invented.

Anything else fails the check and the update is rejected with an audit entry: a non-200 status, a redirect chain ending anywhere but 200, a timeout, a TLS failure, or a 200 whose body lacks the literal — **with one deliberate exception**. A `403` or `429` from the verify request is not treated as a failed check: most `verify_url_pattern` rows point at `api.github.com`, whose anonymous rate limit (60/hour) a sweep across every enabled source can exhaust well before an optional `bytelore.pipeline.github-token` raises it to 5000/hour, and a status that means "the endpoint is rate-limited right now" is not evidence the claimed version is fake. The item is **deferred**, not rejected: no `SourceUpdate` row is written for it (so its `content_hash` is never recorded as processed), a `PipelineAuditLog` entry names it as deferred rather than rejected, and it is retried in full on the next fetch cycle. Rejecting it as an ordinary `VERSION_CONFIRMED` failure instead would do lasting damage a retry could otherwise undo: the `SourceUpdate` row a rejection writes is exactly what the dedup check at the top of the pipeline uses to recognize "already processed," so a transient rate limit would otherwise become a permanent block on that item, on every future cycle, long after the rate limit itself has cleared.

**The version string is validated before it is ever placed in a URL.** It must match `^[A-Za-z0-9._+-]{1,64}$`; a value that does not is rejected outright and never interpolated. On substitution it is additionally percent-encoded as a URL path segment. Both steps are required and neither replaces the other: the pattern is what stops `../`, a scheme, an authority, a query separator or whitespace from reaching URL construction, and the encoding is what handles the characters the pattern legitimately allows. Without them, a feed — remote input, from a source whose *feed* is trusted but whose *field contents* are not — chooses part of the URL the server then fetches, which is how a whitelist becomes a server-side request forgery primitive against the one component that is supposed to be verifying trust.

The check runs against the whitelist source's stored `verify_url_pattern` only. It never follows a URL taken from the feed item itself.

`GET /admin/blog/posts/{id}/audit-log`:

```json
{
  "items": [
    {
      "id": "018f4c11-0f2a-7c60-99d3-77aa11bb22cc",
      "step": "FETCH",
      "actor_user_id": null,
      "from_status": null,
      "to_status": null,
      "reason": "spring-blog feed item 2026-08-20",
      "occurred_at": "2026-08-20T11:58:42.310Z"
    },
    {
      "id": "018f4c11-3b81-7e02-8a44-99cc33dd44ee",
      "step": "APPROVE",
      "actor_user_id": "018f3b20-1122-7aa0-9f01-334455667788",
      "from_status": "PENDING_REVIEW",
      "to_status": "PUBLISHED",
      "reason": "Version string verified against the GitHub release tag.",
      "occurred_at": "2026-08-20T13:05:00.000Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 5,
  "total_pages": 1
}
```

`step` ∈ `FETCH | NORMALIZE | VERIFY | DRAFT | SUBMIT | APPROVE | REJECT | PUBLISH | UNPUBLISH`. `actor_user_id` is `null` for machine steps and non-null for every human decision — that distinction is the audit trail's whole purpose. The audit log is append-only; there is no write endpoint for it on this API.

`POST /admin/whitelist-sources`:

```json
{
  "name": "Angular Releases",
  "feed_url": "https://github.com/angular/angular/releases.atom",
  "verify_url_pattern": "https://api.github.com/repos/angular/angular/releases/tags/{version}",
  "enabled": true
}
```

`name`, `feed_url`, `verify_url_pattern` required; `enabled` optional, default `true`. Both URLs must be absolute `https://` (an `http://` feed is rejected — the trust chain cannot start on a channel anyone can rewrite). `verify_url_pattern` must contain exactly one `{version}` placeholder.

`201 Created` returns the row with `id`, `created_at`, `updated_at`, `version`, and `last_fetched_at` (nullable).

Deleting a whitelist source that has `SourceUpdate` rows is refused with `409 PARENT_NOT_EMPTY`; disable it with `PATCH {"enabled": false, "version": n}` instead. Provenance must remain reconstructible for every published post.

#### `POST /admin/whitelist-sources/{id}/fetch`

Runs the ingest cycle for one source immediately, instead of waiting up to six hours for the scheduler. Without it, the only way to test a newly added source is to add it and wait — which means new sources get added blind, and a typo in a `verify_url_pattern` is discovered a quarter of a day later, if at all.

`ADMIN` only, rate-limited to 6 per hour per source (§3.6). No request body.

**It is not a second code path.** It calls the identical service method the scheduler calls, with no arguments that alter behaviour, under the **same ShedLock lock name** — so a manual run and a scheduled run can never process the same source concurrently, and a manual run cannot skip, reorder or weaken any step of the verification chain. There is no `?skip_verify`, no `?force`, and no parameter of any kind: an endpoint that could bypass verification would be the single most dangerous thing in this system, so it is built such that bypassing is not expressible.

The call is **synchronous** and returns when the cycle completes, because the operator is testing a configuration and needs the result, not an acknowledgement. Feed fetches are bounded by a short timeout, so the request does not hang indefinitely.

`200 OK`:

```json
{
  "whitelist_source_id": "018f2100-4d31-7a09-8b77-0f1e2d3c4b5a",
  "fetched": 12,
  "created": 2,
  "duplicates": 9,
  "rejected": 1,
  "created_source_update_ids": [
    "018f4c0f-2a55-7d19-83b4-6c7e9f001122",
    "018f4c0f-8b71-7a03-92c1-4d5e6f778899"
  ],
  "rejections": [
    {
      "version_string": "20.1.0-next.3",
      "failed_check": "VERSION_CONFIRMED",
      "detail": "HTTP 404 from verify URL"
    }
  ],
  "duration_ms": 3184
}
```

`fetched` counts items that reached a decision this cycle; `created` new `SourceUpdate` rows; `duplicates` items whose `content_hash` was already known; `rejected` items that failed the verification chain. **`created + duplicates + rejected` equals `fetched`, always** — this is not merely descriptive, it is the whole reason `fetched` is defined the way it is (see the next paragraph). The chain's last check is `DRAFT_VALIDATION`: the drafted body is offered to the same allow-list an authored body faces (§2.8), and an item whose draft would be refused is rejected as a failed check — visibly, with the rest of the feed still processed — rather than aborting the cycle. An item that fails in some other way is reported with the check name `ITEM_FAILED`; it too costs only that item. Every outcome is written to `PipelineAuditLog` exactly as a scheduled run would write it, with `actor_user_id` set to the calling administrator — a manual run is attributable, a scheduled one is not.

**A deferred item (see `VERSION_CONFIRMED` above) is not counted anywhere in this response, not merely excluded from `rejected`.** The feed may have carried more items than `fetched` reports; the difference, if any, is the number of items deferred this cycle. This is a deliberate design choice, not an oversight: a deferred item was skipped, not decided, so folding it into `fetched` (as an items-seen count) while excluding it from the three counts that must sum to `fetched` would break the invariant above, and counting it under `rejected` would misrepresent it to any caller that treats `rejected` as "this item failed verification" — which is exactly what a deferred item did not do. Its only visible trace in this API is a `PipelineAuditLog` row (`GET /admin/blog/posts/{id}/audit-log` for a post-scoped trail; there is no source-scoped audit-log endpoint in this phase). An operator who needs to know a fetch was rate-limited reads the audit log, not this response's counts.

If the scheduler (or another manual run) currently holds the lock, the request returns `409 PIPELINE_RUN_IN_PROGRESS` immediately rather than queueing or blocking.

Errors: `WHITELIST_SOURCE_NOT_FOUND` (404), `PIPELINE_RUN_IN_PROGRESS` (409), `RATE_LIMITED` (429), `FORBIDDEN_ROLE` (403).

Errors across the group: `VALIDATION_FAILED`, `BLOG_POST_NOT_FOUND` (404), `SOURCE_UPDATE_NOT_FOUND` (404), `WHITELIST_SOURCE_NOT_FOUND` (404), `INSECURE_SOURCE_URL` (422), `INVALID_VERIFY_URL_PATTERN` (422), `PARENT_NOT_EMPTY` (409), `VERSION_CONFLICT`, `FORBIDDEN_ROLE`.

### 5.8 Progress sync

| # | Method | Path | Role |
|---|---|---|---|
| 44 | `POST` | `/api/v1/sync/progress` | `USER`, `EDITOR`, `ADMIN` |
| 45 | `GET` | `/api/v1/sync/progress` | `USER`, `EDITOR`, `ADMIN` |

#### 5.8.1 `POST /api/v1/sync/progress`

Batch upload of local completion state. Always scoped to the authenticated user; there is no `user_id` in the payload and one user can never write another's progress.

```json
{
  "items": [
    {
      "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
      "completed_at": "2026-09-02T20:11:07.400Z",
      "client_updated_at": "2026-09-02T20:11:07.412Z"
    },
    {
      "lesson_id": "018f3b21-88f2-7d15-b3ac-9e10d4f2c001",
      "completed_at": null,
      "client_updated_at": "2026-09-03T07:15:02.900Z"
    }
  ]
}
```

- `lesson_id`, `client_updated_at` required. `completed_at` is required as a key but **nullable**: `null` means "this lesson was un-completed", which is a real user action and must be syncable, not merely an absence.
- 1–500 items. Duplicate `lesson_id` values within one batch → `400 DUPLICATE_ITEM_IN_BATCH`.

**Reconciliation — last-write-wins on `client_updated_at`:**

1. **Clock clamping happens first.** If `client_updated_at` is ahead of server time by more than **24 hours**, the server replaces it with `min(client_updated_at, server_time)` — i.e. server time — and processes the item normally with the clamped value. The response reports `clamped: true` and returns the value actually stored. `completed_at` is clamped by the same rule.
2. If no server row exists, the item is inserted → `APPLIED`.
3. If the (possibly clamped) `client_updated_at` is **strictly greater** than the stored `client_updated_at`, the row is overwritten → `APPLIED`.
4. Otherwise the item is discarded → `STALE`. Equal timestamps keep the stored row, so the rule is deterministic and replay-safe.
5. If `lesson_id` is unknown, soft-deleted or otherwise unresolvable → `REJECTED` with `LESSON_NOT_FOUND`. This is the only per-item rejection.

**Why clamp rather than reject.** A device with a badly wrong clock — a dead CMOS battery, a bad timezone setup, a manual clock change — is not misbehaving on purpose, and its user's completions are real. Rejecting future-dated items would put that device in a permanent deadlock: every write it makes is refused, so its progress never syncs, and nothing the user does inside the application can fix it. Clamping accepts the work, discards only the impossible part of the metadata, and lets the device converge with everyone else the moment its clock is corrected. The one thing clamping must prevent — a far-future timestamp winning every comparison forever — it prevents completely, because the stored value can never exceed server time.

Clamping is silent to the user but visible to the client: `clamped: true` is the signal to surface a "your device clock looks wrong" hint, and it is worth logging, since a device that clamps on every sync has a real problem.

A batch is never rejected as a whole because of individual bad items; the response reports per item. This matters because the desktop client may hold weeks of offline writes, and one stale lesson reference must not block the rest.

`200 OK`:

```json
{
  "server_time": "2026-09-04T09:52:18.006Z",
  "applied_count": 2,
  "stale_count": 0,
  "rejected_count": 1,
  "clamped_count": 1,
  "results": [
    {
      "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
      "status": "APPLIED",
      "code": null,
      "clamped": false,
      "server_client_updated_at": "2026-09-02T20:11:07.412Z"
    },
    {
      "lesson_id": "018f3b21-88f2-7d15-b3ac-9e10d4f2c001",
      "status": "REJECTED",
      "code": "LESSON_NOT_FOUND",
      "clamped": false,
      "server_client_updated_at": null
    },
    {
      "lesson_id": "018f3b22-4d90-7c11-8a55-6f2e0b1d9933",
      "status": "APPLIED",
      "code": null,
      "clamped": true,
      "server_client_updated_at": "2026-09-04T09:52:18.006Z"
    }
  ]
}
```

`status` ∈ `APPLIED | STALE | REJECTED`. `code` is `null` unless `status` is `REJECTED`. `clamped` is `true` when rule 1 rewrote the item's timestamps. `server_client_updated_at` is the value the server holds after processing, so a client that received `STALE` or `clamped: true` learns the winning timestamp without a second request — and should write it back into its local row, so the next sync compares against the same value the server holds.

The whole batch is processed in one transaction. `server_time` lets the client measure its clock offset.

Errors: `AUTH_REQUIRED`, `ACCESS_TOKEN_EXPIRED`, `VALIDATION_FAILED`, `DUPLICATE_ITEM_IN_BATCH` (400), `SYNC_BATCH_TOO_LARGE` (413), `RATE_LIMITED`.

#### 5.8.2 `GET /api/v1/sync/progress`

Pull direction: a fresh installation, or a device that has been offline while another device made progress.

Query: `since` (ISO-8601, optional — server-side `updated_at` lower bound, exclusive), `page`, `size`. Default sort is `updated_at,asc`, which makes `since`-based paging stable.

`200 OK`:

```json
{
  "items": [
    {
      "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
      "completed_at": "2026-09-02T20:11:07.400Z",
      "client_updated_at": "2026-09-02T20:11:07.412Z",
      "updated_at": "2026-09-02T20:14:55.881Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1,
  "server_time": "2026-09-04T09:53:40.119Z"
}
```

The client applies the same last-write-wins rule locally: a pulled row overwrites the local row only when its `client_updated_at` is strictly greater. The rule is symmetric on both sides of the wire, which is what makes repeated syncs converge.

Errors: `AUTH_REQUIRED`, `ACCESS_TOKEN_EXPIRED`, `VALIDATION_FAILED`, `PAGE_SIZE_EXCEEDED`.

---

## 6. Error code catalogue

Every non-2xx response body is exactly:

```json
{
  "code": "LESSON_NOT_FOUND",
  "message": "No lesson exists with slug 'signals-and-reactivty'."
}
```

- `code` is a stable `SCREAMING_SNAKE_CASE` identifier. Clients map it to a translation key and branch on it. It is never renamed after release.
- `message` is English, developer-facing, and intended for logs and debugging. **Clients never display it to end users** and never parse it.
- One additive extension exists: `VALIDATION_FAILED` responses carry an `errors` array. A client that reads only `code` and `message` remains correct.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed for 2 fields.",
  "errors": [
    { "field": "slug", "code": "PATTERN", "message": "must match \"^[a-z0-9]+(?:-[a-z0-9]+)*$\"" },
    { "field": "estimated_minutes", "code": "RANGE", "message": "must be between 1 and 600" }
  ]
}
```

`errors[].field` uses the JSON path of the offending property in `snake_case` (`code_examples[0].language`). `errors[].code` ∈ `REQUIRED | PATTERN | SIZE | RANGE | FORMAT | ENUM`.

### 6.1 Catalogue

**Request / validation — 400, 413, 415**

| Code | HTTP | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation rejected one or more fields, **or** a write reached the database and violated a constraint that mirrors a validation rule (§7's "database constraints mirror them so a bypass fails loudly") — the latter carries no `errors[]` array, only `code` and `message` |
| `MALFORMED_REQUEST` | 400 | Body is not parseable JSON, or a field has the wrong JSON type |
| `INVALID_PARAMETER` | 400 | A query parameter is present but not a legal value |
| `UNSUPPORTED_LOCALE` | 400 | `locale` outside `en\|tr\|fr\|de` |
| `UNSUPPORTED_LANGUAGE` | 400 | Code example `language` outside the highlighter list |
| `ENTITY_TYPE_UNSUPPORTED` | 400 | `entity_type` outside the translatable set |
| `INVALID_SORT_FIELD` | 400 | `sort` names a field not whitelisted for the endpoint |
| `PAGE_SIZE_EXCEEDED` | 400 | `size` above 100 |
| `DUPLICATE_ITEM_IN_BATCH` | 400 | Same `lesson_id` twice in one sync batch |
| `AMBIGUOUS_TOKEN_DELIVERY` | 400 | A refresh token was supplied in both the request body and the cookie (§3.8) |
| `PAYLOAD_TOO_LARGE` | 413 | Request body above 1 MiB |
| `SYNC_BATCH_TOO_LARGE` | 413 | More than 500 sync items |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Body present with a non-JSON `Content-Type` |
| `METHOD_NOT_ALLOWED` | 405 | Path exists, verb does not |

**Authentication — 401**

| Code | HTTP | When |
|---|---|---|
| `AUTH_REQUIRED` | 401 | Endpoint requires authentication; no credentials presented |
| `INVALID_CREDENTIALS` | 401 | Login failed (unknown email or wrong password — deliberately indistinguishable) |
| `ACCESS_TOKEN_EXPIRED` | 401 | JWT `exp` passed. **Refresh; never sign out** |
| `ACCESS_TOKEN_INVALID` | 401 | Signature, structure or `typ` claim wrong |
| `REFRESH_TOKEN_INVALID` | 401 | Unknown or revoked refresh token |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh token past its own lifetime (each rotation issues a fresh one, §3.2) |
| `REFRESH_TOKEN_REUSED` | 401 | A rotated token was replayed outside the grace window, or its successor had already been used; the whole family is now revoked |

**Authorization — 403**

| Code | HTTP | When |
|---|---|---|
| `FORBIDDEN_ROLE` | 403 | Authenticated, but the role is insufficient for this endpoint |
| `ACCOUNT_DISABLED` | 403 | The account exists but is disabled |
| `AUTO_POST_NOT_EDITABLE` | 403 | An `EDITOR` attempted to edit the body of an `AUTO` post, or **any role** attempted to change its `source_url` |

**Not found — 404**

| Code | HTTP |
|---|---|
| `TRACK_NOT_FOUND` | 404 |
| `MODULE_NOT_FOUND` | 404 |
| `LESSON_NOT_FOUND` | 404 |
| `CODE_EXAMPLE_NOT_FOUND` | 404 |
| `MIND_MAP_NOT_FOUND` | 404 |
| `BLOG_POST_NOT_FOUND` | 404 |
| `TRANSLATION_NOT_FOUND` | 404 |
| `SOURCE_UPDATE_NOT_FOUND` | 404 |
| `WHITELIST_SOURCE_NOT_FOUND` | 404 |
| `USER_NOT_FOUND` | 404 |

**Conflict — 409**

| Code | HTTP | When |
|---|---|---|
| `EMAIL_ALREADY_REGISTERED` | 409 | Registration with an existing email |
| `SLUG_ALREADY_EXISTS` | 409 | Slug taken within its uniqueness scope |
| `VERSION_CONFLICT` | 409 | Optimistic lock failure; re-read and retry |
| `INVALID_STATE_TRANSITION` | 409 | Blog transition illegal from the current status, or `expected_status` mismatch |
| `AUTO_POST_APPROVAL_REQUIRED` | 409 | Attempt to publish an `AUTO` post without the admin approval step |
| `SOURCE_UPDATE_NOT_VERIFIED` | 409 | Approval attempted while the originating source update is `PENDING` or `REJECTED` |
| `PARENT_NOT_EMPTY` | 409 | Delete refused: the resource still has children |
| `PUBLISHED_DELETE_BLOCKED` | 409 | Delete refused: the resource is published |
| `ORDER_SET_INCOMPLETE` | 409 | A reorder payload did not list every child exactly once |
| `PIPELINE_RUN_IN_PROGRESS` | 409 | A manual fetch was requested while the scheduler or another manual run holds the source's lock |
| `CONTENT_VERSION_SUPERSEDED` | 409 ² | A package was requested at a `content_version` that is no longer current |

**Semantic — 422**

| Code | HTTP | When |
|---|---|---|
| `UNSAFE_HTML` | 422 | A markdown body or plain-text field carries markup outside the server allow-list (§2.8). `message` names the elements and attributes |
| `AUTO_POST_SOURCE_LINK_REQUIRED` | 422 | An `AUTO` post has no source link and cannot leave `DRAFT` |
| `CANONICAL_LOCALE_NOT_ALLOWED` | 422 | Attempt to store `en` as a `ContentTranslation` row |
| `MIND_MAP_INVALID` | 422 | Node/depth limits, duplicate node id, or a `lesson_id` outside the track |
| `INSECURE_SOURCE_URL` | 422 | Whitelist source URL is not absolute `https://` |
| `INVALID_VERIFY_URL_PATTERN` | 422 | `verify_url_pattern` lacks exactly one `{version}` placeholder, or the version string fails `^[A-Za-z0-9._+-]{1,64}$` |
| `CURRENT_PASSWORD_INCORRECT` | 422 | Password change with a wrong `current_password`. **Deliberately not 401** — see §5.1.7 |
| `CONTENT_PACKAGE_TOO_LARGE` | 422 | A write would produce a content package above the packaging size ceiling |

**Precondition — 412**

| Code | HTTP | When |
|---|---|---|
| `CONTENT_CHANGED_DURING_RESUME` | 412 ² | A ranged resume was attempted against content whose bytes changed since the partial download began |

**Throttling and server — 429, 500, 503**

| Code | HTTP | When |
|---|---|---|
| `RATE_LIMITED` | 429 | Window exceeded; `Retry-After` header carries the wait in seconds |
| `INTERNAL_ERROR` | 500 | Unhandled server fault. `message` is generic; details go to logs with the `X-Request-Id` |
| `SERVICE_UNAVAILABLE` | 503 | Database or a required dependency is unreachable |

² **Owned by the content sync protocol, listed here for registry completeness.** `CONTENT_VERSION_SUPERSEDED` and `CONTENT_CHANGED_DURING_RESUME` are raised only by the manifest and content-package endpoints; their triggering conditions, retry semantics and effect on the download queue are defined there, not here. They appear in this catalogue because the code namespace is shared across the whole API and a code must not be invented twice with two meanings. `CONTENT_PACKAGE_TOO_LARGE` is listed under 422 above because this surface can also raise it — a write that would produce an oversized package is refused at write time, before it can become a package no client can fetch.

**Withdrawn before release:** `CLIENT_TIMESTAMP_OUT_OF_RANGE` appeared in an earlier draft for future-dated sync timestamps. Those are now clamped rather than rejected (§5.8.1), so the code is never emitted and has been removed rather than retained as dead surface. It was never released; the never-rename rule applies from first release onward.

**57 codes.** Handled centrally by a `@RestControllerAdvice` that maps every exception type to exactly one code — no controller builds an error body by hand, and no unmapped exception escapes as a framework-default body.

---

## 7. Validation rules

Bean Validation (Jakarta) on request DTOs, `@Valid` at the controller boundary, failures surfacing as `VALIDATION_FAILED` (§6). Constraints below are the contract; database constraints mirror them so a bypass fails loudly rather than storing bad data.

Shared definitions:

- **Slug:** `@Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(min = 3, max = 80)` — lowercase, hyphen-separated, no leading/trailing/double hyphens.
- **Locale:** `@Pattern(regexp = "^(en|tr|fr|de)$")`, or an enum.
- **Markdown body:** `@NotBlank @Size(max = 200000)`.
- **Order:** `@Min(1) @Max(10000)` on input.

**Order normalization.** `order` is a **1-based, contiguous, unique sequence within the parent**, and the server owns it. On every write that affects ordering — create, update, delete, move between parents, or a bulk reorder — the service renumbers the parent's children to `1..n` in the resulting sequence, in the same transaction. Clients therefore never see gaps, duplicates or zeros, and never need to compute an ordering themselves.

What a submitted `order` means is "insert at this position": a value larger than the current child count appends, and a value colliding with an existing child inserts before it and pushes the rest down. An omitted `order` appends. Accepting `0` on input is what the earlier `@Min(0)` allowed and is now rejected, because a value that can never be stored should not be accepted — the first child is `1`, and a client that sent `0` and read back `1` would reasonably conclude the server had ignored it.

Bulk reorder (`PUT …/order`) is the only way to express a wholesale rearrangement atomically; per-item `order` edits are for single moves.

### 7.1 User

| Field | Constraints |
|---|---|
| `email` | `@NotBlank @Email @Size(max = 254)`; stored lowercase; unique |
| `password` | `@NotBlank @Size(min = 12, max = 128)`; no composition rules — length is the requirement, hashed with bcrypt (cost 12) |
| `locale` | optional, locale pattern, default `en` |
| `theme` | optional, `@Pattern("^(LIGHT|DARK|SYSTEM)$")`, default `SYSTEM` |
| `role` | server-assigned; rejected if present in a request body |
| `device_label` | optional, `@Size(max = 64)` |
| `token_delivery` | optional, `@Pattern("^(COOKIE|BODY)$")`, default `COOKIE`; accepted only on register and login (§3.8) |
| `current_password` | password change only: `@NotBlank`; a mismatch is `CURRENT_PASSWORD_INCORRECT` (422), never `401` |
| `new_password` | password change only: same rules as `password`; class-level constraint requires it to differ from `current_password` |

### 7.2 Track

| Field | Constraints |
|---|---|
| `slug` | `@NotBlank`, slug pattern, unique across tracks |
| `title` | `@NotBlank @Size(max = 200)` |
| `description` | optional/nullable, `@Size(max = 2000)` |
| `icon` | optional/nullable, `@Size(max = 64) @Pattern("^[a-z0-9-]+$")` — an icon identifier, never a URL |
| `order` | optional, order range |
| `published` | optional boolean, default `false` |

### 7.3 Module

| Field | Constraints |
|---|---|
| `track_id` | `@NotNull` on create (from the path), must reference an existing track |
| `title` | `@NotBlank @Size(max = 200)` |
| `order` | optional, order range |
| `estimated_minutes` | optional/nullable, `@Min(1) @Max(6000)` |

### 7.4 Lesson

| Field | Constraints |
|---|---|
| `slug` | `@NotBlank`, slug pattern, **globally unique among non-deleted lessons** |
| `title` | `@NotBlank @Size(max = 200)` |
| `body_markdown` | markdown body rules; checked against the allow-list before persistence and stored unchanged (§2.8); markup outside it → `UNSAFE_HTML` |
| `difficulty` | `@NotNull`, enum `BEGINNER\|INTERMEDIATE\|ADVANCED` |
| `estimated_minutes` | optional/nullable, `@Min(1) @Max(600)` |
| `order` | optional, order range |
| `deleted_at` | server-managed, nullable; set by `DELETE`, never accepted in a request body |

Uniqueness is enforced by a **partial** index: `CREATE UNIQUE INDEX ux_lessons_slug ON lessons (slug) WHERE deleted_at IS NULL`. A plain unique index would make every soft-deleted lesson permanently squat on its slug, so an editor who deleted a lesson could never recreate one at the same address — and the error they would see, `SLUG_ALREADY_EXISTS` for a lesson that no read endpoint can find, is unresolvable from the interface (§2.10).

Any change to `slug` bumps `content_version` (§5.4.1).

### 7.5 CodeExample

| Field | Constraints |
|---|---|
| `lesson_id` | from the path, must exist |
| `language` | `@NotBlank`, member of the supported list |
| `code` | `@NotBlank @Size(max = 20000)`; LF-normalized; stored verbatim, not HTML-sanitized |
| `caption` | optional/nullable, `@Size(max = 300)` |
| `order` | optional, order range |

### 7.6 MindMap

| Field | Constraints |
|---|---|
| `root` | `@NotNull @Valid`; recursive validation |
| `root.id` (each node) | `@NotBlank @Pattern("^[a-z0-9][a-z0-9-]{0,63}$")`; unique within the map |
| `root.label` | `@NotBlank @Size(min = 1, max = 120)`; sanitized to plain text |
| `root.lesson_id` | optional/nullable; must reference a lesson in the same track |
| `root.children` | `@NotNull` (may be `[]`); depth ≤ 8; ≤ 500 nodes total |

Structural violations report `MIND_MAP_INVALID` (422) rather than `VALIDATION_FAILED`, because they are graph-level, not field-level. A node label carrying markup reports `UNSAFE_HTML` (422): a label is plain text by contract, rendered by interpolation, so the boundary asserts there is no markup in it rather than deleting any (§2.8).

### 7.7 ContentTranslation

| Field | Constraints |
|---|---|
| `entity_type` | enum `TRACK\|MODULE\|LESSON\|BLOG_POST` — `MIND_MAP` is not translatable in v1 (`ENTITY_TYPE_UNSUPPORTED`) |
| `entity_id` | must reference an existing entity of that type |
| `locale` | locale pattern, `en` rejected (`CANONICAL_LOCALE_NOT_ALLOWED`) |
| `title` | `@NotBlank @Size(max = 200)` |
| `body` | `@Size(max = 200000)`, sanitized like any markdown; **`@NotBlank` for `LESSON` and `BLOG_POST`, optional/nullable for `TRACK` and `MODULE`** (§5.6) |

The conditional `body` requirement is a class-level constraint that reads `entity_type`, since no single field annotation can express it.

Uniqueness: `(entity_type, entity_id, locale)`.

### 7.8 BlogPost

| Field | Constraints |
|---|---|
| `slug` | `@NotBlank`, slug pattern, unique across posts |
| `title` | `@NotBlank @Size(max = 200)` |
| `body_markdown` | markdown body rules; sanitized |
| `status` | server-managed; rejected in request bodies (transitions use the lifecycle endpoints) |
| `source` | server-assigned (`MANUAL` on manual creation, `AUTO` from the pipeline); immutable |
| `source_url` | `@URL` absolute `https://`, `@Size(max = 2000)`; **required and non-blank when `source = AUTO`** — enforced as a class-level constraint, not a field annotation, and again by a database check constraint. **Immutable for every role once `source = AUTO`** (§5.5.1 rule 7); a `PATCH` attempting to change it returns `403 AUTO_POST_NOT_EDITABLE` |
| `source_update_id` | nullable; non-null exactly when `source = AUTO`; immutable |
| `expected_status` | on transition endpoints: `@NotNull`, enum |
| `reason` | `@Size(min = 10, max = 500)`; required on `reject` and `unpublish` |

### 7.9 WhitelistSource

| Field | Constraints |
|---|---|
| `name` | `@NotBlank @Size(max = 120)`, unique |
| `feed_url` | `@NotBlank @Size(max = 2000)`, absolute `https://` |
| `verify_url_pattern` | `@NotBlank @Size(max = 2000)`, absolute `https://`, exactly one `{version}` placeholder |
| `enabled` | boolean, default `true` |

The version string substituted into `verify_url_pattern` is not a request field but is validated with the same rigour: `^[A-Za-z0-9._+-]{1,64}$` before interpolation, then percent-encoded as a path segment (§5.7). A value failing the pattern is never placed in a URL.

### 7.10 UserProgress (sync item)

| Field | Constraints |
|---|---|
| `lesson_id` | `@NotNull` UUID |
| `completed_at` | nullable `Instant`; a value more than 24 h ahead of server time is **clamped**, not rejected |
| `client_updated_at` | `@NotNull Instant`; a value more than 24 h ahead of server time is **clamped** to server time, and the item is processed with the clamped value (§5.8.1) |
| `items` | `@NotEmpty @Size(max = 500) @Valid` |

Clamping is service-layer behaviour, not Bean Validation: a future timestamp is not an invalid request, it is a correctable one, and rejecting it would lock a device with a wrong clock out of syncing permanently.

Uniqueness: `(user_id, lesson_id)`. Rows are never deleted by any API operation (§2.10).

---

## 8. Decisions taken

Every question this document once left open has been settled. They are recorded here with their reasoning, because the reasoning is what a future change has to argue against.

| # | Question | Decision | Where it lives |
|---|---|---|---|
| 1 | Do manifest and content-package endpoints authenticate? | **No — anonymous** | §3.7, §4 |
| 2 | Is full-text search in v1? | **No — deferred** | Appendix A |
| 3 | Password change? Password reset? | **Change yes, reset no** | §5.1.7, §8.1 |
| 4 | Are mind map labels translated? | **No, not in v1** | §5.2.4, §5.6 |
| 5 | Can a published post be withdrawn? | **Yes — `unpublish` returns it to `DRAFT`** | §5.5.1, §5.5.2 |
| 6 | Does deleting a lesson erase progress? | **No — lessons soft-delete, progress is never deleted** | §2.10, §7.4 |
| 7 | Can the pipeline be triggered manually? | **Yes — same service method, same lock, no bypass** | §5.7 |
| 8 | Can an `EDITOR` see the review queue? | **Yes, read-only; approve/reject stay `ADMIN`** | §4 |
| 9 | Are drafts addressable by slug? | **No — admin endpoints address by UUID** | §8.2 |
| 10 | Where does the web client keep its refresh token? | **Cookie for web, body for desktop; the response follows the request channel** | §3.8 |
| 11 | Identifier type shared with the content sync protocol? | **UUIDv7, in both documents** | §2.3 |

Three of these deserve their reasoning restated where an implementer will trip over them; the rest are explained at the section referenced above.

### 8.1 There is no password reset — the operator procedure that replaces it

Password *change* exists (§5.1.7). Password *reset* — "I forgot it, email me a link" — does not, and its absence is a deliberate scope boundary, not an oversight.

Reset requires outbound email: an SMTP relay or a delivery provider, a deliverability reputation, bounce handling, a signed single-use token with its own expiry and replay rules, and a rate-limit surface that is a favourite target for enumeration attacks. That is the first external service dependency in a platform whose entire operational design is a single Postgres database and no third-party runtime dependencies. It is a real feature with real infrastructure behind it, and it is not being smuggled in as a footnote to an API contract.

Until it exists, the recovery path is administrative:

1. The user contacts an administrator through a channel outside the application, and the administrator establishes who they are by whatever means the operator considers sufficient. This API provides no identity proof and no help with that step.
2. The administrator sets a new password hash directly in the database and revokes all of the user's refresh-token families in the same transaction.
3. The new password is communicated out of band and the user changes it immediately through `POST /auth/me/password`.

This is a manual, non-scaling, audit-free procedure and should be understood as such. It is acceptable while the user base is small and administrators know their users personally. It stops being acceptable at the point where support volume makes step 1 routine — and that point, not a sprint boundary, is when reset gets built. Building it later costs no rework here: reset is additive, touching no endpoint in this document.

### 8.2 Drafts are not addressable by slug

Public endpoints address content by slug; admin endpoints address it by UUID. Unpublished content therefore has no slug-based route at all, and authoring preview goes through `GET /admin/lessons/{id}`.

The tempting alternative is a `?preview=true` flag on the public route. It is rejected: it puts unpublished content exactly one query parameter away from anonymous exposure, and the entire protection then rests on one conditional inside a public handler. Draft content includes half-written lessons, embargoed release posts, and automated drafts that failed verification — precisely the material that must not leak — and a bug in that one conditional is indistinguishable from a bug anywhere else in a controller until it is found by someone outside the organisation.

Keeping the surfaces physically separate means the public handler contains no code path that can return a draft, whatever it is asked. The cost is that authoring previews carry UUIDs in their URLs, which is mildly inconvenient when authors share preview links internally. That is a fair price for a boundary that cannot be crossed by a typo.

### 8.3 What is not settled here

Two things remain genuinely open, and both are owned elsewhere:

- **Package size ceiling.** `CONTENT_PACKAGE_TOO_LARGE` exists in the catalogue, but the actual byte limit is a packaging decision belonging to the content sync protocol. This document raises the error; it does not choose the number.
- **Deployment origins.** §2.9 and §3.8 impose constraints — a same-site relationship between the web build and the API, and exact webview origin strings — that must be settled by whoever chooses the DNS names, before first deployment. They are deployment inputs to this contract, not decisions this contract can make.

---

## Appendix A — Reserved, not frozen

**Nothing in this appendix is part of v1.** It is not implemented, not agreed, and not stable. No client may be written against it, and no code in this appendix has the status the rest of this document has. It exists so that a future decision starts from a considered sketch instead of a blank page.

### A.1 `GET /api/v1/search`

Full-text search over published lessons and published blog posts, backed by a Postgres `tsvector` column and `ts_headline` snippets — no external search service, consistent with the platform's single-database design.

Deferred because the platform commits to Postgres for search but never specifies a search feature, its scope or its ranking, and because search's usefulness scales with library size: with a handful of tracks, browsing outperforms it. It costs a generated column, a GIN index and a migration, and it is additive — adding it later touches no existing endpoint, which is exactly why it is the right thing to cut first.

Sketch, should it be built:

Query: `q` (required, 2–120 chars), `type` (`lesson|blog`, repeatable, default both), `page`, `size`.

```json
{
  "items": [
    {
      "type": "LESSON",
      "id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
      "slug": "signals-and-reactivity",
      "title": "Signals and Reactivity",
      "snippet": "A <mark>signal</mark> is a value wrapper that records a dependency on whatever reads it.",
      "track_slug": "angular-path",
      "rank": 0.87
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 7,
  "total_pages": 1
}
```

Open design points, none of them decided: whether `snippet` may carry `<mark>` markup at all or should return match offsets instead (markup in a JSON field is a sanitization boundary a client must handle correctly, and one client already renders untrusted-looking HTML nowhere else); which text search configuration applies per locale, given that Postgres has no Turkish stemmer in its default distribution; and whether translated content is searchable in its own locale or only in English.

Errors it would use are all existing codes: `VALIDATION_FAILED`, `PAGE_SIZE_EXCEEDED`, `INVALID_PARAMETER`. Adding search introduces no new error code, which is a small argument that it fits the shape of the API as designed.
