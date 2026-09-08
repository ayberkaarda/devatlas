## Why this exists

Kotlin asks you to pick a keyword for every declaration, and the choice looks like a style
preference until the first time it saves you. `val` and `var` are not about safety in
general; they are about one specific and narrow property — whether the *binding* may be
pointed at something else after it is created. Reading `val` as "immutable" is the most
productive misunderstanding in the language, because everything built on top of it is almost
right and fails in one place. Getting the boundary exact early is cheaper than finding it
later, inside a bug.

## The idea

A `val` is a label glued to a box. The glue stops the label moving to a different box. It
does nothing whatsoever about the contents of the box, which anyone holding it can still
rearrange.

### Where the analogy breaks

Two ways, and the second is the one that changes how you write code.

A glued label suggests one label per box. In practice several names can point at the same
object, and a read-only *type* is one of those names rather than a copy. Assigning a
`MutableList` to a variable declared `List` does not freeze anything: it hands out a
narrower view of the same object, and whoever still holds the wide view can keep writing
through it. The first listing shows the two names comparing equal under `===`.

And glue is one operation, whereas Kotlin has several kinds of read-only. A `val` with a
custom getter is recomputed on every read, so its value can differ between two consecutive
lines without anything having been assigned. A `const val` is resolved at compile time and
inlined into every call site, so it has to be a primitive or a `String`. A `lateinit var` is
non-null in the type system and absent in memory until something assigns it. All three read
as "constant" at a glance and behave differently.

## How it works

`val` forbids reassignment of the name. Calls that mutate the object are untouched:

```kotlin
val names = mutableListOf("ada", "grace")
names.add("alan")            // fine: the list changed, the binding did not
// names = mutableListOf()   // refused, and the message says which of the two
```

Depth is bought one level at a time, with a copy at the boundary:

```kotlin
class Team(val name: String, val members: List<String>)

val roster = mutableListOf("ada")
val leaky = Team("core", roster)          // the caller keeps the mutable end
val safe = Team("core", roster.toList())  // a copy the caller cannot reach
```

`toList()` produces a new list, and `roster` and `safe.members` then go their separate ways.
For a map whose values are themselves mutable, `mapValues { it.value.toList() }` buys the
second level. There is no operator that buys all of them at once, and pretending otherwise
is how a defensive copy ends up one level too shallow.

## Common mistakes

**Trying to reassign.** The message is short and exact — it is about the binding, not the
object:

```text
error: 'val' cannot be reassigned.
```

**Reaching for `const` on something the compiler cannot fold.** `const` is not a stronger
`val`; it is a different mechanism with a much smaller domain:

```text
error: const 'val' has type 'List<String>'. Only primitive types and 'String' are allowed.
```

**Treating a `List`-typed parameter as a snapshot.** No diagnostic exists for this one,
which is why it survives review. In the third listing a `Team` built from a caller's
`MutableList` reports `[ada, grace]` after the caller appended `grace`: the object arrived
before the write and was read after it.

**Reading a `lateinit var` too early.** The declared type is `String`, so nothing at the
call site warns you; the failure is an `UninitializedPropertyAccessException` at run time.
Asking `::endpoint.isInitialized` first is the supported way to find out.

## Check yourself

<details><summary>Does <code>val</code> make an object thread-safe?</summary>

No. It makes the binding safe to publish — nothing will repoint it — but the object it names
can be mutated by anyone holding it. A `val` pointing at a `MutableList` shared between
threads is exactly as unsafe as a `var` would be.

</details>

<details><summary>Two names refer to one list, one declared <code>MutableList</code> and one <code>List</code>. What does the second see after the first appends?</summary>

The appended element. The read-only type restricts what that *reference* can do, not what
the object is. This is why a function that stores a `List` parameter should copy it when the
stored value is meant to be stable.

</details>

<details><summary>When is <code>const val</code> the right choice over <code>val</code>?</summary>

When the value is a primitive or a `String` known at compile time and you want it inlined
into callers — a default limit, a header name. Note the cost of that inlining: code compiled
against an old value keeps the old value until it is recompiled.

</details>

## Listings

1. `val-var-and-shallow-immutability-1.kt` — the binding, the object, and the read-only view.
2. `val-var-and-shallow-immutability-2.kt` — computed vals, `const val`, and `lateinit`.
3. `val-var-and-shallow-immutability-3.kt` — how deep a defensive copy actually goes.
