## Why this exists

"QuerySets are lazy" is repeated far more often than it is checked, and the
half-understood version causes real bugs in both directions. Someone assigns a
queryset to a variable and believes they have the rows; they have a description.
Someone else calls a helper three times in a template and believes the ORM
caches it; it does not, and the page runs three queries. The fix for both is the
same and it is not a rule of thumb: Django can tell you exactly how many queries
a piece of code ran, so stop guessing and count.

## The idea

A queryset is a shopping list, not the shopping. You can add to it, cross things
off and reorder it as long as you like, and none of that is a trip to the shop.
The trip happens the moment somebody asks what is actually in the bag. And once
you have been, the bag stays packed: asking again about the same bag costs
nothing.

### Where the analogy breaks

Copying a shopping list gives you a second list; adding one item to a queryset
gives you a whole new queryset with an empty bag. `held.filter(...)` does not
narrow `held` — it returns a different object that has never been to the shop.
Listing 2 shows the filtered one costing a query while the original stays
cached.

The bag is also less reliable than it looks. `count()` and `exists()` go to the
shop and come back with an answer rather than goods, so a `count()` followed by
a `len()` on the same queryset is two trips — listing 2 measures exactly that.
And a bag, once packed, never notices that the shop restocked; a cached queryset
serves rows from the moment it was evaluated, however long ago that was.

## How it works

Building costs nothing. Listing 1 counts zero queries for chaining `filter()`,
`exclude()` and `order_by()`, zero for a slice without a step, and zero for
inspecting `.query`.

```python
pending = Article.objects.filter(words__gt=50).exclude(headline="a09")
pending[:3]        # still zero queries
str(pending.query) # still zero
```

Evaluation happens when the results are needed: iteration, `list()`, `len()`,
`bool()`, indexing, `repr()`, and a slice with a step. Each of those is one
query in listing 1.

The result cache is per queryset object. Listing 2's numbers:

```
first iteration of a held queryset: 1
second iteration of the same object: 0
len() once it is cached: 0
the same filter written again: 1
filtering the cached one produces a new query: 1
```

`count()` and `exists()` deliberately sit outside that. On an unevaluated
queryset each costs a query and neither fills the cache, so listing 2 records
`count()` as 1 and the following `len()` as another 1. On an *already cached*
queryset `count()` costs 0, because the rows are in hand.

Query count is not the whole cost. Listing 3 counts model instances too, using
the `post_init` signal, and finds that on twenty rows `len(qs) > 0` builds
twenty `Article` objects while `count()` and `exists()` build none — one query
each, wildly different work:

```
len(qs) > 0:    queries=1 instances=20
qs.count() > 0: queries=1 instances=0
qs.exists():    queries=1 instances=0
```

## Common mistakes

**A helper that returns a fresh queryset, called several times.** Listing 3
measures three queries where one would do, and one line — assigning to a list —
fixes it.

**`len(qs)` to ask whether anything matched.** It fetches and builds every
matching row to answer a yes/no question. `exists()` is the same query count and
none of the objects.

**Believing a filter narrows in place.** It returns a new queryset. The original
keeps its cache and its rows.

**Trusting a long-lived queryset.** Once evaluated it never refreshes. In a
management command that runs for minutes, that cache can be badly out of date.

## Check yourself

<details><summary>How many queries does <code>qs = Model.objects.filter(...)</code> run?</summary>

None. Listing 1 counts zero for assignment, for three chained calls, for a slice
without a step and for reading `.query`.

</details>

<details><summary>Why is <code>qs.count()</code> then <code>len(qs)</code> two queries?</summary>

`count()` asks the database for a number and does not populate the result cache,
so `len(qs)` still has to fetch the rows. Listing 2 measures 1 and then 1. In the
other order it is 1 and then 0.

</details>

<details><summary>Same query count, different cost — where?</summary>

`len(qs) > 0` and `qs.exists()` are one query each, but listing 3 counts twenty
model instances built for the first and none for the second.

</details>

## Listings

1. `querysets-are-lazy-1.py` — zero queries while building, one when asked.
2. `querysets-are-lazy-2.py` — the result cache, and what deliberately ignores it.
3. `querysets-are-lazy-3.py` — the same rows fetched twice, and instances counted.
