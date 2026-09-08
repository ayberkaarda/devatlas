# ByteLore

**An offline-first learning platform for programming languages and frameworks.**
Lessons you can read on a plane, code examples that were actually executed before
they shipped, interactive mind maps, and a blog that no machine can publish to on
its own.

> 🇬🇧 English · [🇹🇷 Türkçe](README.tr.md)

---

## Status

The server, the desktop client and the website are feature-complete and green across
all three continuous-integration lanes, and the desktop application is packaged for
Windows and Linux on every push to `main`.

**The library is written but not yet published.** All seventeen tracks are in the
repository and load into the database on every build, and every one of them is waiting
on a read-through before the migration that publishes it is committed. That is the
design working as intended rather than a gap: see
[How content gets in](#how-content-gets-in).

No release has been cut, and there is no hosted instance yet.

---

## Contents

- [What it is](#what-it-is) · [The library](#the-library) · [How content gets in](#how-content-gets-in)
- [Architecture](#architecture) · [The offline model](#the-offline-model) · [Design invariants](#design-invariants)
- [Getting started](#getting-started) · [Running the checks](#running-the-checks)
- [Repository layout](#repository-layout) · [Documentation](#documentation)

---

## What it is

ByteLore ships as **two clients built from one Angular codebase**:

| | |
|---|---|
| **Desktop application** *(primary)* | Tauri 2. Download a single lesson, a whole module or an entire track, then work through it with no network at all. Downloads resume after a restart, every package is verified by SHA-256 before it counts as done, and updating a library fetches only what actually changed. |
| **Website** *(secondary)* | The same interface without the download features — for browsing content and reading the blog. |

Neither client is a port of the other. A `PlatformService` abstraction hides whether
a lesson came from the local SQLite replica or from the REST API, so components never
learn which target they are running in, and the two builds are produced from the same
source with a compile-time file replacement.

### What you can do with it

- **Read** a track as a path: three modules, three lessons each, every lesson holding
  prose, runnable listings and a "check yourself" section.
- **See the shape of a subject** as an interactive mind map derived from the track's
  own structure, with each leaf linking to the lesson it names.
- **Download** anything down to a single lesson, watch the queue, pause it, and keep
  reading while it works.
- **Keep your progress** — marked lessons sync between devices, and they keep working
  while you are offline or your session has expired.
- **Read the blog**, which is assembled by a scheduled pipeline but published only by
  a person.
- **Switch language and theme** at runtime, in English, Turkish, French or German,
  with no page reload and no flash of the wrong theme at startup.

---

## The library

Seventeen tracks, fifty-one modules, one hundred and fifty-three lessons and four
hundred and fifty-eight code listings, plus a mind map per track.

**Every listing was compiled or executed on the machine that authored it, and its
output recorded byte for byte.** Where a language could not be run in this repository
— React and Vue are not installed and may not be — the listing says so in its own
first comment and carries no recorded output, rather than pretending.

| # | Track | Teaches |
|---:|---|---|
| 1 | The Angular Path | Angular 22 |
| 2 | The Spring Boot Path | Spring Boot 4.1 |
| 3 | The TypeScript Path | TypeScript 5.9 |
| 4 | The Java Path | Java 21 LTS |
| 5 | The PostgreSQL Path | PostgreSQL 16 |
| 6 | The Python Path | Python 3.13 |
| 7 | The Django Path | Django 6.1 |
| 8 | The Rust Path | Rust 1.98, edition 2024 |
| 9 | The Node.js Path | Node.js 22 LTS |
| 10 | The React Path | React 19 |
| 11 | The Vue Path | Vue 3.5 |
| 12 | The Go Path | Go 1.27 |
| 13 | The C# Path | C# 14 on .NET 10 |
| 14 | The Kotlin Path | Kotlin 2.4.20 |
| 15 | The PHP Path | PHP 8.2 |
| 16 | The Ruby Path | Ruby 3.4.10 |
| 17 | The Modern C++ Path | C++23 |

Every lesson names the version it teaches and anchors each claim about behaviour to
that version's official documentation. The words *latest*, *currently* and *as of
writing* are forbidden in a lesson body: a reader cannot tell when a lesson was
written, so a lesson that depends on when it was written is already wrong.

---

## How content gets in

Content is the part of a learning platform with no compiler. A lesson that teaches the
wrong thing renders perfectly, digests to a stable hash, and is indistinguishable from
a correct one to every mechanism in the system. Only a person reading it can tell.

So the pipeline is built around that fact rather than around throughput.

**Teaching content lives as ordinary files.** One markdown body per lesson, metadata
beside it, and every code listing as a real source file a compiler can read — which is
what makes the one mechanically checkable claim, *the examples run*, checkable at all.
A Flyway migration reads that tree and writes the rows.

**The write boundary is moved, not bypassed.** The loader validates every body and
label through the same static predicates the administration API calls: the markdown
validator, the plain-text validator and a closed list of languages. Content the API
would refuse fails the migration, which fails the build. An integration test loads the
shipped corpus into a database of its own on every run, so this is a gate rather than
a promise.

**Nothing publishes itself.** Every track arrives unpublished and the public read
paths serve only published tracks, so a freshly migrated database shows a reader
nothing. Publication is a separate migration that a person commits after reading the
track, and version control attributes it to them. The same rule governs the blog: a
scheduled fetch may only read from an allowlist of sources, each version string is
confirmed by a second independent request, and a post moves `DRAFT → PENDING_REVIEW →
PUBLISHED` with every transition written to an audit log.

---

## Architecture

The server is the single source of truth. It publishes content together with a
manifest carrying a SHA-256 digest and a version number for every entity. The desktop
client keeps a local SQLite read replica and its own download queue, and compares
local versions against the manifest to decide what to fetch.

```
server/     Java 21 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · JWT · MapStruct
              content model: Track → Module → Lesson → CodeExample, plus MindMap
              manifest endpoints with deterministic per-entity digests
              progress sync, translation fallback, rate limiting
              blog pipeline: allowlisted fetch, two-step verification, human approval

desktop/    Tauri 2 · Rust 1.98 · rusqlite · reqwest · sha2
              local SQLite store, download queue with a real state machine,
              SHA-256 verification, HTTP range resume, progress events to the UI,
              signed automatic updates

frontend/   Angular 22 (standalone + signals) · Tailwind 4 · shiki · Jest 30
              one codebase, two targets; a PlatformService abstraction hides
              whether data comes from local SQLite or from the REST API
              four UI locales (en, tr, fr, de) with runtime switching
```

**There is deliberately no Redis and no message broker.** The job queue uses
`FOR UPDATE SKIP LOCKED`, full-text search uses `tsvector`, and caching stays in
PostgreSQL. The local store is SQLite. Adding an external dependency to this list is
an architectural decision, not a convenience.

### The offline model

1. The client asks for a **manifest**: every entity it may hold, with a version and a
   digest.
2. It compares that against its local replica and enqueues only the differences.
3. Each package is downloaded — resuming from a byte offset if a previous attempt was
   interrupted — and **verified against the digest before it is accepted**. A package
   that does not hash correctly is deleted and retried up to three times, then marked
   failed. It never becomes readable content.
4. Reading and recording progress work with no network and with an expired session.
   When connectivity returns, progress reconciles by last-write-wins on a client
   timestamp, clamped so a badly set clock cannot overwrite good data.

Digests are computed in the service layer over content normalised to UTF-8 with LF
line endings, never by a database trigger — so the same lesson produces the same hash
on Windows, on Linux and in CI.

---

## Design invariants

These hold in every phase of the project, and each one has a test behind it.

| Invariant | Enforced by |
|---|---|
| Identical content always produces an identical SHA-256 | determinism tests covering CRLF and LF inputs |
| No content is published without a person saying so | pipeline tests, audit log, publication-by-migration |
| The blog fetches only from an allowlist, with a second verification request | rejection, dedup, broken-feed and failed-verification tests |
| The platform abstraction never leaks — no component knows its target | boundary checks plus a green web build |
| Nothing is marked done until its digest matches | download-engine tests for verify, resume, retry and queue transitions |
| No hard-coded UI text; four locales carry identical key sets | `npm run i18n:check` |
| The stack stays locked — no Redis, no broker, no external SaaS | dependency checks |
| Markdown is sanitized on both sides, and refused rather than rewritten | XSS tests at the write boundary |
| Tauri capabilities stay minimal — no wildcards, no unscoped shell | capability checks |
| Colour and spacing come from design tokens, never raw hex | frontend checks and a theme-toggle test |
| The application works fully offline | offline auth and sync tests |

---

## Getting started

### Prerequisites

| Tool | Version | Pinned in |
|---|---|---|
| JDK | 21 (Temurin LTS) | `server/pom.xml` |
| Node.js | 22.23.2 LTS | `.nvmrc` |
| Rust | 1.98.0 | `rust-toolchain.toml` |
| Docker | with Compose v2+ | — |

Changing one of the three pinned toolchains is a decision about all three: the CI
workflows read the same files.

### 1. Start the database

```bash
docker compose up -d --wait
```

It listens on **port 5433**, not the default 5432, so it does not collide with another
PostgreSQL already on the machine. From the host:

```
jdbc:postgresql://localhost:5433/bytelore
```

Credentials default to `bytelore` / `bytelore_local_dev`, overridable with
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` and `POSTGRES_PORT`. The database
is initialised with the `C` locale so that index and `ORDER BY` behaviour does not
depend on the host.

Stop it with `docker compose stop`. Note that `docker compose down -v` deletes the
data volume and every row in it.

### 2. Run the server

```bash
cd server
./mvnw spring-boot:run
```

Flyway applies the whole migration chain at startup, including the corpus loader. The
API is served under `/api/v1`; the base URLs both clients use live in
[`config/api-endpoints.json`](config/api-endpoints.json), which the frontend imports
and the desktop build reads at compile time.

### 3. Run a client

```bash
cd frontend
npm ci

npm start          # website, http://localhost:4200
npm run start:tauri  # the desktop target's dev server, port 4300
```

For the desktop shell itself:

```bash
cd desktop
npx tauri dev
```

The window starts hidden and is shown only after the theme has been applied, so there
is no flash of the wrong colours.

---

## Running the checks

Nothing here needs a network or a running server, and none of it touches your
development database: the server suite starts a throwaway PostgreSQL through
Testcontainers, and the Rust tests use a temporary directory.

```bash
# server
cd server
./mvnw spotless:check
./mvnw verify              # unit + integration, including a real corpus load

# desktop
cd desktop/src-tauri
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test

# frontend
cd frontend
npx eslint .
npm run typecheck          # full source coverage, not just what the entry point reaches
npx jest
npm run i18n:check         # key parity across en, tr, fr, de, and no empty values
npm run build:web
npm run build:tauri        # both targets, every time — one build proves nothing here
```

Three GitHub Actions workflows run the same three lanes, and the desktop one also
packages the application for Windows and Linux.

---

## Repository layout

```
server/              Spring Boot application
  src/main/resources/content/v13/    the authored corpus: lessons and listings
  src/main/resources/db/migration/   Flyway migrations
desktop/             Tauri 2 application (src-tauri)
frontend/            Angular application, built for both targets
config/              API base URLs, shared by both clients
docs/adr/            Architecture decision records
docs/protocol/       Frozen contracts: REST API, content sync, corpus format,
                     Tauri commands, PlatformService
.github/workflows/   CI: server · frontend · desktop
```

---

## Documentation

The contracts were written and frozen before the code that implements them, because a
wrongly frozen contract costs three lanes rather than one.

| Document | What it fixes |
|---|---|
| [`docs/protocol/rest-api.md`](docs/protocol/rest-api.md) | Endpoint shapes, the `{code, message}` error contract, auth flow, pagination, locale negotiation |
| [`docs/protocol/content-sync.md`](docs/protocol/content-sync.md) | Manifest and package schema, the hashing rule, the delta algorithm, queue states |
| [`docs/protocol/tauri-commands.md`](docs/protocol/tauri-commands.md) | `invoke` command signatures and event payloads |
| [`docs/protocol/platform-service.md`](docs/protocol/platform-service.md) | The abstraction both platform implementations satisfy |
| [`docs/protocol/content-corpus.md`](docs/protocol/content-corpus.md) | The corpus format: layout, identifiers, limits, sources, and what proves a listing |
| [`docs/content-corpus-plan.md`](docs/content-corpus-plan.md) | The seventeen tracks and every lesson title |

Architecture decision records live in [`docs/adr/`](docs/adr/) and cover the mind map
library, markdown safety at the write boundary, last-write-wins progress sync, the
manifest protocol, SQLite schema versioning, the dual-target build, and why the corpus
is repository markdown loaded by a migration.

---

## Line endings

`.gitattributes` pins content and source files to LF **in the working tree**, not only
in the repository. This is load-bearing rather than cosmetic: the sync protocol
promises that identical content always produces an identical SHA-256, and a CRLF
checkout would silently produce a different digest than an LF one for the very same
lesson — a failure that would appear only on one platform, and only after a download
had already been advertised as valid.

---

## License

[MIT](LICENSE) © 2026 Ayberk Arda.

The corpus is covered by the same licence as the code. Every lesson cites the official
documentation it draws on; those sources keep their own terms, and nothing in this
repository reproduces them beyond the short quotations a citation needs.
