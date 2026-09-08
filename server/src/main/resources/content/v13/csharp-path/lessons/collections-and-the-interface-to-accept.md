## Why this exists

Two decisions get made every time a method touches a collection, and they are not the same
decision. The **parameter** type says what a caller must already have; the **return** type says
what the caller may then rely on. Declaring `List<string>` on both sides makes the first
needlessly strict — an array, a `HashSet` or a query cannot be passed without materialising —
and the second needlessly loose, because a caller can now add to the collection the callee is
still using. C# 14 and .NET 10 give a ladder of interfaces between `IEnumerable<T>` and the
concrete types, and picking a rung deliberately is most of what separates an API people can use
from one they work around.

## The idea

A parameter type is a door and a return type is a receipt. The door decides who is allowed
through: a narrow one turns away people who have what you need in a different bag. The receipt
decides what the person leaving is entitled to claim later, and once it is printed you cannot
take it back.

### Where the analogy breaks

A receipt cannot be forged; a return type can. `IReadOnlyList<T>` is an interface a mutable
`List<T>` already implements, so returning the stored list under that name stops nothing — the
second listing casts it straight back to `List<string>` and adds to the repository's own data.
The interface documents intent. It does not enforce it.

The door also lets people through carrying things they have not yet made. `IEnumerable<T>`
accepts a query that has run nothing, so a method that enumerates its parameter twice runs the
caller's work twice; the first listing counts two enumerations where a reader expects one. A
narrower parameter — `IReadOnlyCollection<T>`, which has `Count` — moves that decision back to
the caller, who is the only one who knows what the sequence costs.

And a receipt is one piece of paper. A return type carries a *lifetime* too. Handing back
`_names.AsReadOnly()` refuses writes but keeps a live view: the third block of the second listing
shows the wrapper's `Count` growing after the repository adds an item. A defensive copy does not
do that, and costs an allocation to not do it.

## How it works

Accept the widest interface that carries what the method actually needs. `IEnumerable<T>` is
right when one forward pass is enough; `IReadOnlyCollection<T>` when a count is needed without a
pass; `IReadOnlyList<T>` when indexing is needed; a concrete type only when a member exists
nowhere else.

```csharp
static double AverageOnce(IEnumerable<int> values) { /* one foreach */ }
static int CountCheaply(IReadOnlyCollection<int> values) => values.Count;
```

Return the narrowest thing that is honest. An iterator returning `IEnumerable<T>` re-runs its
body on every enumeration, which is right for a stream and wrong for a repository read; a
materialised `IReadOnlyList<T>` costs one pass whatever the caller does next. Where the promise
matters, a copy keeps it and a wrapper does not:

```csharp
public IReadOnlyList<string> Names()        => _names;                  // castable back
public IReadOnlyList<string> NamesCopy()    => new List<string>(_names); // detached
public IReadOnlyList<string> NamesWrapped() => _names.AsReadOnly();      // live view, refuses writes
```

Variance follows from the same reasoning the interfaces do. `IEnumerable<out T>` is covariant
because it has no member that accepts a `T`, so an `IEnumerable<string>` is safely an
`IEnumerable<object>`. `IList<T>` has `Add(T)`, so it is invariant. Arrays predate generics and
were made covariant anyway, which is why the check happens at run time.

Choose the concrete type on the property you need: `List<T>` for order and duplicates,
`HashSet<T>` for membership and uniqueness, `Dictionary<K,V>` for lookup by key. Neither
`HashSet<T>` nor `Dictionary<K,V>` specifies an enumeration order, so a program must sort before
it depends on one.

## Common mistakes

**Assigning a query to the concrete type it came from.**

```text
Program.cs(2,19): error CS0266: Cannot implicitly convert type 'System.Collections.Generic.IEnumerable<int>' to 'System.Collections.Generic.List<int>'. An explicit conversion exists (are you missing a cast?)
```

**Expecting `IList<T>` to be covariant because `IEnumerable<T>` is.**

```text
Program.cs(2,25): error CS0266: Cannot implicitly convert type 'System.Collections.Generic.IList<string>' to 'System.Collections.Generic.IList<object>'. An explicit conversion exists (are you missing a cast?)
```

**Trying to add through a sequence.** The message says exactly what is missing:

```text
Program.cs(4,6): error CS1061: 'IEnumerable<int>' does not contain a definition for 'Add' and no accessible extension method 'Add' accepting a first argument of type 'IEnumerable<int>' could be found (are you missing a using directive or an assembly reference?)
```

**Storing a `string[]` in an `object[]` and writing to it.** No diagnostic; an
`ArrayTypeMismatchException` at run time.

**Enumerating an `IEnumerable<T>` parameter more than once.** No diagnostic at all, and it is
invisible in review because both passes look like ordinary LINQ. The first listing measures it.

## Check yourself

<details><summary>A method takes <code>IEnumerable&lt;T&gt;</code> and calls <code>.Count()</code> then <code>.Sum()</code>. What has it charged the caller?</summary>

Two full enumerations of whatever the caller passed, including re-running a query or re-reading
a stream. Either enumerate once by hand, or declare `IReadOnlyCollection<T>` and let the caller
decide when to materialise.

</details>

<details><summary>Returning the stored <code>List&lt;T&gt;</code> as <code>IReadOnlyList&lt;T&gt;</code> — what does that actually prevent?</summary>

Nothing at run time. `List<T>` implements the interface, so a cast gets the mutable object back.
A copy or a `ReadOnlyCollection<T>` wrapper is the enforcing version, and they differ: the
wrapper still reflects later changes to the list underneath.

</details>

<details><summary>Why is <code>IEnumerable&lt;out T&gt;</code> covariant while <code>IList&lt;T&gt;</code> is not?</summary>

Covariance is safe only when `T` appears in output positions. `IEnumerable<T>` only produces
values; `IList<T>` also has `Add(T)`, which would let a caller holding an `IList<object>` insert
an `int` into a list of strings.

</details>

## Listings

1. `collections-and-the-interface-to-accept-1.cs` — what the parameter type costs each side.
2. `collections-and-the-interface-to-accept-2.cs` — lazy versus materialised, copy versus wrapper.
3. `collections-and-the-interface-to-accept-3.cs` — choosing the type, and variance.
