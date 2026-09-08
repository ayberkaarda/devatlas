## Why this exists

`account.Balance` and `account.balance` read identically at the call site, and that is the whole
problem. One is a field — a slot of memory, readable and writable by anyone who can see it, with
no way to validate, log, compute or refuse. The other is a property: a `get` method, an optional
`set` method, and a name that makes them look like storage. C# 14 makes the property cost nothing
to write — `public int Balance { get; set; }` is one line — and that cheapness is the point. The
type's public surface becomes a set of methods from the first day, so the day validation is
needed, or the value has to be derived rather than stored, or an interface has to require it,
nothing outside the type changes.

## The idea

A property is a service counter with a shutter, and a field is a hole in the wall. Both let you
hand things in and take things out. Only one has somebody standing behind it who can look at
what you brought, write it in a ledger, refuse it, or fetch the answer from the back room
instead of a shelf.

### Where the analogy breaks

A counter is obviously a counter; you can see the clerk. A property is invisible at the call
site — `x.Total` gives no hint whether it reads a field or runs a database query — and that
invisibility is what turns "the getter runs every time" into a real bug. The third listing's
loop calls a getter seven times where a reader counts three, and a getter that builds a fresh
`List` on each read silently discards the caller's `Add`.

The clerk also hands you the item itself. A property hands you a **value**. For a class that
value is a reference, so mutating what came back is visible; for a `struct` it is a copy, and
mutating it is either rejected by the compiler or lost. This is why a struct-typed property
needs a read-modify-write rather than an in-place edit.

And a counter can be pointed at. A property cannot: it has no address, so it cannot be passed
by `ref` or `out`, and `Interlocked` operations that need a variable cannot be applied to one.

## How it works

A property is one or two accessor methods. Reflection sees exactly that: `GetProperty("Balance")`
finds it, `GetField("Balance")` does not, and `GetMethod`/`SetMethod` are ordinary `MethodInfo`
values. An interface can therefore require a property and never a field.

An auto-property gets a compiler-written backing field. C# 14 adds a contextual keyword so an
accessor can name that field without declaring one:

```csharp
public double Celsius
{
    get => field;
    set => field = value < -273.15 ? -273.15 : value;
}
```

`init` allows exactly one assignment, in an object initialiser or a constructor. `required` makes
the compiler insist the initialiser sets it, which removes the "non-nullable property with no
value" warning without a fake default:

```csharp
public required string Host { get; init; }
public int Port { get; init; } = 443;
```

A computed property has no storage at all — `public int Total => _prices.Sum();` — so it cannot
disagree with what it derives from. That is the version of encapsulation that costs nothing: the
caller's code is identical whether `Total` is stored or computed, so the decision can be changed
later.

## Common mistakes

**Mutating a struct that a property returned.**

```text
Program.cs(2,1): error CS1612: Cannot modify the return value of 'Box.Size' because it is not a variable
```

**Passing a property by `ref` or `out`.**

```text
Program.cs(3,11): error CS0206: A non ref-returning property or indexer may not be used as an out or ref value
```

**Declaring a field called `field` and reading it from inside an accessor.** In C# 14 the bare
name now binds to the synthesised backing field, and the compiler says so and names the language
version:

```text
Program.cs(12,20): warning CS9258: In language version 14.0, the 'field' keyword binds to a synthesized backing field for the property. To avoid generating a synthesized backing field, and to refer to the existing member, use 'this.field' or '@field' instead.
```

**Forgetting to set a `required` member.**

```text
Program.cs(1,13): error CS9035: Required member 'Config.Host' must be set in the object initializer or attribute constructor.
```

**Returning a fresh collection from a getter and expecting `Add` to stick.** No diagnostic. The
third listing prints `0` after adding a tag, because the list the caller mutated was thrown away
the moment the getter returned.

## Check yourself

<details><summary>Why can an interface declare <code>string Name { get; }</code> but never a field?</summary>

A property is a pair of methods, and an interface declares methods. A field is storage, and an
interface has no storage to declare. This is the practical reason properties are the default
public surface even when the value is simply stored.

</details>

<details><summary>A <code>for</code> loop uses <code>counted.Limit</code> as both its bound and part of its body, and runs three times. Why does the getter run seven times?</summary>

Four condition evaluations — one before each of the three iterations and one that ends the loop
— plus three reads inside the body. Each read is a method call. Hoisting the value into a local
takes it to one.

</details>

<details><summary>When is exposing the stored <code>List&lt;T&gt;</code> from a getter the wrong answer, and what is the alternative?</summary>

When the type needs to know about changes, or to stay valid. Returning a copy loses the caller's
mutations silently; returning the stored list gives away control. The honest options are an
`IReadOnlyList<T>` property plus explicit `Add`/`Remove` methods, which is what the last module's
lesson on collections settles.

</details>

## Listings

1. `properties-are-not-fields-1.cs` — reflection, accessors, refusal, and the interface.
2. `properties-are-not-fields-2.cs` — auto-properties, the `field` keyword, `init` and `required`.
3. `properties-are-not-fields-3.cs` — the getter that runs every time, and the value it returns.
