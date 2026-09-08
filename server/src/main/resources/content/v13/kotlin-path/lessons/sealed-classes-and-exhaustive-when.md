## Why this exists

A type with a fixed set of alternatives is everywhere: a request either succeeded,
redirected or failed; a parse either produced a value or a reason it could not. Modelled as
one class with a nullable field per case, the compiler can help with none of it, and every
reader has to work out which combinations are real. Modelled as an open hierarchy, a `when`
over the cases needs an `else` branch that exists only to satisfy the compiler, and that
branch is where a case added six months later silently lands. Kotlin 2.4's `sealed` modifier
closes the hierarchy so the compiler knows the full list, and an exhaustive `when` over it
needs no `else` at all.

## The idea

A sealed type is a closed guest list. The compiler holds the list, so it can tell you when
your `when` has forgotten someone, and it can tell you at compile time rather than when that
guest arrives.

### Where the analogy breaks

A guest list is closed at one door. Sealed is closed at a boundary you have to know: direct
subtypes must be in the same package and the same compilation unit as the sealed
declaration. Not the same file — that was the rule in older versions of the language, and
code written under it still reads as if nesting the subtypes were required. It is a common
convention, not a constraint.

A guest list is also flat, and a sealed hierarchy is a tree. A direct subtype may itself be
`sealed`, or `open`, in which case the `when` is exhaustive over the direct subtypes and the
branch for one of them covers a whole subtree. Exhaustiveness is checked one level down, not
all the way to the leaves.

And a list tells you who is coming, not what they brought. This is the actual reason to
prefer a sealed hierarchy over an enum: each case carries its own data, with its own types,
and the `when` branch that matches a case is smart-cast to it. `Failure` can hold a status
and a reason while `Ok` holds a body, and neither has to carry a nullable field belonging to
the other.

## How it works

Declare the parent `sealed` and list the cases. A case with no data of its own is a `data
object`, which is a singleton and prints its own name:

```kotlin
sealed interface Reply {
    data class Ok(val body: String) : Reply
    data class Failure(val status: Int, val reason: String) : Reply
    data object Pending : Reply
}

fun render(r: Reply): String = when (r) {
    is Reply.Ok -> "200 ${r.body}"
    is Reply.Failure -> "${r.status} ${r.reason}"
    Reply.Pending -> "waiting"
}
```

There is no `else`, and that absence is the feature: adding a fourth case to `Reply` turns
this function into a compile error, so the build enumerates every `when` that has to be
revisited. `when (val p = parsePort(raw))` binds and matches in one place, and each branch
smart-casts `p` to its own subtype, so `p.reason` resolves in the branch that has a reason
and nowhere else.

## Common mistakes

**Leaving out a branch.** The message names the missing case, which is the whole point:

```text
error: 'when' expression must be exhaustive. Add the 'is Tri' branch or an 'else' branch.
```

Kotlin 2.4 applies this to a `when` used as a statement as well as one used as an
expression, and — worth knowing when you go looking for the error — the message says
"expression" in both cases. The same check covers enums:

```text
error: 'when' expression must be exhaustive. Add the 'AMBER' branch or an 'else' branch.
```

**Adding `else` to make the error go away.** This compiles, and it discards the only thing
the sealed hierarchy was bought for. The second listing runs the same three events through
an exhaustive `when` and an `else`-based one; the second answers `something else` twice
where the first names each case. Reserve `else` for a `when` whose subject is genuinely open
— an `Int`, a `String` — and never for a sealed type.

**Reaching for an enum because the cases have no data yet.** An enum constant is one object
with one set of fields shared by every constant, so the first case that needs a payload
forces a nullable field onto all of them. A `data object` inside a sealed interface costs
nothing extra now and does not have to be unpicked later.

## Check yourself

<details><summary>Why does the compiler not require <code>else</code> on an exhaustive <code>when</code>?</summary>

Because it can prove that the branches cover every subtype, so an `else` would be
unreachable. That proof is only available for a closed hierarchy — for an open class,
another module could add a subtype after this file was compiled.

</details>

<details><summary>A sealed interface with a subtype that is itself sealed. How far does exhaustiveness reach?</summary>

One level: a `when` over the parent is exhaustive when it covers the direct subtypes. If a
branch matches the intermediate sealed type, a second `when` inside it gets the same check
over that subtree.

</details>

<details><summary>When is an enum still the right choice?</summary>

When the cases carry no data, are genuinely interchangeable, and you want `values()`,
`valueOf` and ordinal ordering for free — a set of statuses persisted as names, for
instance. The moment one case needs a field the others do not, the sealed hierarchy is the
honest model.

</details>

## Listings

1. `sealed-classes-and-exhaustive-when-1.kt` — a sealed interface and a complete `when`.
2. `sealed-classes-and-exhaustive-when-2.kt` — the same logic with and without `else`.
3. `sealed-classes-and-exhaustive-when-3.kt` — a result type, smart casts, and `Nothing`.
