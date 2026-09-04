# Content Sync Protocol

**Status: proposed.** This document is a design gate. Nothing described here is
implemented until it has been reviewed and approved, and the implementation is
expected to match it rather than diverge and be documented afterwards.

Scope: how the server publishes content, how a desktop client discovers what has
changed, downloads it, proves it arrived intact, and stores it for offline use.

Not covered here: authentication, admin CRUD, blog editing and the shared error
envelope. Those live in `rest-api.md`. This document uses the same error envelope
(`{ "code": ..., "message": ... }`) and does not redefine it.

---

## 1. The guarantee this protocol exists to make

> Identical content always produces an identical digest, on every platform, in
> every process, forever.

Everything below follows from that sentence. A client that downloads a package
computes its SHA-256 and compares it against the digest the manifest promised. If
the two can disagree for any reason other than corruption — a different line
ending, a different key order, a different Unicode encoding of the same
character — then verification is meaningless and the client cannot distinguish a
truncated download from a server that simply serialized a map differently today.

That is why section 3 is the longest one, and why hashing is specified as an
exact byte-level procedure rather than "hash the JSON".

---

## 2. Vocabulary

| Term | Meaning |
|---|---|
| **Entity** | An independently downloadable unit of content. Exactly two kinds exist: `LESSON` and `MIND_MAP`. |
| **Package** | The complete offline payload for one entity, as a single JSON document. A lesson package carries its body, its code examples and every translation that exists for it. |
| **`content_version`** | A monotonically increasing integer per entity, incremented by the server whenever the entity's published content changes. Never decreases, never reused. |
| **Digest** | The SHA-256 of a package's canonical bytes, lowercase hex, 64 characters. |
| **Manifest** | A listing of entities with their `content_version`, digest and size. |
| **Catalog** | The top-level manifest: one summary row per published track. |

Modules and tracks are structure, not entities: they are described inside a track
manifest but are not downloaded separately, because they carry no content of their
own beyond a title and an ordering.

---

## 3. Determinism rules — normative

A package's digest is computed over the **canonical byte encoding** of the package
document, produced by the following procedure. The server computes it in the
service layer when `content_version` is incremented, and writes it to a column.
The client recomputes it over the bytes it received.

### 3.1 Text normalization happens once, at the write boundary

Text is normalized **when it is stored**, never when it is hashed. One routine,
called from the write path, is the single place this happens; the REST API
document references the same routine. Hashing then operates on the stored string
exactly as it is.

This ordering is the point. If normalization happened at hash time, the stored
text and the hashed text would be different strings, and the server would have to
re-derive the second from the first on every read — reintroducing exactly the
"two implementations of one rule" problem the digest is supposed to rule out. With
normalization at the write boundary, **stored bytes, hashed bytes and served bytes
are the same bytes**, and that identity is what makes verification meaningful.

The routine, applied to every markdown, title and label field on write:

1. Strip a leading U+FEFF byte order mark.
2. Normalize line endings: `\r\n` → `\n`, then any remaining lone `\r` → `\n`.
3. Apply **Unicode Normalization Form C (NFC)**.

Rule 2 is not housekeeping. Content authored on Windows and content authored on
Linux differ by a byte per line for identical text, and a checkout can rewrite line
endings in either direction. Without it the same lesson hashes differently
depending on which machine last touched it, and the failure appears only on the
platform that did not produce the manifest.

Rule 3 is a content-quality rule rather than a determinism requirement: `é` can be
one code point or two and both render identically, so two authors on two keyboards
would otherwise produce two entries that look the same and compare unequal. It is
**not** applied to `code` fields, where an author's exact bytes are the content.

**What the routine deliberately does not do:** it does not strip trailing spaces,
and it does not strip trailing newlines. Two trailing spaces are a hard line break
in Markdown, and removing them silently changes how a lesson renders. A digest that
is stable because it quietly corrupted the content is worse than no digest.

### 3.2 Canonical JSON

1. UTF-8 encoding, no BOM.
2. Object keys sorted by Unicode code point, ascending.
3. No insignificant whitespace: no spaces after `:` or `,`, no newlines, no
   trailing newline at the end of the document.
4. Strings escaped minimally: only `"`, `\` and the control characters below
   U+0020 are escaped, using the short forms (`\n`, `\t`, …) where one exists and
   `\u00XX` otherwise. Non-ASCII characters are emitted literally, never as
   `\uXXXX`.
5. Integers only. The protocol defines no floating-point field, because decimal
   formatting is not portable enough to hash.
6. Arrays preserve their declared order. Where an array's order is not
   semantically meaningful it is sorted by the field named in section 5.
7. `null` is never emitted. An absent value is an absent key.

### 3.2b Transport encoding

Package responses are always `Content-Encoding: identity`. Manifests may be
gzipped by the servlet container.

The reason is `Range`. A range applies to the bytes of the representation that was
selected, so a client that received a compressed first response and stored *N*
decoded bytes cannot ask for `bytes=N-` and get what it expects. Packages are tens
of kilobytes; the compression saved across a whole track does not justify a class
of resume bug that only appears on interrupted downloads. Identity also makes
`Content-Length` equal `size_bytes` exactly, which lets the engine detect
truncation before it bothers hashing.

If compression is ever enabled on packages, the rule that survives is: **a range
request is always served as identity.**

### 3.3 What is hashed

The digest covers the canonical bytes of the **package document**, excluding any
transport framing. `Content-Encoding` (gzip, brotli) does not affect it: the
client decompresses first, then hashes.

The digest is **not** computed over the database row, the response body as
transmitted, or a concatenation of fields.

**The canonical bytes are produced once, at write time, and stored.** When a write
increments `content_version`, the same transaction serializes the package, stores
those exact bytes alongside their digest and length, and the content endpoint
returns them verbatim — it does not re-serialize on read.

Serializing twice, once for the manifest and once for the response, is the
obvious-looking design and it is subtly wrong: the two runs happen against
different database states, so any bug that leaves a row updated without its
owner's version bumped produces a response whose digest does not match the
manifest that advertised it, on every client at once. Storing the bytes makes
that class of bug structurally impossible, and it also settles where the
manifest's `size_bytes` comes from — it is the stored length, not an estimate.

### 3.4 Where hashing does not belong

Digests are never computed by a database trigger, a generated column, a
`DEFAULT` expression, or a backfill `UPDATE`. All four move the computation
somewhere the determinism tests cannot reach, and the SQL functions available for
it do not perform the normalization in 3.1 at all.

### 3.5 Required tests

The implementation is not accepted without these:

| Test | Asserts |
|---|---|
| Same content, two generations | Identical digest |
| Text stored as CRLF and as LF | Identical stored text, therefore identical digest |
| Text stored with and without a BOM | Identical stored text, therefore identical digest |
| NFC and NFD forms stored | Identical stored text, therefore identical digest |
| Markdown with two trailing spaces on a line | Those spaces **survive** storage and hashing |
| Object built with keys in two different insertion orders | Identical digest |
| Adding a translation | Digest changes **and** `content_version` increments |
| Every field present in a package document | Appears in the write-boundary version bump rules |

The last row is a structural test, not a content one, and it exists because of a
concrete failure: a lesson `slug` appears in the package but was initially absent
from the bump rules. Renaming a slug would then change the package bytes without
incrementing `content_version`, leaving the stored digest describing content that
no longer exists — and every client would fail verification three times and give
up. The test enumerates the package schema's fields and asserts each one is
covered by a bump rule, so the next field added cannot reintroduce this.

---

## 4. Endpoints

Base path `/api/v1`. All responses are `application/json;charset=UTF-8`.

**These endpoints require no authentication.** Published lesson bodies are already
served anonymously through the read API — the web client is a public site whose
content is meant to be indexed — so requiring a token here would protect nothing
while forcing the download engine to own a session. That matters more than it
sounds: refresh tokens are single-use and rotated, so the engine and the UI cannot
both hold one without one of them triggering reuse detection and logging the user
out. Leaving these endpoints anonymous keeps the session in exactly one place, the
Angular layer, and reduces the Rust engine to HTTP plus verification.

Requiring an account at first launch is a product decision about progress, which
belongs to a user; the content replica does not. Signing in as a different user on
the same machine therefore does not discard downloaded content.

Abuse is handled with per-IP rate limits rather than authentication: 60 requests
per minute on `/manifest/**` and 600 on `/content/**`. The engine already treats
`429` as a wait rather than a failure.

### 4.1 `GET /manifest/catalog`

One row per published track. This is what the client polls to decide whether
anything is worth looking at.

```json
{
  "generated_at": "2026-09-04T09:12:44.117Z",
  "tracks": [
    {
      "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
      "slug": "angular-path",
      "title": "The Angular Path",
      "content_version": 47,
      "lesson_count": 32,
      "total_size_bytes": 1893441,
      "updated_at": "2026-09-03T18:22:10.904Z"
    }
  ]
}
```

A track's `content_version` is **its own counter**, not a maximum over its
entities. The server increments it in the service layer whenever anything a client
would need to re-read changes: the track's own fields, a module created, deleted,
renamed or reordered, a lesson created, deleted, moved between modules or
reordered, any entity inside it bumping its own version, the mind map changing,
or a track- or module-level translation changing.

A maximum would look equivalent and is not. Deleting a lesson lowers nothing and
raises nothing, so the maximum stays where it was: the client sees an unchanged
catalog, never fetches the track manifest, and never learns the lesson is gone.
The same blindness covers module renames, reordering, and every structural edit
that does not touch an entity's body. An own counter changes on all of them.

`total_size_bytes` is the sum of the entity package sizes, so a client can show
"about 1.8 MB" before committing the user to a download.

Caching: responds with `ETag` and honours `If-None-Match` with `304`.

### 4.2 `GET /manifest/track/{trackId}`

The full manifest for one track. It carries two things that serve two different
readers: a **structural summary** the UI renders, and an **entity list** the
download engine consumes.

```json
{
  "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
  "slug": "angular-path",
  "content_version": 47,
  "title": "The Angular Path",
  "description": "Signals, routing and the modern component model.",
  "icon": "angular",
  "translations": [
    {
      "locale": "tr",
      "title": "Angular Yolu",
      "body": "Sinyaller, yönlendirme ve modern bileşen modeli."
    }
  ],
  "modules": [
    {
      "module_id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
      "title": "Signals and reactivity",
      "order": 1,
      "estimated_minutes": 90,
      "translations": [{ "locale": "tr", "title": "Reaktivite Temelleri" }],
      "lessons": [
        {
          "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
          "slug": "signals-and-reactivity",
          "title": "Introduction to signals",
          "difficulty": "INTERMEDIATE",
          "estimated_minutes": 25,
          "order": 1,
          "translations": [{ "locale": "tr", "title": "Sinyallere giriş" }]
        }
      ]
    }
  ],
  "entities": [
    {
      "entity_type": "LESSON",
      "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
      "content_version": 12,
      "sha256": "9f2c4e6a8b0d1f3579ace0246813579bdf02468ace13579bdf02468ace13579b",
      "size_bytes": 41233
    },
    {
      "entity_type": "MIND_MAP",
      "entity_id": "018f3b40-2a83-7e19-8f77-1c5b9e04a6d2",
      "content_version": 5,
      "sha256": "1a3c5e7092b4d6f8012345678abcdef0123456789abcdef0123456789abcdef0",
      "size_bytes": 8117
    }
  ]
}
```

**Why the structural summary is here at all.** The download UI lets a user pick a
single lesson, and a user picks a lesson by its name. An entity entry carries an
identifier and a digest — nothing a person can read. Without titles in the
manifest a desktop client could only render a list of UUIDs, or fall back to
calling the REST API, which would mean two data paths for the same screen and a
network requirement on a feature whose entire purpose is working offline.

The same reasoning covers translations: track and module titles are translatable,
but no package carries them, so without them here a desktop client would show
English structure around Turkish lessons while the web client showed both in
Turkish.

`entities` remains a flat list of exactly the five fields the engine needs, so
section 6's "one engine, three granularities" still holds: a module download is
the entity entries for `modules[].lessons[].lesson_id`, and nothing about the
engine changes.

**Byte stability.** There is no `generated_at` field. A timestamp in the body would
make every generation differ, which would defeat both the `ETag` below and any
claim that the manifest is reproducible. The generation time is available in the
HTTP `Date` header, where it belongs.

Ordering is fixed so that two generations of unchanged content produce identical
bytes: `entities` by `entity_type` then `entity_id`, `modules` by `order`,
`lessons` by `order`, `translations` by `locale`.

Caching and errors:

```
ETag: "<sha256 of the canonical manifest bytes>"
Cache-Control: no-cache
```

`no-cache`, not `immutable`: unlike a package, a manifest at a given URL changes.
The client always revalidates, and `If-None-Match` makes that revalidation cheap.

| Situation | Response |
|---|---|
| Track exists and is published | `200`, or `304` when `If-None-Match` matches |
| Track unpublished, deleted, or unknown | `404` `TRACK_NOT_FOUND` |
| Rate limit exceeded | `429` `RATE_LIMITED` with `Retry-After` |

A `404` here is **not** treated as data loss by the client. Every entity the user
holds from that track is marked withdrawn, exactly as in section 8, and nothing is
deleted.

### 4.3 `GET /content/{entityType}/{entityId}?version=N`

The package. `version` is **required** — a client always knows which version it
intends to download, and omitting it would let a manifest and a package silently
disagree.

`version` here is the entity's `content_version`, the counter that tracks content
revisions. It is not the optimistic-locking `version` the write API uses to detect
concurrent edits. The two are different numbers with the same name, and the query
parameter's spelling is fixed by the product specification; nothing in this
protocol ever refers to the locking one.

`entityType` is lowercase in the path — `lesson` and `mind_map` — while the same
value appears as `LESSON` and `MIND_MAP` inside every JSON body, matching the
enum convention used across the API.

Response headers:

```
ETag: "9f2c4e6a8b0d1f3579ace0246813579bdf02468ace13579bdf02468ace13579b"
Content-Type: application/json; charset=utf-8
Cache-Control: public, max-age=31536000, immutable
```

`(entity_id, version)` addresses immutable content, so it can be cached forever.
The `ETag` is the digest, quoted per RFC 9110. It is a strong validator: it
identifies exact bytes, and any transformation that changes those bytes changes
the digest.

Lesson package:

```json
{
  "code_examples": [
    {
      "caption": "A writable signal",
      "code": "const count = signal(0);\ncount.set(1);",
      "language": "typescript",
      "order": 1
    }
  ],
  "content_version": 12,
  "difficulty": "INTERMEDIATE",
  "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "entity_type": "LESSON",
  "estimated_minutes": 25,
  "module_id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
  "order": 3,
  "slug": "signals-basics",
  "translations": [
    {
      "body_markdown": "# Sinyaller\n\nBir sinyal, okunduğunda...",
      "locale": "tr",
      "title": "Sinyallere giriş"
    }
  ],
  "body_markdown": "# Signals\n\nA signal is a value that...",
  "title": "Introduction to signals"
}
```

The keys above appear in canonical (sorted) order deliberately: this is what the
hashed bytes look like, not a pretty-printed illustration.

English is the canonical language and lives in the top-level `title` and `body`.
`translations` carries only the non-English locales that actually exist. A missing
locale is not an error and not an empty entry — it is simply absent, and the
client falls back to English and marks it as untranslated.

Mind map package:

```json
{
  "content_version": 5,
  "entity_id": "018f3b40-2a83-7e19-8f77-1c5b9e04a6d2",
  "entity_type": "MIND_MAP",
  "root": {
    "children": [
      {
        "children": [],
        "label": "Signals",
        "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
        "id": "n2"
      }
    ],
    "label": "The Angular Path",
    "id": "n1"
  },
  "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10"
}
```

`lesson_id` is optional on a node and points at a lesson in the same track. A node
referencing a lesson the client has not downloaded is a normal state, not an
error: the UI offers to fetch it.

### 4.4 Version negotiation

| Situation | Response |
|---|---|
| Requested version is current | `200` with the package |
| Requested version is older than current | `409` `CONTENT_VERSION_SUPERSEDED`, with the current version in the body so the client can re-plan without another manifest fetch |
| Requested version is newer than current | `404` `LESSON_NOT_FOUND` / `MIND_MAP_NOT_FOUND` |
| Entity does not exist or is not published | `404` `LESSON_NOT_FOUND` / `MIND_MAP_NOT_FOUND` |

Not-found codes are per entity type, matching the shared catalogue in
`rest-api.md`; this protocol does not introduce a generic content-not-found code.
Two codes this protocol **does** add to that catalogue:

| Code | Status | Meaning |
|---|---|---|
| `CONTENT_VERSION_SUPERSEDED` | 409 | A version older than current was requested |
| `CONTENT_CHANGED_DURING_RESUME` | 412 | `If-Match` on a Range request no longer matches |

Only the current version of an entity is served. Historical versions are not
retained: a client whose manifest has gone stale re-fetches the manifest, which is
cheap, rather than the server storing every past package, which is not.

---

## 5. Ordering rules

Any array whose order is not semantically meaningful is sorted before hashing, so
that a database returning rows in a different order cannot change a digest:

| Array | Sorted by |
|---|---|
| `entities` | `entity_type`, then `entity_id` |
| `translations` | `locale` |
| `code_examples` | `order`, then `caption` |
| `modules` | `order`, then `module_id` |
| `modules[].lessons` | `order`, then `lesson_id` |
| mind map `children` | declared order — this one **is** semantic and is preserved |

---

## 6. Download granularity

Three levels, one engine. The UI offers a lesson, a module, or a whole track; all
three resolve to a list of `(entity_type, entity_id, content_version, sha256,
size_bytes)` tuples from the track manifest and enqueue them. There is no separate
code path per level, because there is no behaviour that differs between them.

| Level | Resolves to |
|---|---|
| Lesson | That lesson's entity entry |
| Module | Every lesson listed in `modules[].lessons` |
| Track | Every entry in `entities`, mind map included |

Enqueuing an entity already stored at the same `content_version` is a no-op, so
downloading a module and then its track does not re-fetch anything.

---

## 7. Queue and state machine

The queue lives in the client's SQLite store and survives restarts.

```
                 ┌──────────┐
   enqueue ─────>│  QUEUED  │<──────────────── resume ────────────┐
                 └────┬─────┘                                     │
                      │ a slot is free (max 3 concurrent)         │
                      v                                           │
               ┌─────────────┐    pause                      ┌────┴────┐
               │ DOWNLOADING ├──────────────────────────────>│ PAUSED  │
               └──────┬──────┘                               └─────────┘
                      │ bytes complete                            ^
                      v                                           │
                ┌───────────┐    pause ────────────────────────────┘
                │ VERIFYING │
                └─────┬─────┘
            digest ok │       digest mismatch / transport error
                      │              │
                      v              v
                  ┌──────┐     attempts < 3 ? ──yes──> QUEUED (backoff)
                  │ DONE │              │
                  └──────┘              no
                                        v
                                   ┌────────┐
                                   │ FAILED │
                                   └────────┘
```

Rules:

- **Concurrency is capped at 3 in-flight packages.** The cap is on the queue, not
  per track, so starting a second track download does not multiply connections.
- **Partial files live in a `partials/` directory inside the application data
  directory**, named by `(entity_id, version)` — not in the system temp directory.
  The queue is required to survive a restart with its partial files intact, and
  the system temp directory offers no such guarantee: it is cleaned by the
  operating system on a schedule nobody controls. A resume that finds its bytes
  gone is not a correctness failure, but it silently discards work the user
  already paid for on a metered connection.
  There is no permanent package file: once a package verifies, it is parsed and
  written into the replica tables, and the temp file is deleted.
- **Verification, replica write and `DONE` are one SQLite transaction.** A crash
  between verifying and inserting must not be able to leave an entity marked
  `DONE` with no rows behind it — that state would satisfy every future delta
  comparison while the lesson does not actually exist locally.
- **Truncation and corruption are different failures.** A response that ended
  early (`received < size_bytes`) is incomplete, not wrong: the partial file is
  kept and the next attempt resumes with `Range`. A response that arrived in full
  and failed verification (`received == size_bytes`, digest mismatch) is wrong:
  the partial is deleted and the next attempt starts from zero, because resuming
  would splice new bytes onto known-bad ones. More bytes than the manifest
  promised (`received > size_bytes`) means the manifest is stale — discard and
  re-plan.
- **Three attempts**, then `FAILED`. Backoff is exponential with jitter, starting
  at 2s.
- **Being offline does not consume attempts.** A failure to reach the server at
  all — DNS failure, connection refused, connect timeout — is not an attempt. The
  engine enters an offline state, stops scheduling, and waits until a probe
  (`GET /manifest/catalog` with `If-None-Match`) succeeds.
  Without this rule the three attempts and their backoff elapse in about fourteen
  seconds, so a laptop opened after a week away would mark its entire queue
  `FAILED` before the user finished logging in. Attempts exist to bound retries
  against a server that answers badly, not against a network that is not there.
- **A response that arrives and is wrong does consume an attempt**: `5xx`, a
  digest mismatch, or a connection dropped mid-body.
- **An unexpected `4xx` fails immediately, without retries.** `401`, `403`, `400`,
  `405` and `415` are configuration errors, not transient ones, and three attempts
  cannot fix one. The entity goes straight to `FAILED` carrying the server's
  `code`, or `UNEXPECTED_RESPONSE` if the body cannot be parsed. The rest of the
  batch is unaffected.
- **Resume uses HTTP `Range`.** On restart, an entry in `DOWNLOADING` is treated as
  `QUEUED` with its partial file intact; the engine issues
  `Range: bytes=<partial-size>-`. If the server answers `200` instead of `206`, the
  partial file is discarded and the download restarts from zero.
- **An entry left in `VERIFYING` by a restart is re-verified, not re-downloaded.**
  The bytes are complete by definition — that is what the state means — so the
  digest is recomputed over the existing temp file. It either passes and completes
  normally, or fails and follows the mismatch path. Re-downloading a complete file
  because the process died at the wrong moment would waste the transfer that
  already succeeded.
- **`If-Match` on resume.** A Range request carries `If-Match: "<sha256>"` so that
  content republished between attempts produces `412` rather than a silently
  spliced file made of two different versions. `412` discards the partial and
  re-plans against a fresh manifest.
- **`PAUSED` carries a `pause_reason`: `USER` or `INSUFFICIENT_STORAGE`.** The
  partial file is kept either way. A full disk pauses every non-terminal entry
  without consuming attempts — retrying into a disk that is still full would burn
  the queue for a condition only the user can clear — and resuming is a user
  action in both cases.
- **`FAILED` is terminal until the user retries.** A failed entry keeps its last
  error code so the UI can explain what happened.

---

## 8. Delta update

"Update your library" compares what is stored against a freshly fetched manifest.

```
for each track the user has any content from:
    fetch /manifest/track/{id}            (If-None-Match; 304 means done)
    for each entity in the manifest:
        local = stored version of entity, or absent
        if absent:                 skip        # not downloaded; not an update
        if local <  manifest:      enqueue     # changed
        if local == manifest:      skip
        if local >  manifest:      flag        # see below
    for each stored entity not present in the manifest:
        mark as withdrawn
```

Only entities the user already has are updated. A delta never grows the library:
downloading new content is a deliberate action, not a side effect of updating.

**`local > manifest` should be impossible** — versions only increase. If it
happens, the store is ahead of the server, which means either a restored backup or
a server rollback. The client does not delete anything; it flags the entity, keeps
serving the local copy, and surfaces it. Silently overwriting the user's data on
the strength of an anomaly is worse than a warning.

**Withdrawn entities** (present locally, absent from the manifest — unpublished or
deleted upstream) are kept on disk and marked. The user is told and can remove
them. Progress data is never touched by any of this.

The UI reports the result as a count: "3 lessons updated". The count is of
entities, not bytes, because that is what the user recognizes.

---

## 9. Progress events

Rust emits `download://progress` to the frontend. Emission is throttled to at most
one event per 250 ms per entity, plus one final event on any state change, so a
fast connection cannot flood the UI thread.

```json
{
  "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
  "entity_type": "LESSON",
  "state": "DOWNLOADING",
  "received_bytes": 20480,
  "total_bytes": 41233,
  "attempt": 1,
  "batch": {
    "batch_id": "018f3c55-7b1e-7d02-a933-0e84f2617b59",
    "completed_entities": 4,
    "total_entities": 32,
    "received_bytes": 512000,
    "total_bytes": 1893441
  },
  "error_code": null
}
```

`batch` describes the user-visible operation that enqueued this entity (one
lesson, one module, one track), so the UI can render both a per-item bar and an
overall one without tracking state itself. `error_code` is non-null only in
`FAILED`, and carries a code from section 11.

`total_bytes` comes from the manifest, not from `Content-Length`, so a percentage
is available before the response headers arrive.

---

## 10. Local store

The client keeps a read replica of published content plus its own operational
tables. Schema versioning uses SQLite's `user_version` pragma with an ordered,
append-only list of migrations.

| Table | Holds |
|---|---|
| `tracks`, `modules`, `lessons`, `code_examples`, `mind_maps`, `content_translations` | The replica, each content row carrying `content_version` and `sha256` |
| `download_queue` | One row per enqueued entity: state, attempts, partial path, batch id, last error |
| `user_progress` | `user_id`, `lesson_id`, `completed_at` (nullable and meaningful), `client_updated_at`, `sync_state` ∈ {`PENDING`, `SYNCED`, `ORPHANED`} |
| `app_settings` | A key-value store: locale, theme, the preferences dirty marker, the last successful sync time, and the stored session |
| `sync_log` | What was reconciled with the server and when |

Two details in `user_progress` are load-bearing.

It is keyed by `user_id` as well as `lesson_id`, because the replica is not tied to
an account: two people sharing one installation keep separate progress over the
same downloaded content, and signing in as someone else does not discard what was
downloaded.

`sync_state` is what stops a batch growing forever. A row the server rejects —
usually because its lesson has since been deleted — becomes `ORPHANED` and is never
sent again. Without it, a client that accumulated writes offline would carry the
same rejected row in every future batch for the life of the installation. The row
is kept either way; progress is never deleted.

A `completed_at` of null is a value, not an absence: it records "explicitly marked
incomplete" and has to survive a sync round trip as such.

The store lives in the platform's application data directory, resolved at runtime.
It is never placed relative to the working directory or inside the installation
directory.

Deleting downloaded content removes replica rows and files. **It never touches
`user_progress`** — a user who deletes a track to reclaim space and downloads it
again later finds their completed lessons still marked.

---

## 11. Error scenarios

Two kinds of code appear in a progress event's `error_code`, and implementers need
to be able to tell them apart. **Server codes** arrive in a response body and come
from the shared catalogue. **Client codes** are produced locally by the download
engine; they never appear in an API response, and looking for them in the
catalogue is wasted effort.

Server codes:

| Situation | Client behaviour | Code |
|---|---|---|
| `5xx` from the server | Counts as an attempt, retried | `INTERNAL_ERROR` / `SERVICE_UNAVAILABLE` |
| `429` | Honours `Retry-After`; does not consume an attempt | `RATE_LIMITED` |
| `409` version superseded | Entry re-planned against the current version; not an attempt | `CONTENT_VERSION_SUPERSEDED` |
| `404` on a manifested entity | Terminal for that entity; the manifest is stale | `LESSON_NOT_FOUND` / `MIND_MAP_NOT_FOUND` |
| `412` on a resume | Partial discarded, re-planned | `CONTENT_CHANGED_DURING_RESUME` |

Client codes:

| Situation | Client behaviour | Code |
|---|---|---|
| Server unreachable (DNS, refused, connect timeout) | Engine goes offline, stops scheduling, waits for a probe. **No attempt is consumed** and the entry never reaches `FAILED` for this reason alone | `NETWORK_UNAVAILABLE` |
| Connection dropped mid-body | Partial kept, resumed with `Range`. **Consumes an attempt** — the server answered, the transfer failed | `NETWORK_UNAVAILABLE` |
| Response ended cleanly but short of `size_bytes` | Partial kept, resumed. Consumes an attempt | `UNEXPECTED_RESPONSE` |
| Digest mismatch | Partial deleted, retried from zero | `DIGEST_MISMATCH` |
| Disk full | Whole batch pauses, user is told; nothing is deleted | `INSUFFICIENT_STORAGE` |

A local store that cannot be opened is deliberately **not** in this table. It is
not a download outcome: nothing can be enqueued in the first place, so it surfaces
as an error from the command that was called, not as a progress event about an
entity that never entered the queue.

A manifest fetched at time T describes the server at time T. Anything downloaded
afterwards may already be stale, and the protocol treats that as normal: the
digest check catches it, and `409`/`412` name it precisely.

---

## 12. Decisions taken

Every question this document opened has been answered. They are recorded here
because the reasoning matters more than the outcome, and because a later reader
will otherwise re-litigate them.

1. **Authentication on content endpoints — none.** Published lesson bodies are
   already served anonymously through the read API, so a token here would guard
   content that is reachable without one. The deciding factor was not access
   control but session ownership: refresh tokens are single-use and rotated, so
   the download engine and the UI cannot both hold one without triggering reuse
   detection and signing the user out. Anonymous endpoints keep the session in the
   Angular layer alone and reduce the Rust engine to HTTP plus verification. Abuse
   is bounded by per-IP rate limits instead. See section 4.

2. **Compression — packages are always identity, manifests may be gzipped.** A
   range applies to the selected representation, so a client holding *N* decoded
   bytes of a compressed response cannot resume correctly. The compression saved
   on tens of kilobytes does not pay for a resume bug that appears only on
   interrupted downloads. See section 3.2b.

3. **Package size ceiling — 2 MiB of canonical bytes, enforced on write.** The
   check happens in the same service method that produces the canonical bytes and
   the digest, so the length is already in hand. Enforcing it during manifest
   generation instead would let a successful editorial save silently drop a lesson
   out of a track later, with the author long gone; enforcing it on write means the
   author sees `422 CONTENT_PACKAGE_TOO_LARGE` while they are still editing.

4. **Identifier format — UUIDv7.** Shared with `rest-api.md`, and the examples in
   the two documents describe the same entities with the same identifiers on
   purpose. UUIDv7 is a published standard rather than a community convention,
   PostgreSQL stores it natively in sixteen bytes, and Java maps it to
   `java.util.UUID` without a converter. It is time-ordered like a ULID, so the
   ordering in section 5 stays stable and index locality is preserved. The cost is
   ten more characters per identifier in JSON.

5. **Withdrawn content — kept indefinitely.** The replica marks `withdrawn_at` and
   clears it if the entity reappears in a manifest; only the user deletes it. This
   follows the same principle as the anomaly rule in section 8: text costs almost
   nothing to keep, and a reader who loses a lesson they were halfway through has
   lost something that mattered to them. Adding expiry later is a client-only
   change.

6. **Mind map labels are not translatable in v1.** `ContentTranslation` is shaped
   as `(title, body)`, and a mind map has many labels rather than one body; there
   is no defined way to carry them, and packing JSON into `body` would not survive
   sanitization. `MIND_MAP` is therefore not a translatable entity type for now,
   and clients render English labels with the same "not yet translated" treatment
   they already apply elsewhere. Adding per-node label maps later is additive, but
   it touches the node schema, the admin editor, the digest and the validation
   rules at once, which is why it is not being done alongside everything else.
