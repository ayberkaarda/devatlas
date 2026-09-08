## Why this exists

Nobody can tell how a query will run by reading it. SQL says what you want, not how to get
it, and the planner in PostgreSQL 16 decides the how from statistics about the data. That
gap is why a query that was instant last month is slow this month with the same text and the
same indexes. `EXPLAIN` closes it, and `EXPLAIN ANALYZE` runs the query and shows what
actually happened — the only way to find out that an estimate was wrong.

## The idea

A plan is a receipt from a delivery route. Reading it tells you which warehouses were
visited, in what order, and how many parcels came out of each. You are not being told how
to drive; you are being told what was done and how much of it, so that you can see which
stop is the one with ten thousand parcels in it.

### Where the analogy breaks

A receipt is a record of something that happened. Plain `EXPLAIN` is a forecast: nothing
has run, and every number in it is an estimate derived from statistics that may be stale or
missing. The two are printed in the same shape, which is exactly why people quote costs as
though they were measurements.

The route picture also suggests the stops happen one after another. Plan nodes are a tree,
and the tree pulls: a node asks its children for rows one at a time, and some nodes stop
early. A `LIMIT` above a `Sort` still requires the whole sort; a `LIMIT` above a nested loop
may consume only a handful of rows from a child estimated to produce millions.

## How it works

The plan prints as an indented tree with the root at the top. Read it from the most-indented
line outwards: those nodes run first and hand rows upwards. `COSTS OFF` removes the numbers
and leaves the shape, which is the part worth memorising.

```
Nested Loop
  ->  Index Scan using pk_author on author a
        Index Cond: (id = 7)
  ->  Bitmap Heap Scan on post p
        Recheck Cond: (author_id = 7)
        ->  Bitmap Index Scan on ix_post_author
```

That is one author fetched by key, then their posts. The same query text without the
`WHERE` becomes a `Hash Join` over two sequential scans, because twenty thousand index
lookups cost more than reading both tables once and building a hash table. The plan changed;
the SQL did not.

Cost is expressed in arbitrary units, as a pair: the cost to produce the first row and the
cost to produce all of them. That split is why `LIMIT` can change which plan wins. The second
listing runs the same query twice, once with `enable_seqscan` turned off, so the rejected
plan and its cost are both visible.

```
Seq Scan on track          (cost=0.00..9.00 rows=166 width=17)
Bitmap Heap Scan on track  (cost=9.56..15.22 rows=166 width=17)
```

Four pages. Reading them start to finish costs nine units; walking the index and then
fetching from those same four pages costs fifteen. **A sequential scan is not a diagnosis.**
On a small table, or a condition that matches a large fraction of a big one, it is the right
answer and the planner is not confused.

The number that does deserve suspicion is the estimate. `EXPLAIN ANALYZE` prints
`rows=N` beside `actual rows=M`, and a large gap between them means the planner was choosing
between plans using a fiction. The usual cause is columns that are related in a way the
statistics do not record: PostgreSQL 16 assumes conditions on different columns are
independent and multiplies their selectivities.

```
Seq Scan on visit  (cost=0.00..409.00 rows=1250 width=0) (actual rows=5000 loops=1)
      Filter: ((country = 'TR') AND (city = 'Istanbul'))
```

A quarter of a quarter is a sixteenth; the real answer is a quarter, because the city
implies the country. `CREATE STATISTICS ... (dependencies)` tells the planner the two are
related, and after `ANALYZE` the estimate matches. Nothing about the query changed — but on
a plan where that node feeds a join, a four-fold underestimate is how a nested loop gets
chosen over a hash join.

Timings are deliberately left out of the listings, with `TIMING OFF`, because they differ on
every machine. What is being compared is the estimate against the count.

## Common mistakes

**Quoting a cost as a measurement.** Costs are arbitrary units from a forecast. Compare two
plans for the same query with them; do not report them as milliseconds.

**Running `EXPLAIN ANALYZE` on an `UPDATE` casually.** It executes the statement. Wrap it in
a transaction you roll back.

**Treating `Seq Scan` as the problem.** Look first at what the node was estimated to return
and what it actually returned.

**Reading the plan top down.** The top line is the last thing that happens.

**Forgetting `ANALYZE` after a bulk load.** If nobody has collected statistics since the
table was filled, the estimates describe an empty table.

## Check yourself

<details><summary>Where do you start reading a plan?</summary>

At the most-indented node. Those run first and pass rows up to their parents. The root at
the top is the last step, and it is usually the least interesting one.

</details>

<details><summary><code>rows=1250</code> and <code>actual rows=5000</code>. What does that tell you?</summary>

That the planner chose this plan believing the node would return a quarter of what it did.
The plan may still be fine, but every decision above that node was made on a wrong number,
so it is the first place to look when a join method seems bizarre.

</details>

<details><summary>Why does turning off <code>enable_seqscan</code> help you understand a plan?</summary>

It forces the planner to show the alternative it rejected, with its cost. That turns "why
isn't it using my index" into two numbers you can compare. It is a diagnostic, not a
setting to leave on.

</details>

## Listings

1. `reading-a-query-plan-1.sql` — nested loop, hash join and sort, read as a tree.
2. `reading-a-query-plan-2.sql` — the sequential scan that was cheaper, and the proof.
3. `reading-a-query-plan-3.sql` — estimate against actual, and the statistics that fix it.
