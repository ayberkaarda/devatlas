# ByteLore

An offline-first learning platform for programming languages and frameworks: lessons,
runnable code examples, interactive mind maps and a blog.

ByteLore ships as two clients built from one Angular codebase:

- **Desktop application (primary)** — Tauri 2. Download a single lesson, a whole
  module, or an entire track, then work through it with no network at all. Downloads
  resume after a restart, every package is verified by SHA-256, and updating a library
  fetches only what actually changed.
- **Website (secondary)** — the same UI without the download features, for browsing
  content and reading the blog.

## Status

**Under construction.** The repository is at phase 0 (skeleton). Nothing below the
"Local development" section is usable yet except the database.

| Component | State |
|---|---|
| `docker-compose.yml` — PostgreSQL 16 | working |
| `server/` — Spring Boot API | not started |
| `frontend/` — Angular client | not started |
| `desktop/` — Tauri 2 shell and download engine | not started |

## Architecture

The server is the single source of truth. It publishes content together with a
manifest that carries a SHA-256 digest and a version number for every entity. The
desktop client keeps a local SQLite read replica plus its own download queue, and
compares local versions against the manifest to decide what to fetch.

```
server/     Spring Boot 3 · Java 21 · PostgreSQL 16 · Flyway · JWT
              content model: Track -> Module -> Lesson -> CodeExample
              manifest endpoints with deterministic per-entity hashes
              blog pipeline: scheduled fetch from an allowlist, multi-step
              verification, and mandatory human approval before publishing

desktop/    Tauri 2 · Rust
              local SQLite store, download queue, SHA-256 verification,
              HTTP range resume, progress events to the UI

frontend/   Angular · Tailwind · design tokens · four UI locales (en, tr, fr, de)
              one codebase, two targets; a PlatformService abstraction hides
              whether data comes from local SQLite or from the REST API
```

There is deliberately no Redis and no message broker. The job queue uses
`FOR UPDATE SKIP LOCKED`, full-text search uses `tsvector`, and caching stays in
PostgreSQL.

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 (LTS) |
| Node.js | 22 LTS — pinned in `.nvmrc` |
| Rust | stable |
| Docker | with Compose v2+ |

## Local development

Start the database:

```bash
docker compose up -d --wait
```

It listens on **port 5433**, not the default 5432, so it does not collide with another
PostgreSQL already running on the machine. From the host the connection string is:

```
jdbc:postgresql://localhost:5433/bytelore
```

Credentials default to `bytelore` / `bytelore_local_dev` and can be overridden with
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` and `POSTGRES_PORT`.

The test suite does not use this instance. Server tests start a throwaway PostgreSQL
through Testcontainers, so running them cannot touch local development data.

Stop it with `docker compose stop`. Note that `docker compose down -v` deletes the
`bytelore-postgres-data` volume and every row in it.

## Repository layout

```
server/              Spring Boot application
desktop/             Tauri 2 application (src-tauri)
frontend/            Angular application, built for both targets
docs/adr/            Architecture decision records
docs/protocol/       Content sync and API contracts
.github/workflows/   CI: server, frontend, desktop
```

## Line endings

`.gitattributes` pins content and source files to LF in the working tree, not only in
the repository. This is load-bearing rather than cosmetic: the sync protocol promises
that identical content always produces an identical SHA-256, and a CRLF checkout would
silently produce a different digest than an LF one for the very same lesson.

## License

Not yet chosen.
