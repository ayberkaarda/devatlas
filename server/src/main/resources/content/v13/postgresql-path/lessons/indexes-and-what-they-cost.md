## Why this exists

Without an index, finding the rows that match a condition means reading every row, because
the only way to be sure nothing was missed is to look at everything. That is fine on a
thousand rows and ruinous on ten million. An index is a second, ordered copy of some
columns that lets the database skip straight to a range. It is also a thing that must be
written on every insert, kept correct on every update, and stored on disk forever, which
is why "add an index" is a trade and not a free improvement.

## The idea

An index is the index at the back of a reference book. The entries are sorted, each points
at a page, and you find "collation" without reading the book. The book itself is still
where the content lives; the index only tells you where to look.

### Where the analogy breaks

A book's index is printed once and then it is finished. A database index is maintained: it
is a second structure that every insert, update and delete has to keep in step, inside the
same transaction, at the cost of the write. A table with six indexes does six extra pieces
of work on every row it accepts.

The bigger divergence is that a reader always chooses to use the book's index, and the
database does not. PostgreSQL 16 has a planner that estimates the cost of each approach and
picks one. Given a condition matched by most of the table it reads the whole table on
purpose, and it is right to: walking an index and then fetching the rows it points at means
touching the same pages anyway, plus the index, in a less useful order.

## How it works

The default index type is a B-tree, which keeps entries in sorted order. That ordering is
the whole mechanism and it decides which questions the index can answer: equality and
ranges on the leading column become a seek to a contiguous stretch of entries, and anything
that does not translate into such a stretch does not.

```sql
EXPLAIN (COSTS OFF) SELECT id FROM event WHERE user_id = 4242;
-- Seq Scan on event                      before the index
-- Bitmap Heap Scan on event              after it
--   ->  Bitmap Index Scan on ix_event_user
```

A function applied to the column defeats it, because the index stores the column and the
query asks about the result. The repair is an index over the expression the query actually
uses. The same applies to pattern matching: a prefix pattern is a range only in an ordering
where "starts with" and "sorts between" are the same question, which is true under the `C`
collation and not under a linguistic one. `text_pattern_ops` builds the index with that
ordering, and the plan then shows the range operators `~>=~` and `~<~`.

```sql
CREATE INDEX ix_account_email_lower   ON account (lower(email));
CREATE INDEX ix_account_email_pattern ON account (email text_pattern_ops);
```

A pattern anchored at the other end — `LIKE '%example.com'` — cannot use any ordering of
whole strings, and no B-tree will help.

For a multicolumn index the order of the columns is the decision. Entries are sorted by the
first column, then the second within it. A condition on the leading column is a seek. A
condition on a later column alone is not: PostgreSQL 16 will still use such an index,
because reading every entry of a narrow index can beat reading the whole table, but it is
scanning all of it rather than seeking. The third listing shows the planner switching to a
second index once one exists with the columns the other way round.

```sql
CREATE INDEX ix_progress_user_lesson ON progress (user_id, lesson_id);
-- WHERE user_id = 42                 -> seek
-- WHERE user_id = 42 AND lesson_id=7 -> narrower seek, answered from the index alone
-- WHERE lesson_id = 7                -> the whole index read and filtered
```

This application's own schema shows both halves of the trade. `ix_tracks_published_order`
exists because the public list is always filtered on `published` and ordered by
`display_order`, so the ordering in the index is the ordering the query wants. There is
deliberately no unique constraint on `(track_id, display_order)`: the service renumbers a
parent's children inside one transaction, and an immediately checked constraint would
reject the intermediate states.

## Common mistakes

**Indexing a column the query never uses in that form.** `WHERE lower(email) = ...` against
an index on `email` gives a sequential scan, and the plan says so.

**Adding an index without reading the plan first.** If the planner was already choosing a
sequential scan for good reasons, the new index is pure write cost.

**Leaving indexes nobody uses.** `pg_stat_user_indexes.idx_scan` counts how often each was
chosen. A zero there on a busy system is an index paying rent and doing nothing.

## Check yourself

<details><summary>The planner refuses to use an index that exactly matches the condition. Is it broken?</summary>

Almost certainly not. Check how many rows match. In the first listing, the condition
matching one row in a hundred gets an index-only scan and the one matching ninety-nine in a
hundred gets a sequential scan, from the same table with the same index.

</details>

<details><summary>Why does an index on <code>(user_id, lesson_id)</code> not seek for <code>WHERE lesson_id = 7</code>?</summary>

Because the entries are sorted by `user_id` first. Every user has a lesson 7, so the
matching entries are scattered through the whole index and there is no contiguous stretch to
jump to. The index can still be read end to end and filtered, which the plan shows.

</details>

<details><summary>What does an index cost when nobody queries it?</summary>

Disk, maintenance on every write to the table, and time during vacuum. It also widens the
lock footprint of some schema changes. It costs the same whether it is used or not.

</details>

## Listings

1. `indexes-and-what-they-cost-1.sql` — the same query with and without, and selectivity.
2. `indexes-and-what-they-cost-2.sql` — expressions, prefixes, and what no B-tree can do.
3. `indexes-and-what-they-cost-3.sql` — column order, and the index nobody has ever used.
