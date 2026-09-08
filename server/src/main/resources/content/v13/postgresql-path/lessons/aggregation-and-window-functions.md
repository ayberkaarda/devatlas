## Why this exists

Two kinds of question look similar and are not. "What did each region sell?" collapses many
rows into one per region, and `GROUP BY` does that. "What was the running total after each
sale, and what share of its region was this one?" needs every row kept *and* a figure
computed across its neighbours. Doing the second by fetching rows into application code and
looping is the usual answer, and it turns one query into thousands of round trips.
PostgreSQL 16 computes both in one pass over the data.

## The idea

`GROUP BY` is sorting a pile of receipts into envelopes and writing a total on each
envelope; what you keep is the envelopes. A window function is walking along the receipts
still laid out in a row, with a ruler that covers some of them, and writing a figure on
each receipt as you pass. The receipts stay. The ruler is the window, and `OVER` is where
you say how wide it is.

### Where the analogy breaks

A ruler covering "some of them" suggests a fixed span, and the default is not fixed. When a
window has an `ORDER BY` and no explicit frame, PostgreSQL 16 uses `RANGE BETWEEN UNBOUNDED
PRECEDING AND CURRENT ROW`, and `RANGE` is defined by *value*, not position: every row whose
ordering value ties with the current one is inside the frame. On a ledger ordered by day
with two entries on the same day, both entries get the same running total. The ruler jumped
past a receipt you were standing on.

The picture also suggests the walk happens after the grouping. It does not — window
functions are evaluated after `WHERE`, `GROUP BY` and `HAVING`, but before `ORDER BY` and
`LIMIT`, which is why `WHERE` cannot see one and why filtering on a ranking needs a subquery.

## How it works

`GROUP BY` collapses. Every column in the select list must therefore be grouped or
aggregated, because there is no single answer for a column that varied within the group;
PostgreSQL 16 refuses rather than picking one.

```
ERROR:  column "sale.seller" must appear in the GROUP BY clause or be used in an aggregate function
```

`WHERE` filters rows before grouping and `HAVING` filters groups after. They are not
interchangeable: `WHERE amount >= 50` removes small sales and can remove a whole region;
`HAVING sum(amount) >= 50` keeps every sale and removes regions whose total is small. An
aggregate in `WHERE` is rejected outright, because at that point no groups exist.

Adding `OVER` to the same aggregate keeps the rows instead of collapsing them.

```sql
sum(amount) OVER (ORDER BY day, id)                            -- running total
sum(amount) OVER (PARTITION BY day)                            -- that day's total, on every row
sum(amount) OVER (ORDER BY day ROWS UNBOUNDED PRECEDING)       -- running total, by position
```

The second listing puts the `RANGE` and `ROWS` versions side by side on a ledger with a
tied day. `RANGE` gives both entries on that day 60.00; `ROWS` gives 30.00 and 60.00. Both
are correct answers to different questions, and only one of them is what "running total"
usually means. Say `ROWS` when you mean positions, or order by something unique.

The ranking functions differ in how they treat ties. `rank` leaves a gap after a tie,
`dense_rank` does not, and `row_number` refuses to tie at all — which is why its window has
to order by something unique, or the assignment among tied rows is arbitrary.

```
 seller | amount | rank | dense_rank | row_number
 ada    | 100.00 |    1 |          1 |          1
 bo     | 100.00 |    1 |          1 |          2
 cyd    |  40.00 |    3 |          2 |          3
```

Because window functions are evaluated after `WHERE`, "the top sale per region" is written
in two steps: compute the numbering in a subquery, then filter its result.

`lag` and `lead` reach into neighbouring rows of the same window. At a partition boundary
there is no neighbour, and the result is unknown rather than zero — which propagates through
any arithmetic done with it, exactly as the third lesson of the first module described.

## Common mistakes

**Selecting an ungrouped column.** The error names the column. Either group by it or decide
which value of it you meant and say so with an aggregate.

**Putting an aggregate in `WHERE`.** `aggregate functions are not allowed in WHERE`. Use
`HAVING`, or a subquery if the filter is on something else entirely.

**Using the default frame for a running total over a non-unique ordering.** The listing
shows it: ties share a total. Add `ROWS`, or add a unique tiebreaker to the window's
`ORDER BY`.

**Filtering on `row_number()` in `WHERE`.** `window functions are not allowed in WHERE`.
Wrap it in a subquery.

**Ordering a `row_number()` window by a column with ties.** The numbering among tied rows is
not defined, so the same query can return a different "top" row on a different run.

## Check yourself

<details><summary>When is <code>HAVING</code> right and <code>WHERE</code> wrong?</summary>

When the condition is about the group rather than the row. `WHERE amount >= 50` throws away
small sales before any total is computed, so a region's total changes and a region can
disappear. `HAVING sum(amount) >= 50` computes the true totals and then removes the small
groups.

</details>

<details><summary>Two rows share a day and both show the same running total. What happened?</summary>

The default frame is `RANGE`, which includes every row whose ordering value ties with the
current one. Both entries for that day are inside each other's frame. `ROWS BETWEEN
UNBOUNDED PRECEDING AND CURRENT ROW` counts positions instead.

</details>

<details><summary>Why can a window function not appear in <code>WHERE</code>?</summary>

Because it is computed after `WHERE` has already chosen the rows the window is defined over.
Allowing it would make the definition circular. Compute it in a subquery and filter the
result of that.

</details>

## Listings

1. `aggregation-and-window-functions-1.sql` — grouping, its error, and `WHERE` against `HAVING`.
2. `aggregation-and-window-functions-2.sql` — a running total, and the frame that swallowed a tie.
3. `aggregation-and-window-functions-3.sql` — three numberings of a tie, and filtering on one.
