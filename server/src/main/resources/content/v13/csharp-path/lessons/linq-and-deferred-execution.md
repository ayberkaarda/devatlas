## Why this exists

`numbers.Where(IsEven)` looks like a function call that returns the even numbers. It is not. It
returns an object that *knows how* to produce the even numbers, and produces none of them until
something enumerates it. Almost every surprise LINQ causes comes from that one sentence: a
predicate that runs twice, a query that gives a different answer the second time it is read, a
database round trip that happens at the point the collection is printed rather than the point it
was written. C# 14 and .NET 10 keep this design because it is what makes a chain of operators
cost one pass instead of one pass per operator — and the price is that a reader must know when
the pass happens.

## The idea

A LINQ query is a recipe card, not a meal. Writing `Where(...).Select(...)` is writing down
instructions. Handing the card to `foreach`, `ToList`, `Count` or `First` is the cooking. Hand
the same card to two different cooks and the kitchen gets used twice.

### Where the analogy breaks

A recipe is fixed once written; a query is not. It holds references to the source collection and
to any variable its lambda captured, and it reads both at cooking time. The second listing
changes a `threshold` variable between two enumerations of the *same* query object and gets two
different answers. Nothing about the query changed; the world it reads did.

A recipe is also followed from start to finish. A query is pulled from the far end, one element
at a time, and stops the moment the caller stops asking. `First()` on a `Where` over a
`Select` runs the projection exactly once — the third listing measures it — because nothing
downstream ever asked for a second element.

And some steps cannot work that way. `OrderBy` and `GroupBy` cannot yield their first result
before they have read their last input, so they buffer the whole source. The same `First()` that
cost one projection through `Where` costs six through `OrderBy`. Both facts follow from what the
operator means, not from how it happens to be written.

## How it works

Operators fall into three groups. **Deferred and streaming** — `Where`, `Select`, `Take`,
`SelectMany`, `Skip` — produce elements as they are pulled. **Deferred and buffering** —
`OrderBy`, `GroupBy`, `ToLookup` — defer until enumerated and then read everything, because
their first result depends on their last input. **Immediate** — `ToList`, `ToArray`, `Count`,
`Sum`, `First`, `Any` — enumerate on the spot and return data rather than a query.

```csharp
var evens = numbers.Where(IsEven);   // nothing has run
var a = evens.ToList();              // one pass
var b = evens.ToList();              // a second pass, from scratch
```

The query object holds no results, so every terminal operator applied to it starts again. Three
uses of one query is three passes over the source; three uses of the `List` it produced is none.
That is the whole reason `ToList` exists.

The edge cases of the terminal operators are worth memorising, because they differ on purpose:

```csharp
Array.Empty<int>().FirstOrDefault();   // 0
Array.Empty<int>().First();            // throws
source.Where(n => n > 4).Single();     // throws when there is more than one
source.Where(n => n > 4).SingleOrDefault(); // also throws when there is more than one
Array.Empty<int>().Sum();              // 0
Array.Empty<int>().Max();              // throws
```

Order is guaranteed only where the operator promises it. `OrderBy` is documented as a stable
sort, so equal keys keep their source order, and `GroupBy` yields groups in the order their keys
first appeared. Nothing promises anything about the order in which `Distinct` or a dictionary
lookup returns things, so a program must not depend on it.

## Common mistakes

**Assigning a query to the collection type you started with.**

```text
Program.cs(2,19): error CS0266: Cannot implicitly convert type 'System.Collections.Generic.IEnumerable<int>' to 'System.Collections.Generic.List<int>'. An explicit conversion exists (are you missing a cast?)
```

**Trying to change a query.** A query is not a collection, and the diagnostic says why the
member is not there:

```text
Program.cs(4,6): error CS1061: 'IEnumerable<int>' does not contain a definition for 'Add' and no accessible extension method 'Add' accepting a first argument of type 'IEnumerable<int>' could be found (are you missing a using directive or an assembly reference?)
```

**Losing `System.Linq`.** The operators are extension methods, so without the namespace they
simply are not there:

```text
Program.cs(4,21): error CS1061: 'List<int>' does not contain a definition for 'Where' and no accessible extension method 'Where' accepting a first argument of type 'List<int>' could be found (are you missing a using directive or an assembly reference?)
```

**Adding to the source while enumerating a query over it.** No compiler diagnostic; an
`InvalidOperationException` at run time, from the collection's own enumerator.

**Passing a query around and enumerating it in several places.** No diagnostic at all. The first
listing shows fourteen predicate calls where a reader expects six.

## Check yourself

<details><summary>The same query object is enumerated twice and gives two different answers. What changed?</summary>

Either the source collection or a variable the lambda captured. The query holds references to
both and reads them at enumeration time, so it reports the world as it is when it is asked, not
as it was when it was written.

</details>

<details><summary>Why does <code>First()</code> after <code>OrderBy</code> read the whole source when <code>First()</code> after <code>Where</code> stops early?</summary>

`Where` can decide element by element. `OrderBy` cannot know which element is first until it has
seen every element, so it buffers. The cost difference follows from the meaning of the operator.

</details>

<details><summary>When is <code>ToList()</code> the right call, and when is it premature?</summary>

Right when the results will be used more than once, when the source is about to change, or when
the query holds a resource that should be released. Premature when a single `foreach` or a
single `Any()` follows, because materialising then allocates a list nobody needs and gives up
the early exit.

</details>

## Listings

1. `linq-and-deferred-execution-1.cs` — counting when the predicate actually runs.
2. `linq-and-deferred-execution-2.cs` — the source and the captured variable, read late.
3. `linq-and-deferred-execution-3.cs` — streaming, buffering, and the terminal operators' edges.
