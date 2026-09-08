## Why this exists

Most of the types in an application are not objects with behaviour; they are values being
carried from one place to another — a price, an address, a parsed request. Written as an
ordinary class, such a type needs a constructor, a set of read-only properties, `Equals`,
`GetHashCode`, `ToString` and some way to produce a modified copy. That is roughly eighty lines
of code with no decisions in it, and every one of those lines is a place to make a mistake: an
`Equals` that forgets a field, a `GetHashCode` that disagrees with it, a copy method that misses
the new property somebody added last week. C# 14's `record` generates all of it from the
declaration, and `with` generates the modified copy.

## The idea

A record is a printed form with a photocopier beside it. You never rub anything out. To change
the address you copy the form, write the new address in that field as the copy comes out, and
keep both sheets. Two forms are "the same form" when every field on them reads the same, not
because they are the same piece of paper.

### Where the analogy breaks

A photocopier copies everything on the page. `with` copies each **member**, and a member that
is a reference is copied as a reference — so the copy and the original share whatever it points
at. The second listing shows two baskets with different owners adding to one list. This is a
shallow copy and the documentation calls it that; the word "immutable" attached to records
describes the *properties*, not the graph beneath them.

The same shallowness reaches equality. The generated `Equals` compares members with each
member's own `Equals`, so a record holding a `List<string>` compares that list by reference, and
two records with identical contents are unequal. A record holding only strings, numbers and
other records behaves the way the analogy promises; a record holding a collection does not,
until someone writes an `Equals` for it.

And the form has a hidden field. A derived record's generated `Equals` first compares a
synthesised `EqualityContract`, so a `Shape` and a `Circle` are never equal even when every
shared member matches — in both directions. That is the right answer, and it surprises people
who expect base-typed comparison to consider only base-typed members.

## How it works

A positional record declares its members in the header, and the compiler generates the
constructor, init-only properties, `Deconstruct`, `Equals`, `GetHashCode`, `==`, `!=` and a
`ToString` of the documented form `TypeName { Member = value, ... }`:

```csharp
record Money(decimal Amount, string Currency);

var a = new Money(12.50m, "EUR");
var b = a with { Amount = 20m };   // a is unchanged
var (amount, currency) = a;        // Deconstruct
```

`with` calls a generated copy constructor and then applies the initialiser, so it always
produces a new instance — `ReferenceEquals(a, a with { })` is false while `a == (a with { })`
is true. On a record class the copy constructor is virtual, which is why `with` through a
base-typed variable returns the derived runtime type.

`record struct` applies the same generation to a value type. `readonly record struct` makes the
generated properties init-only; a plain `record struct` leaves them settable, which is usually
not what a value type wants.

Deconstruction makes records fit pattern matching directly:

```csharp
static string Describe(Money m) => m switch
{
    (0m, var cur) => $"nothing in {cur}",
    ( < 0m, var cur) => $"a debt in {cur}",
    var (amt, cur) => $"{amt} in {cur}",
};
```

## Common mistakes

**Assigning to a positional record's property.** The generated properties are `init`:

```text
Program.cs(2,1): error CS8852: Init-only property or indexer 'Money.Amount' can only be assigned in an object initializer, or on 'this' or 'base' in an instance constructor or an 'init' accessor.
```

**Using `with` on an ordinary class.** There is no generated copy constructor to call:

```text
Program.cs(5,9): error CS8858: The receiver type 'Plain' is not a valid record type and is not a struct type.
```

**Putting a `List<T>` in a record and expecting value equality.** No diagnostic at all. Two
records with identical contents compare unequal, and a `HashSet` of them keeps duplicates. The
second listing prints `False` for exactly that case.

**Treating `with` as a deep copy.** Also no diagnostic. The copy and the original share every
mutable member, so a mutation through either is visible through both.

## Check yourself

<details><summary>Why is <code>a == (a with { })</code> true while <code>ReferenceEquals(a, a with { })</code> is false?</summary>

`with` always constructs a new instance through the generated copy constructor, so the
references differ. The generated `==` compares members, and every member was copied unchanged,
so the values are equal.

</details>

<details><summary>A <code>Shape</code> and a <code>Circle</code> have the same <code>Colour</code>. Why are they not equal?</summary>

The generated `Equals` compares the synthesised `EqualityContract` before any member, and the
two types report different contracts. The comparison is symmetric — neither direction returns
true — which is what keeps the relation well-behaved in a hash set.

</details>

<details><summary>When does a record <em>not</em> give you value equality?</summary>

When a member does not have value equality itself. A `List<T>` member is compared by reference,
so identical contents are unequal. Replace it with a record, an immutable value, or a type that
implements `IEquatable<T>` and a matching `GetHashCode`.

</details>

## Listings

1. `records-and-with-1.cs` — what a positional record generates, and `with`.
2. `records-and-with-2.cs` — the shallow copy, and equality through a member.
3. `records-and-with-3.cs` — `record struct`, and the equality contract under inheritance.
