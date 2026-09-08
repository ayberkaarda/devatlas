## Why this exists

Almost every bug in this lesson looks like a query that returned nothing, a total that was
too small, or a filter that quietly stopped filtering. None of them raise an error. They
happen because SQL logic has three truth values rather than two, and a condition that comes
out neither true nor false is discarded by `WHERE` exactly as a false one is. PostgreSQL 16
follows the standard here, so this is not a quirk of one database; it is the model.

## The idea

An unknown is a sealed envelope. You can hold two envelopes and be certain of one thing:
you cannot say whether their contents match. Not "they do not match" — you cannot say.
Asked whether the envelope contains the number ten, the honest answer is neither yes nor
no. Asked whether it differs from ten, the honest answer is again neither.

### Where the analogy breaks

An envelope contains something and an unknown may stand for nothing at all: absent,
inapplicable, never asked, deliberately cleared. SQL uses one marker for all of those,
which is why a column that means "not yet estimated" and a column that means "declined to
answer" cannot be told apart later without a second column saying which.

The envelope also misleads about grouping. Two sealed envelopes cannot be compared, so you
would expect them never to land in the same pile. `GROUP BY` and `SELECT DISTINCT` put
them in the same pile anyway: for grouping purposes PostgreSQL 16 treats two unknowns as
the same, which is the exact opposite of what the equality operator says about them. The
last listing shows both behaviours in the same query.

## How it works

`AND` and `OR` are extended to three values. `false AND unknown` is `false`, because one
false is enough. `true OR unknown` is `true`, for the same reason. Everything else
involving an unknown is unknown, and `NOT unknown` is unknown.

The consequence is the one line worth memorising: **`WHERE` keeps a row only when its
condition is true.** Unknown is thrown away with false, so `value = NULL` and
`value <> NULL` both match nothing at all, and no error is raised to tell you.

```sql
WHERE value =  NULL   -- 0 rows
WHERE value <> NULL   -- 0 rows
WHERE value IS NULL   -- the rows you meant
```

`IS DISTINCT FROM` is the comparison that answers rather than shrugging: it treats two
unknowns as equal and an unknown against a value as different, and it always returns true
or false.

The most expensive form of this is `NOT IN` over a subquery. If the subquery yields even
one unknown, the whole predicate can never be true for any row, and the query returns
nothing. `NOT EXISTS` asks a different question and is not affected.

```sql
SELECT count(*) FROM lesson WHERE id NOT IN (SELECT lesson_id FROM flagged);          -- 0
SELECT count(*) FROM lesson l
WHERE NOT EXISTS (SELECT 1 FROM flagged f WHERE f.lesson_id = l.id);                  -- 2
```

Aggregates take the other approach: they skip unknowns rather than propagating them.
`count(*)` counts rows and `count(col)` counts rows where `col` has a value, so the two
disagree exactly when it matters. `avg` shrinks its denominator to match, which is usually
right and occasionally very wrong — `coalesce(minutes, 0)` changes the answer rather than
tidying it. Over no rows at all, `sum` returns unknown while `count` returns zero.

Constraints deserve a separate warning. A `CHECK` refuses a row only when its condition is
false, so a nullable column slips past its own rule.

```sql
CREATE TABLE priced (sku text NOT NULL, price numeric(10,2) CHECK (price > 0));
INSERT INTO priced VALUES ('b', -5.00);  -- refused, 23514
INSERT INTO priced VALUES ('c', NULL);   -- accepted
```

If the column must have a value, say `NOT NULL`. The `CHECK` will not do it for you.

## Common mistakes

**Writing `= NULL` instead of `IS NULL`.** Zero rows, no error, and it looks fine in a
code review.

**`NOT IN` against a nullable column.** The second listing shows the query returning zero
where the honest answer is two. Prefer `NOT EXISTS`, or add `WHERE col IS NOT NULL` to
the subquery.

**Concatenating a name with an absent middle name.** `'Ada' || NULL || 'Lovelace'` is
unknown, and the whole rendered string vanishes. The `concat()` function ignores unknown
arguments instead, which is why it exists.

**Assuming an unknown sorts last.** It does ascending and first descending, because
PostgreSQL 16 treats unknowns as larger than everything. Say `NULLS FIRST` or `NULLS LAST`
when it matters.

**Expecting a `CHECK` to imply `NOT NULL`.** It does not.

## Check yourself

<details><summary>Why does <code>NOT IN</code> return nothing when the subquery contains one unknown?</summary>

`id NOT IN (1, NULL)` expands to `id <> 1 AND id <> NULL`. The second comparison is
unknown for every row, so the conjunction is at best unknown and never true, and `WHERE`
keeps only true. `NOT EXISTS` asks whether a matching row was found, which is always
answerable.

</details>

<details><summary><code>count(*)</code> says 3 and <code>count(minutes)</code> says 2. Which is wrong?</summary>

Neither. `count(*)` counts rows; `count(minutes)` counts rows where `minutes` has a value.
The gap between them is the number of rows where the value is absent, and a report that
divides one total by the other needs to be explicit about which denominator it meant.

</details>

<details><summary><code>GROUP BY</code> put two unknown values in one group, but <code>=</code> says they are not equal. Is that a contradiction?</summary>

It is an inconsistency in the standard that PostgreSQL 16 implements faithfully. Equality
is three-valued; grouping and `DISTINCT` are defined in terms of "not distinct", which
treats two unknowns as the same. Both are listed in the third listing so the difference is
visible side by side.

</details>

## Listings

1. `null-is-not-a-value-1.sql` — the three-valued truth table and what `WHERE` keeps.
2. `null-is-not-a-value-2.sql` — `NOT IN` returning nothing, and what aggregates skip.
3. `null-is-not-a-value-3.sql` — the `CHECK` that admits it, and grouping that does not.
