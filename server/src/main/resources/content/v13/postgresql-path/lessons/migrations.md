## Why this exists

A schema change on an empty database is a text edit. The same change on a table holding
fifty million rows, with two releases of an application running against it, is an operation
with a duration, a lock, and a version of the code on either side of it. Most migration
accidents are not wrong SQL. They are correct SQL that held an exclusive lock for four
minutes, or that broke the release still running while the new one was rolling out.

## The idea

A migration is repairing a bridge that traffic is still crossing. Nobody argues about
whether the new design is better; the whole question is the sequence of steps, and whether
any single step closes the bridge. You work a lane at a time, and for a while both the old
surface and the new one carry cars.

### Where the analogy breaks

Lanes on a bridge are physically separate and a table's columns are not. Both releases are
writing to the same rows, so the intermediate state needs something keeping the old column
and the new one in step — a trigger, or application code that writes both — and that
scaffolding is itself a thing you must remember to remove.

The bridge also fails visibly. A schema change fails in two quieter ways: a lock held long
enough for connections to pile up behind it, and a change that succeeds while breaking a
release still in production. Neither shows up in the migration's own output. The migration
says it worked, because from its point of view it did.

## How it works

The first question about any `ALTER TABLE` is whether it rewrites the table. A rewrite
copies every row into a new file and holds `ACCESS EXCLUSIVE` — which blocks reads as well
as writes — for the whole copy. Comparing `pg_relation_filenode` before and after is how to
find out, and the first listing does exactly that.

Since PostgreSQL 11, adding a column with a **constant** default is recorded as metadata:
no rows are touched, and the value is supplied as they are read. A default that has to be
evaluated per row cannot be metadata, and the table is rewritten.

```sql
ALTER TABLE lesson ADD COLUMN difficulty  text NOT NULL DEFAULT 'BEGINNER';        -- no rewrite
ALTER TABLE lesson ADD COLUMN external_id uuid NOT NULL DEFAULT gen_random_uuid(); -- rewrite
```

Adding a `NOT NULL` column with no default to a populated table fails, and the message says
why: the existing rows would have nothing in it. The safe form is three statements — add it
nullable, fill it in, then declare the promise — each of which can be deployed separately
and none of which needs the whole table locked at once.

The same shape applies to constraints. `ADD CONSTRAINT ... CHECK (...)` scans the entire
table before it will accept the rule, and refuses if any existing row breaks it. `NOT VALID`
records the rule without scanning: it binds every new row and every row an `UPDATE` touches
from that moment, and says nothing about the rows already there. `VALIDATE CONSTRAINT` then
does the scan as a separate step, under a weaker lock that does not block writes.

```sql
ALTER TABLE account ADD CONSTRAINT ck_account_balance CHECK (balance >= 0) NOT VALID;
-- fix the old rows, at whatever pace suits
ALTER TABLE account VALIDATE CONSTRAINT ck_account_balance;
```

`pg_constraint.convalidated` reports which state a constraint is in, which matters because
a constraint that is enforced but never validated is easy to forget about. Foreign keys
behave identically, and the second listing shows both.

Renaming is instant and unsafe. The moment the rename commits, every query the previous
release is still sending fails with `42703`. Expand and contract replaces one step with
four: add the new column, backfill it, keep both in step while both releases run, then drop
the old one once nothing reads it. Each step is deployable on its own.

Finally, building an index the ordinary way locks out writers for the duration.
`CREATE INDEX CONCURRENTLY` does not, at the price of not being transactional — and a
migration tool that wraps each step in a transaction therefore cannot run it.

```
ERROR:  CREATE INDEX CONCURRENTLY cannot run inside a transaction block
```

That error is worth recognising on sight, because the fix is a property of the migration
file rather than of the SQL in it.

## Common mistakes

**Adding a column with a computed default on a large table.** It is a full rewrite under an
exclusive lock, and the migration that took a second in development takes the site down.

**Renaming a column in the same deployment that changes the code.** For the length of the
rollout, two versions are running. One of them is broken.

**Adding a constraint outright on a big table.** The scan holds a lock. `NOT VALID` first,
`VALIDATE` second.

**Reaching for `CREATE INDEX CONCURRENTLY` inside a transactional migration.** It fails with
`25001` every time, and no amount of rewriting the SQL helps.

## Check yourself

<details><summary>How do you tell whether an <code>ALTER TABLE</code> rewrote the table?</summary>

Compare `pg_relation_filenode` for that table before and after. A rewrite writes a new file,
so the number changes. The first listing does this for a constant default and a computed one
and gets different answers.

</details>

<details><summary>What does <code>NOT VALID</code> actually promise?</summary>

That the rule is enforced from now on — for inserts and for updates to existing rows — and
that no scan of the existing data has happened. Rows already present may still break it
until `VALIDATE CONSTRAINT` succeeds, and `pg_constraint.convalidated` says which state it
is in.

</details>

<details><summary>Why can a migration tool not run <code>CREATE INDEX CONCURRENTLY</code>?</summary>

Because it wraps each migration in a transaction, and the concurrent build has to commit
several times to do its multiple passes over the table. The statement refuses with `25001`.
Such a step has to be run outside a transaction block.

</details>

## Listings

1. `migrations-1.sql` — which `ALTER TABLE` rewrites, proved by the file behind the table.
2. `migrations-2.sql` — `NOT VALID` and `VALIDATE`, for a check and for a foreign key.
3. `migrations-3.sql` — the rename that broke a release, expand and contract, and `25001`.
