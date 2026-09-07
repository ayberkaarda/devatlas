# 0004. Manifest and content-package download protocol

## Status

Accepted

## Context

The desktop client has to discover what content exists on the server,
download it, prove it arrived byte-for-byte intact, resume an interrupted
transfer without re-fetching what already arrived, and do all of this without
holding a session — because a session already lives in exactly one place (the
Angular layer; see `docs/protocol/rest-api.md` §3.7 and
`docs/protocol/tauri-commands.md` §1). `docs/protocol/content-sync.md` is the
frozen design that answers all of this; this ADR records why its central
choices were made rather than the alternatives that were actually on the
table, so a later reader does not have to re-derive them from the protocol
document's prose.

The one requirement every choice below serves is stated as the protocol's own
premise: "Identical content always produces an identical digest, on every
platform, in every process, forever" (`content-sync.md` §1). A digest is
computed once, in the service layer, at the moment `content_version` is
incremented, and is never re-derived by anything else: not a database
trigger, not a generated column, not a `DEFAULT` expression, not a backfill
`UPDATE` (§3.4). All four are ruled out for the same reason — they move the
computation somewhere the determinism tests cannot reach, and none of the
SQL functions available in Postgres perform the normalization the digest
depends on. Ahead of hashing, every markdown, title and label field is
normalized exactly once, at the write boundary that stores it: a leading
BOM is stripped, `\r\n` and lone `\r` become `\n`, and the result is put into
Unicode Normalization Form C (§3.1). Because normalization happens at write
time and not at hash time, the stored bytes, the hashed bytes and the served
bytes are provably the same bytes — a working tree checked out with CRLF line
endings and a continuous-integration checkout that produced LF both store,
and therefore hash, identical text, because the divergent line ending never
survives past the write.

## Options considered

### A. Package granularity: one package per entity, translations embedded, versus a manifest entry per locale

The protocol defines exactly two entity kinds, `LESSON` and `MIND_MAP`
(`content-sync.md` §2), and a lesson's package carries its English text in
the top-level fields plus a `translations` array holding "only the non-English
locales that actually exist" (§4.3). The local store mirrors this: a
`content_translations` row is keyed by `(entity_type, entity_id, locale)` but
carries `content_version` and `sha256` columns that hold *the owning
document's* version and digest, not one of its own — the schema comment in
`desktop/src-tauri/src/store.rs` states the reason directly: translations
"arrive inside the document that owns them... so a row carries the version
and digest of that document rather than one of its own."

The alternative actually available was to make a translation its own
manifest entry — its own `entity_id`, its own `content_version`, its own
`sha256`, downloadable and verifiable independently of the base-locale
content.

- Plus (rejected alternative): a client that only ever reads one locale could
  skip downloading the others, saving bytes for a single-locale user.
- Minus (rejected alternative): every translation would need its own
  version counter, its own digest, and its own place in the `entities` array
  that section 5's ordering rules and section 3.5's structural test would
  then have to cover — the "every field present in a package document...
  appears in the write-boundary version bump rules" test exists precisely
  because a field that is not enumerated can drift out of sync with the
  digest that is supposed to describe it, and a per-locale entity would
  quadruple the surface that rule has to hold over.
- Minus (rejected alternative): a lesson and its translations are edited
  together in practice — the same `AdminLessonService`/`TranslationService`
  write path bumps the owning entity's `content_version` — so a translation
  has no independent lifecycle to justify an independent identity in the
  protocol.

**Chosen:** one package per entity, all existing translations embedded. A
lesson is either held or not; there is no partial-locale download state to
represent, which keeps `Availability` in `docs/protocol/platform-service.md`
§4 a six-value enum with no seventh value for "downloaded, but only in some
locales." The per-locale byte cost is accepted because packages are
consistently described as "tens of kilobytes" (§3.2b) — small enough that
shipping every locale together is cheaper than the bookkeeping a finer grain
would cost.

### B. Track-level change signal: an entity-derived maximum versus the track's own counter

Section 4.1 states this explicitly as a choice that looked equivalent and
was not: a track's `content_version` could be computed as the maximum
`content_version` over its entities, or the server could maintain it as its
own counter, incremented whenever anything a client would need to re-read
changes — the track's own fields, a module created, deleted, renamed or
reordered, a lesson created, deleted, moved or reordered, any entity's own
version bumping, the mind map changing, or a track- or module-level
translation changing.

- Minus (the maximum): "Deleting a lesson lowers nothing and raises nothing,
  so the maximum stays where it was: the client sees an unchanged catalog,
  never fetches the track manifest, and never learns the lesson is gone."
  The same blindness covers every structural edit that does not touch an
  entity's body — a module rename, a reorder.
- **Chosen:** an own counter, bumped on every structural or content change
  listed above. This is the reason a deletion, a rename or a reorder is
  visible to a polling client at all: none of those operations touch an
  entity's own `content_version`, so only a counter that is not derived from
  the entities can register them.

### C. Manifest caching: unconditional re-fetch versus `ETag`/`304` revalidation

Both the catalog (§4.1) and the per-track manifest (§4.2) respond with an
`ETag` and honour `If-None-Match` with `304`. The per-track manifest is
additionally engineered for **byte stability** so that this caching is
meaningful at all: there is no `generated_at` field in the body (a
timestamp would make every generation differ and defeat the `ETag`
entirely), the generation time lives in the HTTP `Date` header instead, and
every array with no inherent order (`entities`, `modules`,
`modules[].lessons`, `translations`) is sorted by a fixed key (§5) so that
two generations of unchanged content produce identical bytes.

- Minus (unconditional re-fetch, the implicit alternative): the protocol's
  own delta-update loop (§8) polls every track the user holds content from,
  every time "update my library" runs; without conditional revalidation that
  is a full manifest body over the wire for every track, every time, even
  when nothing changed.
- **Chosen:** `ETag`/`304`, with `Cache-Control: no-cache` (not `immutable`)
  on manifests specifically because, unlike a package, "a manifest at a
  given URL changes" — revalidation must happen every time, but it can be
  made cheap. Packages, by contrast, are addressed by `(entity_id, version)`
  and therefore *are* immutable and cacheable forever (§4.3): the two
  endpoints get different caching postures because they have different
  mutability, not because one caching strategy was judged generally better.

### D. Resume: `Range`-based resume versus restart-from-zero or a bespoke chunk protocol

Packages are served with `Content-Encoding: identity` unconditionally,
specifically so `Range` works (§3.2b): "a range applies to the bytes of the
representation that was selected, so a client that received a compressed
first response and stored *N* decoded bytes cannot ask for `bytes=N-` and get
what it expects." The alternative of compressing packages was therefore
rejected outright, even though packages would compress well, because it
would break the resume mechanism the protocol is built around; the rule
that survives if compression is ever revisited is stated explicitly: "a
range request is always served as identity."

- Minus (restart-from-zero on any interruption): wastes every byte already
  transferred on a connection that merely dropped mid-body, which the queue
  state machine (§7) explicitly treats as a distinct, non-corrupting failure
  ("Truncation and corruption are different failures... A response that
  ended early... is incomplete, not wrong: the partial file is kept and the
  next attempt resumes with `Range`").
- Minus (a bespoke chunk protocol): would duplicate what HTTP `Range` and
  `If-Match` already provide (§7's `If-Match: "<sha256>"` on a resume request,
  which turns content republished mid-download into a clean `412` rather
  than a silently spliced file made of two different versions) for no
  benefit over the standard mechanism.
- **Chosen:** `Range`-based resume, with partial files kept in a
  `partials/` directory inside the application data directory — not the
  system temp directory, because the queue is required to survive a restart
  with its partial files intact and the OS is free to clean temp on its own
  schedule (§7).

### E. Version negotiation on a stale request: fail hard, silently serve latest, or `409` with re-planning

A client always states the `content_version` it intends to download (§4.3
— "a client always knows which version it intends to download, and omitting
it would let a manifest and a package silently disagree"). When that version
has since moved on, three responses were possible: refuse with a generic
error and make the client start its whole planning cycle over from a fresh
manifest fetch; silently serve whatever the current version is regardless of
what was asked for; or say precisely what happened and hand back enough
information to recover without another manifest round trip.

- **Chosen:** `409 CONTENT_VERSION_SUPERSEDED`, with the current version
  included in the body "so the client can re-plan without another manifest
  fetch" (§4.4). Silently serving a different version than the one requested
  would violate the addressing rule that `(entity_id, version)` names
  immutable content in the first place — the client would have no way to
  know its locally recorded digest no longer describes what it just
  downloaded.

### F. Session posture on the download surface: shared token, engine-specific token, or anonymous

This is the decision `content-sync.md` §12.1 and `tauri-commands.md` §1 both
call out as deliberate rather than incidental, and it is the one this ADR
weighs most carefully because "anonymous" sounds, out of context, like a
security regression rather than a considered trade-off.

- **Option: the Rust engine shares the Angular layer's token pair.**
  Rejected because refresh tokens are single-use and rotated (REST contract
  §3.3): "If both the Angular layer and the Rust engine held the session,
  each refresh would invalidate the other's copy, and every concurrent
  refresh would look exactly like the theft pattern in §3.4" — the reuse
  detection built to catch a stolen token would instead fire on the
  application racing itself, revoking the whole family and signing the user
  out for no fault of their own. `tauri-commands.md` §1 calls this
  "a synchronisation problem with no good solution," not a hard-but-solvable
  one.
- **Option: mint the engine its own, separate credential** (a second token
  type, or a long-lived engine-scoped key). Not adopted; no such mechanism
  exists in the frozen contract, and the reasoning that rules it out is the
  same one that makes anonymous access sufficient in the first place: a
  credential would be guarding content that is already served without one.
  §4's own framing states it plainly — "Authentication there would protect
  nothing... The same bytes that a package delivers are already readable
  without a token. A gate on the package endpoints would be a gate on a door
  standing beside an open wall," because §5.2 of the REST contract already
  grants anonymous callers the full lesson body and every code example.
- **Chosen: anonymous, rate-limited by IP.** `GET /manifest/**` and `GET
  /content/**` accept and require no credentials (REST contract §3.7,
  `content-sync.md` §4). Abuse is bounded per client IP rather than per
  account — 60 requests/minute on `/manifest/**`, 600/minute on
  `/content/**`, a ratio chosen because "a client reads a handful of
  manifests and then downloads many packages, and the download engine runs
  up to three concurrent transfers with resume" (REST contract §3.6). This
  keeps the session in exactly one place, the Angular layer, and reduces the
  Rust engine to "HTTP plus verification" (`tauri-commands.md` §1) — no
  token store, no refresh logic, no reuse-detection exposure at all on the
  Rust side.

## Decision

The protocol in `docs/protocol/content-sync.md` is adopted as specified:
canonical, sorted, whitespace-free JSON hashed once at the write boundary
after UTF-8/LF/NFC normalization (§3.1–3.4); one package per entity with
translations embedded rather than addressed separately (§2, §4.3); a track's
own change counter rather than a derived maximum (§4.1); `ETag`/`304`
revalidation on both manifest endpoints with a byte-stable per-track manifest
body to make that revalidation meaningful (§4.1–4.2); identity-encoded,
`Range`-resumable package transport with `If-Match` protecting a resume
against content that changed mid-download (§3.2b, §7); `409
CONTENT_VERSION_SUPERSEDED` with the current version attached, rather than a
silent substitution or a bare failure, when a requested version has moved on
(§4.4); and anonymous, IP-rate-limited manifest and content endpoints, driven
not by a judgment that the content needs no protection in the abstract, but
by the concrete fact that it already has none on the read API, combined with
the specific, structural reason a shared or engine-owned credential cannot
work under single-use token rotation.

## Consequences

### Positive

- A digest failure can only mean corruption or staleness, never "the server
  serialized the same content differently today" — which is what makes
  verification (§1) a meaningful signal rather than noise the client has to
  learn to ignore.
- The engine never participates in authentication at all: no token storage,
  no refresh scheduling, no exposure to the reuse-detection failure mode in
  REST contract §3.4. Its entire contract with the server is HTTP status
  codes, `Range`, `If-Match`, and a digest.
- A resumed, paused, or restarted download costs at most the bytes actually
  lost to a real failure — a dropped connection resumes from where it
  stopped; a corrupted body is discarded and retried from zero; a
  version that moved on triggers a `409` and a re-plan — never a full
  re-download for a network hiccup.
- Deletion, rename and reorder are all visible to a polling client, because
  the track counter is independent of any entity's own version.

### Negative

- Published content is unconditionally public to anyone who can reach the
  server, including a user who never registered. This was already true of
  the read API before this protocol existed; this decision extends the same
  posture to the download surface rather than introducing it, but it is
  worth stating as a consequence rather than leaving it implicit: there is
  no way, within this protocol, to gate a track behind a login without
  first closing that door on `docs/protocol/rest-api.md` §5.2 — a change
  this document explicitly defers to that surface ("Bolting a token onto the
  download path while §5.2 stays open would be theatre," §3.7).
- Embedding every translation in one package means a single-locale reader
  downloads bytes for locales they never render. Accepted because measured
  package sizes are "tens of kilobytes" (§3.2b), not because the cost was
  found to be zero.
- Historical package versions are not retained (§4.4); a client whose local
  copy is several versions behind cannot request an intermediate version to
  diff against — it always re-fetches the manifest and the current package,
  which is judged cheap enough that history was not worth the storage.

### Follow-up

- Section 3.5's required test list is the acceptance bar for the server
  implementation and is not restated here; this ADR's job is the "why," the
  protocol document's §3.5 remains the checklist.
- If content is ever restricted to signed-in users, that is a change to
  `docs/protocol/rest-api.md` §5.2 first and this protocol's endpoints
  second, per §3.7 — not a token bolted onto `/manifest/**` and
  `/content/**` in isolation.
