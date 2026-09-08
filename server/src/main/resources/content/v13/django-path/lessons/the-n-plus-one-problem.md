## Why this exists

The N+1 problem is the most common performance bug in ORM code and the easiest
one to write without noticing, because the code that causes it is the clearest
code you could write. A loop over books that reads `book.author.name` looks like
a loop over books. It is one query for the books and one more for every single
one of them. On five rows in development you will never see it; on fifty
thousand in production it is the whole outage.

The advice — "use `select_related`" — is repeated without the measurement that
makes it useful, and applied where it does nothing. This lesson counts.

## The idea

Fetching related rows one at a time is like going back to the archive for each
folder. You know the shelf numbers because they are written on the list you are
holding, but you walk back for every single one. `select_related` is asking the
archivist to bring the folders attached to what you asked for, in the same trip.
`prefetch_related` is a second trip, once, for every folder anyone on the list
will need.

### Where the analogy breaks

A second trip to an archive is straightforwardly worse than one. In a database
it often is not: `prefetch_related` deliberately runs two queries rather than
one, because joining a many-valued relation would return the parent row once per
child and Django would have to unpick that. Two clean queries beat one that
multiplies rows. The measurements in listing 3 are `2` for the prefetch and `11`
for the loop, and the right comparison is against eleven, not against one.

The archivist also cannot help with folders you never open. Listing 2 records
`select_related('author')` costing one query when the loop reads `author.name`,
and still one query when the loop reads only titles — the join happened either
way. The saving is real only where the access is real.

## How it works

Start by measuring. Listing 1 varies the row count and confirms the shape:

```
books=5  queries=6   equals 1 + N: True
books=10 queries=11  equals 1 + N: True
books=20 queries=21  equals 1 + N: True
```

For a relation the row points *at* — a `ForeignKey` or `OneToOneField` — a join
gets it in the first query, and the path may go deeper than one step:

```python
Book.objects.select_related("author")             # 1 query
Book.objects.select_related("author__publisher")  # 1 query
```

Listing 2 measures `select_related("author")` at 1 for `author.name` and at 11
when the loop reaches `author.publisher.name`: the join went one level, the code
went two.

For a relation that points *back* — a reverse foreign key or a `ManyToManyField`
— `prefetch_related` runs a second query and joins in Python:

```python
Author.objects.prefetch_related("books")   # 2 queries, was 11
Book.objects.prefetch_related("tags")      # 2 queries, was 11
```

Django 6.1 adds a third option that needs no relation named in advance. A
queryset's fetch mode says what an unfetched field should do, and listing 1
measures all three:

```
fetch_mode(FETCH_ONE), the default: 11
fetch_mode(FETCH_PEERS):             2
fetch_mode(FETCH_RAISE):             FieldFetchBlocked after 1 query
```

`FETCH_PEERS` fetches the missing field for every instance of the originating
queryset at once. `FETCH_RAISE` turns a silent N+1 into an exception, which
makes it a measuring instrument rather than an optimisation.

## Common mistakes

**Filtering inside the loop after prefetching.** `b.tags.all()` uses the
prefetched cache; `b.tags.filter(...)` is a new queryset and goes back to the
database. Listing 4 measures 2 against 12.

**`only()` next to a related access.** Deferring `author_id` means Django must
load it *and then* the author. Listing 4 measures 21 queries against
`select_related`'s 1.

**Counting related rows in a loop.** `a.books.count()` per author is 11
queries; `annotate(Count("books"))` is 1. Listing 4 shows both, and shows they
agree.

**Optimising a queryset nobody kept.** `Book.objects.select_related("author")`
on a line of its own returns a new queryset and changes nothing about the one
the loop uses. Listing 4 measures it at 11.

**Assuming `FETCH_PEERS` covers everything.** Listing 1 measures 11 for a loop
over a reverse many relation under `FETCH_PEERS`: asking a related manager for
rows is a query in its own right.

## Check yourself

<details><summary>When does <code>select_related</code> do nothing?</summary>

When the loop never touches the related object, and when the relation is
many-valued. Listing 2 measures the first; `prefetch_related` is the answer to
the second.

</details>

<details><summary>Why is <code>prefetch_related</code> two queries rather than one?</summary>

Because joining a many-valued relation repeats the parent row per child. Two
queries and a join in Python avoid that. Against the eleven it replaces, two is
the win.

</details>

<details><summary>You prefetched <code>tags</code> and the page is still slow. What would you check first?</summary>

Whether the loop calls `.all()` or something else. `.filter()`, `.exclude()`
and `.order_by()` inside the loop all bypass the prefetched cache — listing 4
measures 12 queries where `.all()` gives 2.

</details>

## Listings

1. `the-n-plus-one-problem-1.py` — measuring 1 + N, and Django 6.1's fetch modes.
2. `the-n-plus-one-problem-2.py` — `select_related`, and how deep it went.
3. `the-n-plus-one-problem-3.py` — `prefetch_related` for reverse and
   many-to-many relations.
4. `the-n-plus-one-problem-4.py` — four ways to put the queries back.
