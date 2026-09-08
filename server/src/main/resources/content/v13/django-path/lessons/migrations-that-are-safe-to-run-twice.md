## Why this exists

Deployment scripts run `migrate` every time. Somebody runs it locally, then in
staging, then on a machine that was already up to date. The question "is it safe
to run this again?" therefore gets asked constantly and answered with folklore:
*migrations are idempotent*, people say, or *Django keeps track*. Both are half
true, and the half that is missing is the half that loses data. Django keeps a
ledger of what has been applied, and the ledger is what makes a repeat run free.
The ledger does not make an *operation* repeatable, and the difference shows up
the first time anybody rolls back.

## The idea

The migration files are a recipe book and the `django_migrations` table is the
cook's tick list. Running `migrate` means reading down the list, finding the
first recipe with no tick beside it, and cooking from there. Run it again with
every box ticked and the cook does nothing at all — not because the recipes are
harmless to repeat, but because nobody asks for them a second time.

### Where the analogy breaks

A tick list is a record of the past; this one is also the instruction. Delete a
row and the migration runs again for real. Listing 1 deletes one row from
`django_migrations` and the plan length goes from `0` back to `1`, with nothing
having changed on disk.

And the ticks say nothing about whether cooking twice is safe. Listing 2 has a
data migration that adds 100 to every balance. Applying it, then running
`migrate` again, leaves the balance at 200 — the ledger did its job. Rolling
back to `0001` and re-applying leaves it at **300**. The operation was never
repeatable; the ledger was just hiding it.

## How it works

Each applied migration is recorded by app label and name. Listing 1 prints the
ledger and the plan Django would execute:

```python
executor = MigrationExecutor(connection)
executor.migration_plan(executor.loader.graph.leaf_nodes("ledger"))
```

Before the first run the plan holds two migrations; after it, zero. `migrate`
run a second time changes nothing and touches no rows.

That protects you from repetition. It does not protect you from re-application,
and the difference is whether an operation describes a *state* or a *change*:

```python
Account.objects.update(balance=F("balance") + 100)      # a change: not repeatable
Account.objects.filter(tier="").update(tier="standard") # a state: repeatable
```

Listing 2 runs both through a rollback and a re-apply. The relative update
lands at 300; the guarded backfill is still exactly `"standard"`. Writing data
migrations in the second style costs nothing and removes a whole category of
incident.

Rows already in the table are the other half. Adding a `NOT NULL` column with no
default has nothing to write into the existing rows. Listing 3 applies the same
one-step migration twice — against an empty table and against a table with two
rows:

```
one step, empty table: applied
one step, table with rows: refused with IntegrityError
```

The documented way through is three operations rather than one: add the column
nullable, backfill it in a `RunPython`, then `AlterField` to tighten it. Listing
3 does that and ends with `currency column allows null: False` and both existing
rows carrying a value.

## Common mistakes

**Reading "migrate is idempotent" as "my migration is."** The command is; the
`RunPython` you wrote may not be. Rollback and re-apply is the case that finds
out, and listing 2 measures it.

**A `RunPython` with no reverse.** `migrations.RunPython.noop` as `reverse_code`
makes a rollback *possible*, not correct: listing 2's rollback leaves the data
where the forward function put it. That is fine when it is deliberate and a trap
when it is copied in without thought.

**Adding a required column to a table with rows.** `IntegrityError`, at the
worst moment. Add it nullable, backfill, then tighten.

**Importing the model into a data migration.** `apps.get_model("app", "Model")`
gives the historical version that matches this point in the migration history.
The imported class is today's, and it will not match when the migration is
replayed on a fresh database.

**Editing a migration that has already been applied somewhere.** Its ledger row
says it ran. Your edit will not.

## Check yourself

<details><summary>Why is a second <code>migrate</code> free?</summary>

Because the plan is empty. Listing 1 prints plan length 2 before the first run
and 0 before the second; nothing executes, so nothing can go wrong.

</details>

<details><summary>Rollback then re-apply. What breaks?</summary>

Anything that expresses a change rather than a state. Listing 2's
`balance = balance + 100` reaches 300; the guarded `filter(tier="")` backfill
reaches the same value it reached the first time.

</details>

<details><summary>How do you add a required column to a populated table?</summary>

In three migrations: nullable, backfill, then `AlterField` to `null=False`.
Listing 3 shows the one-step version failing on rows and succeeding on an empty
table, and the three-step version succeeding on both.

</details>

## Listings

1. `migrations-that-are-safe-to-run-twice-1.py` — the ledger, and what deleting
   a row from it does.
2. `migrations-that-are-safe-to-run-twice-2.py` — a data migration that is not
   safe to re-apply, beside one that is.
3. `migrations-that-are-safe-to-run-twice-3.py` — the same migration on an empty
   table and on a populated one.
