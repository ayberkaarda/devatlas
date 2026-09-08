## Why this exists

Most of the types in an application exist only to carry a few values from one place to
another, and writing them out by hand is a chore with a failure mode: `equals` and
`hashCode` get written once, then a field is added and only one of the two is updated, and a
value quietly stops being findable in the set it was put into. Kotlin 2.4's `data` modifier
asks the compiler to derive `equals`, `hashCode`, `toString`, `componentN` and `copy` from
the primary constructor, so the derivation cannot fall out of step with the fields. The
saving is not the typing. It is that there is no longer a second place to forget.

## The idea

A data class is a labelled tuple. You supply the labels and the types; the compiler supplies
the paperwork that makes a tuple behave like a value — comparison by contents, a printable
form, positional access, and a way to make a near-copy.

### Where the analogy breaks

A tuple is entirely its contents. A data class is not, and the gap is exactly where the
mistakes live.

Only the primary constructor parameters count. A property declared in the class body is a
real property with a real value, and `equals`, `hashCode`, `copy` and `componentN` all
ignore it. Two orders with the same `id` and different `status` compare equal, and `copy()`
resets the status to whatever the initialiser says. The second listing prints both facts.

A tuple's contents are compared by value. A data class compares its properties using
*their* `equals`, and for a JVM array that is identity. A `data class Payload(val bytes:
ByteArray)` therefore reports two payloads holding identical bytes as unequal, and Kotlin
2.4 issues no warning about it. Comparing contents needs `contentEquals` inside a
hand-written `equals`.

And a tuple has no behaviour. A data class is an ordinary class as well: it can implement
interfaces, hold functions, and validate in an `init` block. `data` adds members; it does
not take the class away.

## How it works

The declaration is one word longer than an ordinary class, and everything below follows from
the primary constructor:

```kotlin
data class Money(val amount: Int, val currency: String)

val price = Money(1250, "EUR")
val discounted = price.copy(amount = 999)   // currency carried over
val (amount, currency) = price              // component1(), component2()
```

`copy` names only what changes, which is what makes an immutable data model bearable to work
with. Destructuring is `componentN` in disguise, so it is strictly positional: the names on
the left are yours to choose and are not matched against the property names.

Because `equals` and `hashCode` are derived together and agree, a data class works as a set
element and a map key without further effort:

```kotlin
val seen = setOf(Point(1, 2), Point(1, 2), Point(3, 4))   // two distinct points
val counts = mutableMapOf<Point, Int>()
counts[Point(0, 0)] = 1
counts[Point(0, 0)] = 2                                   // one key, updated
```

The same two lines with a non-data class produce a set of size two from two identical points
and two separate map entries, because the inherited `equals` compares references.

## Common mistakes

**A data class with nothing in the primary constructor.** There would be nothing to derive
from, so the compiler refuses rather than deriving something empty:

```text
error: data class must have at least one primary constructor parameter.
```

**Putting state in the body and expecting it to count.** No diagnostic at all. The second
listing shows the consequence directly: an order whose status was set to `SHIPPED` compares
equal to a fresh one, and `copy()` returns an object whose status is `NEW`.

**Destructuring by the name you wrote.** Also silent. `val (currency, amount) = Money(1,
"EUR")` compiles and binds `currency` to `1`, because the names are positional and the
compiler is content as long as the types line up.

**Holding an array.** `Payload(byteArrayOf(1, 2)) == Payload(byteArrayOf(1, 2))` is `false`,
while `contentEquals` over the same two arrays is `true`.

## Check yourself

<details><summary>Why does <code>copy()</code> not carry over a property declared in the class body?</summary>

Because `copy` is generated as a call to the primary constructor with the current values of
its parameters. A body property is not a parameter, so the new object simply runs the
ordinary initialiser for it. If the value matters, it belongs in the constructor.

</details>

<details><summary>Two data class instances compare equal. Are their hash codes guaranteed to match?</summary>

Yes, because both members are derived from the same list of properties. That is the
invariant a hand-written pair breaks when only one of the two is updated, and it is why a
data class can be used as a map key without thinking about it.

</details>

<details><summary>When is an ordinary class the better choice?</summary>

When identity matters more than contents — an entity with a database key whose fields change
over its lifetime — or when the type has behaviour and few fields. `data` is about values.
Giving it to something that is not a value produces an object that two different rows can
claim to be.

</details>

## Listings

1. `data-classes-1.kt` — what the derived members change about equality and lookup.
2. `data-classes-2.kt` — what the derivation ignores: body properties and arrays.
3. `data-classes-3.kt` — `copy`, destructuring, and the positional rule.
