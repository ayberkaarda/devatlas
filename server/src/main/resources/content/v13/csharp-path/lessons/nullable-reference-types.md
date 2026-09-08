## Why this exists

`NullReferenceException` is the failure that tells you least. It names no field, no argument and
no caller; it says only that something was null somewhere on the line. Before C# 8 the type
system had nothing to say about it, because every reference type accepted `null` and no
signature could refuse one. C# 14 keeps that runtime behaviour exactly — it has to, or every
existing assembly would break — and adds a compile-time analysis on top. In a project with
`<Nullable>enable</Nullable>`, `string` means "I do not expect null here" and `string?` means "I
do", and the compiler warns wherever the code disagrees with itself. The value is not that null
becomes impossible. It is that every place null can arrive is now written down.

## The idea

The annotations are a colour-coded floor plan pinned to the wall of a building. It marks which
rooms are safe to walk into without a torch and which are not, and the safety officer checks
your route against it before you set off. The plan is genuinely useful and genuinely accurate
about what the architect intended.

### Where the analogy breaks

The plan is not the building. Nothing is bolted to a door; a person who ignores the plan walks
straight in. `string` and `string?` are the **same runtime type** — `typeof(string?)` is not
even legal to write — and no check is emitted at a non-nullable parameter. A `null` that arrives
from a library compiled without the feature, from deserialisation, or from a `null!` an author
wrote to silence a warning, arrives without a sound.

The plan is also read by one office only. A project compiled with nullable annotations disabled
sees no plan at all, and its callers into your code are unchecked. That is why the first listing
shows a `NullReferenceException` thrown from a method whose parameter is declared non-nullable:
the signature was a claim, not a barrier.

And the officer checks routes, not rooms. The analysis is flow-based: it tracks what a variable
could be at each point given the tests you wrote. Assign to the variable from a lambda, pass it
by `ref`, or hide the check behind a helper method, and the tracking stops — not because the
value changed, but because the analysis lost sight of it.

## How it works

A test narrows a variable for the rest of the branch, and the operators do the rest:

```csharp
string? maybe = Read();
if (maybe is not null)
{
    Console.WriteLine(maybe.Length);   // no warning: narrowed to string
}

Console.WriteLine(order?.Customer.Name);   // whole chain short-circuits
slot ??= "first";                          // assigns only when null
var text = maybe ?? Fallback();            // right side evaluated only when null
```

Where a method's guarantee cannot be written in the signature, an attribute states it.
`[NotNullWhen(true)]` on an `out` parameter is what makes the `Try` pattern usable without a
warning at every call site, and `[MemberNotNullWhen]` does the same for a field a property
vouches for:

```csharp
static bool TryGet(Dictionary<string, string> src, string key,
                   [NotNullWhen(true)] out string? value)
    => src.TryGetValue(key, out value);
```

Nullable *value* types are a different mechanism with a similar spelling. `int?` is
`Nullable<int>` — a real, distinct type with `HasValue` and lifted arithmetic, where any null
operand yields null. `string?` is `string` with an annotation the compiler erases.

That difference bites in generics: for an unconstrained `T`, `T?` means "may be `default`", and
`default` is null only when `T` happens to be a reference type. The third listing prints
`'0'` for `T = int` and `none` for `T = string` from the same method.

## Common mistakes

**Dropping `[NotNullWhen(true)]` from a `Try` method.** The method still works; every caller now
carries a warning it will be tempted to suppress:

```text
Program.cs(4,23): warning CS8602: Dereference of a possibly null reference.
```

**Passing a literal null to a non-nullable parameter.**

```text
Program.cs(7,7): warning CS8625: Cannot convert null literal to non-nullable reference type.
```

**Passing a `string?` where a `string` is declared** — the same mistake one step removed, and
the compiler names the parameter and the method:

```text
Program.cs(2,7): warning CS8604: Possible null reference argument for parameter 'text' in 'void Shout(string text)'.
```

**Leaving a non-nullable property with no value after construction.**

```text
Program.cs(10,28): warning CS8618: Non-nullable property 'Name' must contain a non-null value when exiting constructor. Consider adding the 'required' modifier or declaring the property as nullable.
```

**Reaching for `!` to make a warning go away.** It suppresses the diagnostic and emits nothing.
The first listing's `NullReferenceException` is what that costs.

## Check yourself

<details><summary>Why is <code>typeof(string?)</code> a compile error while <code>typeof(int?)</code> is fine?</summary>

`int?` is `Nullable<int>`, a distinct runtime type. `string?` is `string` plus an annotation the
compiler erases, so there is no separate type for `typeof` to name. The compiler rejects it
(CS8639) rather than quietly returning `typeof(string)`.

</details>

<details><summary>A method declares <code>string text</code> and receives null anyway. Where could it have come from?</summary>

From a `null!` at the call site, from an assembly compiled without nullable annotations, from
deserialisation or reflection, or from any path the flow analysis could not follow. Annotations
generate no runtime check; `ArgumentNullException.ThrowIfNull` does.

</details>

<details><summary>For an unconstrained <code>T</code>, why does <code>T? FirstOrNone&lt;T&gt;</code> not give the caller a usable "absent"?</summary>

`T?` there means "may be `default`". For `T = int` that is `0`, an ordinary value indistinguishable
from a real one. A `Try` method with a `bool` result — or a dedicated option type — says "absent"
for every `T`.

</details>

## Listings

1. `nullable-reference-types-1.cs` — what is erased, and the exception a non-nullable parameter still throws.
2. `nullable-reference-types-2.cs` — narrowing, `?.`, `??`, `??=` and lifted arithmetic.
3. `nullable-reference-types-3.cs` — `NotNullWhen`, `MemberNotNullWhen`, and `default` in a generic.
