# 0005. SQLite schema versioning for the desktop replica

## Status

Accepted, with one gap recorded rather than resolved (see Consequences).

## Context

The desktop client keeps a local SQLite replica of published content plus its
own operational tables — the download queue, progress, sync bookkeeping
(`docs/protocol/content-sync.md` §10). That schema has to evolve across
application releases without losing a user's downloaded content or their
progress, both of which the protocol treats as close to sacred: progress
rows are never deleted "by any operation" (REST contract §2.10), and
deleting downloaded content "never touches `user_progress`"
(`content-sync.md` §10). Whatever mechanism upgrades the schema in place has
to preserve that guarantee across a binary upgrade, not just across ordinary
runtime operation.

What is actually implemented, in `desktop/src-tauri/src/store.rs`:

- Schema version is tracked with SQLite's own `user_version` pragma — a
  32-bit integer built into every SQLite file, not an application-defined
  table.
- Migrations are a `const MIGRATIONS: &[&str]` — an ordered, append-only
  array of raw SQL strings. Index `i` upgrades the schema from
  `user_version = i` to `i + 1`. The module comment states the append-only
  rule as a hard constraint: "editing or reordering an existing entry would
  leave already-upgraded installations on a schema that no longer matches
  what this list describes."
- `migrate()` reads the current `user_version`, then runs
  `MIGRATIONS.iter().enumerate().skip(current)` — every step the store has
  not seen yet — each inside its own transaction that executes the batch of
  DDL and advances `user_version` together, committed as one unit. A crash
  between two steps leaves the store at a whole, valid version; it cannot
  leave a step half-applied.
- `open()` calls `configure()` (WAL mode, foreign keys on — both need
  asking for on every connection) and then `migrate()`, unconditionally, on
  every launch. There is no separate "check if migration is needed" step
  visible to the caller; opening the store *is* bringing it up to date.
- The store path is resolved exclusively from the Tauri runtime's
  application-data directory (`database_path`), never from the working
  directory or a path relative to the executable — the function's own doc
  comment states this is deliberate, so that "a user's downloaded content
  and progress" cannot end up "somewhere that depends on how the binary
  happened to be launched." Tests use `open_in_memory()` or a
  `tempfile::tempdir()`-backed path (`store.rs` tests), never the real
  application data directory.

The server side of this project uses Flyway: versioned `.sql` files, each
one checksummed and recorded in a `flyway_schema_history` table, applied
through a JDBC connection by a library that understands rollback,
out-of-order detection, and repeatable migrations. The desktop replica does
not use anything resembling this, and that is a deliberate difference, not
an oversight — the module comment states the goal outright: an upgrade path
"without a migration framework."

## Options considered

### A. Adopt a Rust migration framework (e.g. a `refinery`- or `sqlx`-migrate-style crate)

- Plus: versioned migration files as separate artifacts, checksums to catch
  a modified-after-release migration, tooling for out-of-order detection —
  closer in spirit to what Flyway gives the server.
- Minus: every such crate is built around the idea of migrations as
  external files or a build-time embedded directory, resolved through the
  crate's own runner, which is more machinery than eleven lines of
  `execute_batch` calls need for a schema this size. `desktop/src-tauri/Cargo.toml`
  carries no such dependency today, and the existing dependency list is
  narrow and pointedly justified line by line (`rusqlite` with the
  `bundled` feature specifically so the on-disk format does not depend on
  "whatever the user's machine happens to ship," `reqwest` and `tokio`
  named for exactly what the download engine needs). Adding a migration
  framework for a linear, append-only list of DDL statements would be the
  first dependency in that file whose job the existing four lines of
  `migrate()` already do.
- Minus: most of these crates assume a connection pool and an async or
  blocking driver abstraction layered over the database, aimed at services
  with many concurrent writers; a single-file embedded store opened once
  per process has no such concurrency to coordinate.

### B. Mirror the server's Flyway approach directly

Give the desktop store versioned `.sql` files on disk, a schema-history
table modeled on Flyway's, and checksum verification of applied migrations.

- Minus: Flyway is a JVM library, tied to a JDBC connection; there is no
  version of it, or a drop-in equivalent, for an embedded Rust/SQLite store.
  Reimplementing its checksum-and-history-table concept by hand would
  produce a second, independent, hand-rolled migration framework — which is
  exactly what Option A already weighs and rejects, just built in-house
  instead of imported.
- Minus: the server and the desktop replica are never migrated together,
  never share a migration history, and run against two different database
  engines with two different constraint models (Postgres with foreign keys
  and generated columns available; SQLite with `foreign_keys` an
  opt-in pragma per connection, as `configure()` sets explicitly). Mirroring
  Flyway's *mechanism* would buy consistency with a system this one never
  interacts with, for a cost — a second migration engine — that inconsistency
  with Flyway does not actually create.

### C. `user_version` pragma plus an ordered, append-only, in-binary list — the option shipped

- Plus: zero additional dependencies. The mechanism is native to SQLite —
  every `.db` file already carries a `user_version` integer, defaulting to
  0, that exists for exactly this purpose — so "checking the schema
  version" costs one `PRAGMA` read, and "advancing it" costs one
  `PRAGMA` write, both already transactional with the DDL they accompany.
- Plus: the migration list ships inside the binary, not as loose files
  beside it. A user cannot accidentally delete, reorder, or edit a
  migration file after installation, because there are no migration files
  on disk to touch — the array is compiled in.
- Plus: matches the actual size and shape of the problem. The current list
  has two steps (`0 → 1` establishes `app_settings` and the versioning
  mechanism itself; `1 → 2` creates the full replica, queue, progress and
  sync-log schema in one batch). A schema this size does not need
  checksummed migration files to stay auditable; it needs to not be edited
  after release, which the append-only convention enforces by discipline
  and by the tests below, not by tooling.
- Minus: no independent tooling. There is no `flyway info`-equivalent to
  list applied migrations from outside the process, no checksum to catch a
  MIGRATIONS entry edited after it shipped (only code review and the
  append-only convention guard against that), and no built-in repair
  command for a store stuck between versions.

## Decision

**Option C: `user_version` plus an ordered, append-only, compiled-in list of
migration strings**, exactly as implemented in `desktop/src-tauri/src/store.rs`.

The deciding factor is not that a migration framework or a Flyway-style
approach would be wrong in the abstract — Flyway is the right tool for the
server, where the store is shared across every server process, evolves
independently of any one client release, and benefits from external
tooling operators can run against a live database. None of that applies to
a single-file, single-process, embedded store that ships and upgrades in
lockstep with the desktop binary itself: there is exactly one writer, the
binary that opens it, and exactly one migration path that writer will ever
run — its own compiled-in list, in order, once. A general-purpose migration
framework is solving a coordination problem this store does not have.

## Consequences

### Positive

- An upgrade across an application release preserves all replica content
  and all progress by construction, not by a migration author's care: today's
  migrations only ever `CREATE TABLE`/`CREATE INDEX` against a fresh or
  partially-migrated store, and `migrate()` runs only the steps a given
  installation has not yet seen (`skip(current)`), so an installation
  upgrading from version 1 to 2 runs step `1 → 2` alone, on top of its
  existing `app_settings` table and whatever it already held under the
  previous binary — nothing is dropped, recreated, or copied.
- Atomicity is per-step: `execute_batch` and the `user_version` bump for one
  migration commit together, so a crash mid-upgrade leaves the store at a
  fully-applied, self-consistent version — never a version whose DDL ran
  but whose `user_version` was not advanced, or the reverse. The test
  `migrating_twice_is_a_no_op` exists specifically to guard the failure mode
  this would otherwise invite: re-running a step that already committed
  (`CREATE TABLE` against a table that already exists).
- No new dependency, no new file format to validate, no separate tooling to
  keep working across Rust toolchain or crate upgrades.

### Negative — an acknowledged, unresolved gap

**A downgrade is not detected, guarded against, or reported.** `migrate()`
computes `current = user_version` and iterates
`MIGRATIONS.iter().enumerate().skip(current)`. If `current` is *larger* than
`MIGRATIONS.len()` — which happens when a store was last opened by a newer
binary whose migration list has grown, and is now opened by an older binary
whose list has not — `skip(current)` simply yields nothing. `migrate()`
returns `Ok(())`, `user_version` is left exactly as it was, and the older
binary proceeds to use a store shaped by migrations its own code has never
seen. Nothing in `store.rs`, and nothing in `lib.rs` at the point it calls
`store::open`, checks for this case or refuses to start. This was verified
directly by reading `migrate()` and its one caller: there is no
`current > MIGRATIONS.len()` branch anywhere in the module.

The practical consequence depends entirely on what the newer binary's extra
migrations did, which this ADR cannot generalise about because only two
migrations exist today and both are purely additive (`CREATE TABLE`,
`CREATE INDEX`, nothing that alters or removes an existing column or
table). An older binary opening a store one step ahead of its own list
would today, most likely, simply not know about a table or column that
exists on disk and never touch it — but nothing in the mechanism *requires*
future migrations to stay additive, and nothing would stop a downgrade from
running against a schema that removed or renamed something the older
binary's queries assume is still there. This is a real gap in the shipped
mechanism, not a hypothetical: it was checked against the code as written,
not inferred from documentation.

### Follow-up

- Add an explicit guard in `migrate()` (or in its caller) for
  `current > MIGRATIONS.len()`, surfaced as a `StoreError` variant rather
  than silently proceeding — matching the existing `StoreError::Sqlite` /
  `StoreError::DataDir` pattern already in the module — so a downgrade fails
  loudly at startup instead of running an old binary against an
  unrecognised schema.
- If a future migration is ever destructive (drops or renames a column a
  running binary depends on), the append-only convention alone stops being
  sufficient protection against a downgrade; the guard above becomes load
  bearing rather than a safety margin.
- No test in `store.rs`'s existing suite exercises the downgrade path
  (`migrating_twice_is_a_no_op` only exercises re-running the *same* list
  against an already-current store, which is a different case). A test
  fixing `current` above `MIGRATIONS.len()` and asserting the new guarded
  behaviour should accompany the fix above.
