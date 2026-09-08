## Why this exists

A transaction that runs alone is easy to reason about. Two transactions running at the same
time are not, because each one is reading rows the other is in the middle of changing. The
isolation level is the setting that decides how much of that the database hides from you,
and its default in PostgreSQL 16 is `read committed`, which hides less than most people
assume. Code that assumed otherwise does not fail loudly; it produces a balance that is
wrong by exactly one withdrawal.

**You need two connections to see any of this.** A transaction always sees its own writes,
so a reader who tries these experiments in one `psql` session gets a different and
thoroughly confusing answer. Open two sessions and interleave them by hand. The listings
here open the second connection with `dblink` instead, so that the interleaving is fixed
and the output is the same every time — but they are two real backends, and the first
listing counts them to prove it.

## The idea

Isolation is a photograph. A transaction is handed a picture of the database and works from
that, so what other people do afterwards does not appear in its hands halfway through a
calculation. `read committed` takes a fresh photograph before each statement.
`repeatable read` takes one at the start and works from it to the end.

### Where the analogy breaks

A photograph is read-only and a transaction writes. That is where the whole difficulty
lives: two transactions can each be working from a perfectly consistent picture, each make a
change that is legal in its own picture, and produce a state that was legal in neither. The
third listing shows two people each confirming that a colleague is still on call, and each
standing down, leaving nobody.

The picture also suggests a transaction is unaware of the outside world. Writes are not
like that: an `UPDATE` under `read committed` that meets a row another transaction has just
changed does not use its own snapshot for that row. It waits, re-reads the new version and
applies itself to that. This is why arithmetic done inside SQL survives an interleaving that
the same arithmetic in application code does not.

## How it works

PostgreSQL 16 offers three levels: `read committed`, `repeatable read` and `serializable`.
Each statement under `read committed` sees everything committed before that statement began,
so two reads in one transaction can disagree.

```sql
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT taken FROM seat WHERE id = 1;   -- f
-- another session commits taken = true here
SELECT taken FROM seat WHERE id = 1;   -- t
COMMIT;
```

Under `repeatable read` the second read still answers `f`. The other session's commit is
real and permanent; this transaction simply is not looking at a picture that contains it.

The lost update is the practical consequence. Read a balance of 100, decide in application
code to subtract 30, write 70 — while another session has read the same 100 and written 50.
The second write does not conflict with anything, both commit, and one withdrawal has
disappeared. The second listing produces exactly that: 70 where the correct answer is 20.

```sql
UPDATE balance SET amount = 100.00 - 30.00 WHERE id = 1;   -- 70.00, one withdrawal lost
UPDATE balance SET amount = amount - 30.00 WHERE id = 1;   -- 20.00, both applied
```

Two repairs exist. Do the arithmetic in SQL, as above. Where the decision has to happen
outside the database, say so when reading: `SELECT ... FOR UPDATE` holds the row until the
transaction ends, and the other session waits — or, with `NOWAIT`, is told `55P03` at once
rather than reading a value that is about to change.

`serializable` handles the case neither of those catches. Under `repeatable read`, two
transactions that read overlapping rows and write different ones never conflict, so both
commit and the invariant they each checked is broken. `serializable` tracks those read-write
dependencies and refuses the combination, raising `40001` at the second commit.

```
ERROR:  could not serialize access due to read/write dependencies among transactions
HINT:  The transaction might succeed if retried.
```

`40001` is not a bug report. It means "run this transaction again from the beginning", and
any application using `serializable` needs a retry loop, because the level's guarantee is
bought with that error.

## Common mistakes

**Read-modify-write across two statements under the default level.** It is the lost update,
and it leaves no trace.

**Testing isolation in a single session.** A transaction sees its own uncommitted writes, so
every experiment appears to work.

**Assuming `repeatable read` prevents anomalies.** It prevents a transaction seeing two
different values for the same row. It does not prevent two transactions between them
breaking a rule that each checked, which is what the third listing demonstrates.

**Using `serializable` without a retry loop.** The `40001` reaches the user as a failure.

**Holding a transaction open across a network call.** Every row it locked stays locked, and
its snapshot keeps old row versions from being cleaned up.

## Check yourself

<details><summary>Why can this not be demonstrated in one session?</summary>

Because a transaction always sees its own writes, at every isolation level. Both statements
would come from the same backend, and no snapshot boundary is ever crossed. Two connections
are required, which is why the listings count the backends before starting.

</details>

<details><summary><code>amount = amount - 30</code> survived the interleaving and <code>amount = 100 - 30</code> did not. Why?</summary>

Under `read committed`, an `UPDATE` that meets a row changed by a concurrent transaction
waits for it, then re-evaluates against the committed version. The subtraction therefore
starts from 50. The literal `100` was computed from a read taken before the other write and
is stale by the time it is written.

</details>

<details><summary>What is the correct response to <code>SQLSTATE 40001</code>?</summary>

Roll back and run the whole transaction again. It is the mechanism by which `serializable`
delivers its guarantee, not a sign that anything is misconfigured.

</details>

## Listings

1. `transactions-and-isolation-1.sql` — the same read, twice, at two isolation levels.
2. `transactions-and-isolation-2.sql` — the lost update, and two ways to stop it.
3. `transactions-and-isolation-3.sql` — the rule both sessions checked, and `40001`.
