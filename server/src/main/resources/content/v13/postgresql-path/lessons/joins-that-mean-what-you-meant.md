## Why this exists

A join produces a wrong answer more quietly than almost anything else in SQL. The query
parses, runs, returns rows, and the total at the bottom of the report is twice what it
should be. Nothing in PostgreSQL 16 can warn you, because every row it returned is a row
you asked for. The two failures in this lesson — a join that multiplied rows, and an outer
join that turned into an inner one — account for a large share of the numbers that leave a
database looking plausible and being wrong.

## The idea

A join is a pairing at a dance. Two lists of people are brought together and the `ON`
clause is the rule for who partners whom. `INNER` keeps only the couples. `LEFT` keeps
every person from the first list, giving the unpartnered ones an empty space where a
partner would be. `FULL` keeps everyone from both.

### Where the analogy breaks

A dance pairs each person once, and a join does not. If three people match the rule, one
person appears three times, once with each. That is not a mistake in the join — it is what
a join is — but it means the number of rows out is not the number of rows in, and any total
computed over those rows counts some values more than once.

The picture also implies the pairing happens and then it is over. In SQL there is a second
step: `WHERE` runs after the pairing, on the paired result. That ordering is why a
condition on the second table can undo an outer join entirely, and it is the second failure
this lesson demonstrates.

## How it works

`ON` decides which rows pair. The outer keyword decides which unpaired rows survive, padded
with unknowns where the missing side's columns would be. The first listing runs all four
over identical data: three rows inner, four left, four right, five full.

Row multiplication happens when one parent has several children on two different sides. An
order with two items and two shipments produces two times two rows, every item amount
appears twice, and `sum` doubles.

```sql
SELECT sum(i.amount) FROM "order" o
JOIN item i     ON i.order_id = o.id
JOIN shipment s ON s.order_id = o.id;   -- 100.00; the order is worth 50.00
```

There is no error and no warning. The symptom is a total that is a clean multiple of the
right answer, which is exactly the kind of wrong that survives review. The diagnosis is to
delete the `GROUP BY` and look at the rows: the duplication is obvious the moment it is not
being summed.

The repair is to make each side one row per parent before joining, either with a subquery
that aggregates first or with `count(DISTINCT ...)` to divide the fan-out back out. The
first is clearer and does not depend on every parent having the same number of children.

```sql
JOIN (SELECT order_id, sum(amount) AS total FROM item     GROUP BY order_id) i ON ...
JOIN (SELECT order_id, count(*)    AS parcels FROM shipment GROUP BY order_id) s ON ...
```

The second failure is about where a predicate goes. "Every author, with their published
posts beside them" is an outer join with the condition in `ON`. Move that condition to
`WHERE` and the padded rows — the authors with nothing to show — hold an unknown in
`p.published`, which is not true, so `WHERE` discards them. The outer join has silently
become an inner one.

```sql
LEFT JOIN post p ON p.author_id = a.id AND p.published    -- 3 rows: every author
LEFT JOIN post p ON p.author_id = a.id WHERE p.published  -- 1 row: an inner join
```

The one predicate that does belong in `WHERE` after an outer join is a test for the padding
itself. `WHERE p.id IS NULL` after a `LEFT JOIN` is how "the rows with no match at all" is
written, and it is only meaningful because the padding is there to test.

## Common mistakes

**Joining two child tables to the same parent and aggregating.** The fan-out multiplies.
Aggregate each child separately first.

**Putting a condition on the outer side in `WHERE`.** It cancels the outer join. Conditions
that decide *which rows pair* go in `ON`; conditions that decide *which result rows
survive* go in `WHERE`, and after an outer join those are rarely the same thing.

**Reading a `LEFT JOIN` as "give me more rows".** It gives you every row of the left side
exactly once for each match, and once with padding when there is no match. The row count can
go up, and it never goes below the left side's count.

**Trusting `count(*)` after a join.** It counts pairings. `count(DISTINCT a.id)` counts
authors.

## Check yourself

<details><summary>A report's total is exactly double. Where do you look first?</summary>

At the join list. Two children of the same parent in one query multiply. Remove the
aggregation and look at the raw rows; a repeated amount beside two different shipment
identifiers is the whole diagnosis.

</details>

<details><summary>Why does moving <code>p.published</code> from <code>ON</code> to <code>WHERE</code> change the answer?</summary>

`ON` is applied while pairing, so unpaired left rows are still added afterwards with
unknowns in the right-hand columns. `WHERE` is applied to that padded result, and an unknown
is not true, so every padded row is discarded. What is left is exactly the inner join.

</details>

<details><summary>How do you find authors with no posts?</summary>

`LEFT JOIN` on the relationship and then `WHERE p.id IS NULL`. This is the one place a
right-hand column belongs in `WHERE` after an outer join: you are testing for the padding
rather than filtering on real data.

</details>

## Listings

1. `joins-that-mean-what-you-meant-1.sql` — inner, left, right and full over one data set.
2. `joins-that-mean-what-you-meant-2.sql` — the total that doubled, and two repairs.
3. `joins-that-mean-what-you-meant-3.sql` — `ON` against `WHERE`, and the anti-join.
