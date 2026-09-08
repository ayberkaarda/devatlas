## Why this exists

A reference that may be absent is the most common shape in programming and, for a long
time, was the least visible one. A `String` looked identical whether or not it could be
missing, so the obligation to check lived in a comment, in a naming convention, or nowhere
at all. Kotlin 2.4 moves that obligation into the type system: `String` and `String?` are
two different types, the compiler tracks which one every expression has, and it will not
let you read a member of the nullable one without saying what should happen when there is
nothing there. Null does not disappear. What changes is that the compiler, rather than a
reviewer or a user, is the thing that notices you forgot.

## The idea

`String?` is a form field marked *optional*; `String` is one marked *required*. Copying an
optional field into a required one obliges you to look at whether it was filled in, and the
form's own layout is what forces the look. The check happens once, where the value is
transferred, instead of at every later place the value is read.

### Where the analogy breaks

A form is checked by whoever reads it, at the moment they read it. Kotlin's check happens
when the code is compiled, which makes it free at run time and — much more importantly —
exhaustive. It is exhaustive only over code the Kotlin compiler saw, and three gaps follow
from that.

The first is the boundary with Java, where a value arrives carrying no nullability
information at all and the compiler stops arguing. That is a lesson of its own at the end of
this track.

The second is `!!`, which is a hole you are allowed to cut deliberately. It converts a
compile-time obligation into a runtime `NullPointerException`, so it is not a way of
satisfying the type system but a way of postponing the failure to a worse moment.

The third is that a form, once submitted, stays filled in. A Kotlin `var` property does not:
it can change between the check and the use. The compiler knows this and declines to carry a
null check forward across a mutable property, which is the most surprising refusal a
newcomer meets.

## How it works

Three operators cover almost everything. `?.` calls only when the receiver is not null and
yields `null` otherwise, so the result type gains a `?` of its own. `?:` supplies the value
for that null branch. `?.let` runs a block only when there is something to run it on.

```kotlin
val absent: String? = null
println(absent?.length)           // null, and the expression's type is Int?
println(absent?.length ?: -1)     // -1
absent?.let { println("never") }  // the block is skipped entirely
```

Where the compiler can prove a value is not null it stops asking. That is a smart cast:
after `if (x != null)` the value has type `String` inside the branch, and after `is String`
it has type `String` in that branch of a `when`. Smart casts apply to local values and to
`val` properties, because nothing can change either of them in between.

```kotlin
class Config(val name: String?)          // val: the smart cast applies
class MutableConfig(var name: String?)   // var: it does not

fun lengthOf(c: MutableConfig): Int {
    val snapshot = c.name                // read once into a local, then check that
    return if (snapshot != null) snapshot.length else -1
}
```

The local copy is the whole fix, and it is honest about what it does: it makes explicit that
you are working from a value read at one instant rather than from a property that may
already have moved on.

## Common mistakes

**Assuming the types will coerce.** They do not, and the message names both of them:

```text
error: null cannot be a value of a non-null type 'String'.
```

**Reading straight through a nullable reference.** The compiler lists exactly the two
operators it will accept in that position:

```text
error: only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.
```

**Expecting a smart cast on a mutable property.** Kotlin 2.4 says why it refuses rather than
only that it refuses:

```text
error: smart cast to 'String' is impossible, because 'name' is a mutable property that could be mutated concurrently.
```

**Reaching for `?.` out of habit.** On a receiver the compiler already knows is not null it
is dead weight, and you are told so:

```text
warning: unnecessary safe call on a non-null receiver of type 'String'.
```

## Check yourself

<details><summary>Why does <code>absent?.length</code> have type <code>Int?</code> rather than <code>Int</code>?</summary>

Because the safe call has two outcomes: the member's value when the receiver is present, and
`null` when it is not. The type has to describe both, so it is the member's type with a `?`.
That is also why safe calls chain — each link can produce the null that the next one skips.

</details>

<details><summary>Two nullable strings, one a local <code>val</code> and one a <code>var</code> property. Why does only one of them smart-cast?</summary>

Because the compiler will only carry a check forward when it can prove nothing invalidated
it. A local `val` cannot be reassigned. A `var` property can be written by anything holding
the object, including another thread, so the check and the use might see different values.
Reading the property once into a local restores the guarantee.

</details>

<details><summary>Is <code>!!</code> ever the right answer?</summary>

When you know something the compiler cannot: a value the framework guarantees is populated
by the time your code runs, for example. Even then `requireNotNull` with a message is
usually better, because it fails with a sentence explaining what was missing rather than
with a bare exception.

</details>

## Listings

1. `null-safety-as-a-type-1.kt` — the three operators and how a chain behaves.
2. `null-safety-as-a-type-2.kt` — smart casts, where they stop, and what `!!` costs.
3. `null-safety-as-a-type-3.kt` — where the question mark sits in a collection type.
