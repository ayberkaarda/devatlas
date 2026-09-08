## Why this exists

Application code enforces its rules on the path that code takes. A database usually
has several other paths: a second service, a background job, a data-repair script, a
bulk import, and a person with a terminal open at two in the morning. Every one of
those writes rows. Only one thing sits on all of those paths at once, and that is the
table declaration. PostgreSQL 16 will refuse a write that breaks a declared rule, at
the cost of a few microseconds, on every path, forever — including the paths that had
not been invented when the rule was written.

## The idea

A table declaration is the gauge of a railway line. The width between the rails is not
a policy the stationmaster applies when he remembers to; it is the geometry of the
track. A wagon built to the wrong gauge does not get a warning and does not get through
on a quiet afternoon. It cannot physically enter, and it cannot enter by a side door
either, because the side door is on the same rails.

### Where the analogy breaks

A gauge is passive and costs nothing to maintain. A constraint is an active check that
runs on every insert and every update, and a foreign key makes the database go and look
at another table before it says yes. That is cheap, but it is not free, and on a bulk
load of millions of rows it is measurable.

The gauge also stops the wrong wagon every single time, and a `CHECK` does not. A
constraint refuses a row only when its condition evaluates to **false**. A condition on
a column that holds no value evaluates to unknown, which is not false, and the row goes
through. This is the single most surprising thing about constraints and it is the
subject of the third lesson in this module.

Finally, a railway gauge cannot be changed while trains are running, and a constraint
can. PostgreSQL 16 lets you add one without scanning the rows already present, which is
what the last lesson of this track is about.

## How it works

A declaration mixes four kinds of promise: the type, the width, `NOT NULL`, and named
`CHECK` / `UNIQUE` / `PRIMARY KEY` / `FOREIGN KEY` constraints. The PostgreSQL 16
documentation on constraints describes each, and every violation reports a specific
`SQLSTATE`: `23502` for not-null, `23514` for check, `23505` for unique, `22001` for a
value too long for its declared width.

```sql
CONSTRAINT uq_account_email UNIQUE (email),
CONSTRAINT ck_account_email_lowercase CHECK (email = lower(email)),
CONSTRAINT ck_account_role CHECK (role IN ('ADMIN', 'EDITOR', 'USER'))
```

Naming them is not decoration. An unnamed constraint gets a generated name such as
`account_role_check`, and that is the string that reaches your error handler, your logs
and eventually a user. A name you chose is a name you can map to a message.

The type is the first promise and the hardest to change later. `numeric` computes in
base ten and `double precision` does not. `timestamptz` stores an instant and renders
it in the reader's time zone; `timestamp` stores the reading on a wall clock and means
nothing without knowing which wall.

```sql
SELECT 0.1::double precision + 0.2::double precision = 0.3::double precision,  -- false
       0.1::numeric          + 0.2::numeric          = 0.3::numeric;           -- true
```

Width behaves differently from precision, and the difference catches people. Inserting
`12.345` into `numeric(10,2)` rounds silently to `12.35`; inserting `123456789.01`
raises `22003`, because the digits to the left of the point do not fit. One is a quiet
adjustment and one is a refusal.

```sql
INSERT INTO priced VALUES ('rounded', 12.345);       -- stored as 12.35
INSERT INTO priced VALUES ('too-big', 123456789.01); -- ERROR: numeric field overflow
```

## Common mistakes

**Validating only in the application.** The third listing runs the same bad values
through `UPDATE` and through `COPY`. Both are refused by the constraint and neither
went anywhere near the service layer. Note also that the failing `COPY` discarded its
whole batch, including the good row before it: `COPY` is one statement.

**Money in `double precision`.** It compiles, it runs, and the totals drift.

**Trusting a `CHECK` on a nullable column.** `CHECK (price > 0)` admits a row whose
price is absent, because unknown is not false.

**Reaching for `text` everywhere.** There is no storage penalty for `varchar(n)` in
PostgreSQL 16, and a declared width is a promise the database keeps for you. The schema
this application is built on uses `varchar(80)` for slugs and `varchar(200)` for titles
for exactly that reason.

## Check yourself

<details><summary>An <code>UPDATE</code> sets a column to a value the <code>CHECK</code> forbids. When is it refused?</summary>

Immediately, with `SQLSTATE 23514`. A `CHECK` is evaluated for every row an `INSERT` or
`UPDATE` produces, not only at insert time. That is the whole point: the insert path was
never the only way in.

</details>

<details><summary>Why does <code>numeric(10,2)</code> round <code>12.345</code> but refuse <code>123456789.01</code>?</summary>

Scale and precision are enforced differently. Excess digits after the point are rounded
to the declared scale; digits before the point have nowhere to go, so the value cannot
be represented at all and PostgreSQL raises `22003`.

</details>

<details><summary>Two services write to this table. Where does the rule belong?</summary>

In the table. A rule in one service is a rule the other has to be told about, remember,
and keep in step through every future release. A constraint is checked once, in the one
place both of them go through.

</details>

## Listings

1. `a-table-is-a-promise-1.sql` — five bad rows and the five different refusals.
2. `a-table-is-a-promise-2.sql` — what the type promises: base ten, width, and instants.
3. `a-table-is-a-promise-3.sql` — the same constraint holding on `UPDATE` and on `COPY`.
