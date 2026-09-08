## Why this exists

An object that can change under you is an object you have to reason about across time. Every
method that received a reference to it is a place where it might have changed, so a bug in
one file becomes an investigation across ten. Making objects immutable removes the question
rather than answering it. The reason people avoid it is a belief that copying is expensive,
and in Java 21 that belief is usually wrong: the copies that matter happen once, at the
boundary, and the sharing that immutability enables removes copies elsewhere.

## The idea

An immutable object is a photograph. You do not edit a photograph; you take another one. The
old print stays exactly as it was, so anyone holding it can keep looking at it without
checking whether it still shows what it showed a minute ago, and you can hand out as many
prints as you like because none of them can be altered by whoever holds them.

### Where the analogy breaks

A photograph captures everything in the frame. A Java object captures only its own fields,
and a field that holds a reference is a window onto a scene that is still moving. If your
"immutable" record holds a `List` somebody else can still reach, the photograph has a live
video feed pasted into one corner. That is the failure listing 1 demonstrates, and it is the
single most common way an immutable design leaks.

The analogy also overstates the cost. Prints consume paper, so you take few of them; sharing
an immutable Java object costs nothing, and the library optimises for that. `List.copyOf`
applied to a list that is already unmodifiable returns the very same instance rather than a
copy — listing 3 checks the reference and prints `true`.

## How it works

`final` on a field is a statement about the variable, not about the object it names. JLS
§4.12.4 makes `final` mean the variable is assigned once; it says nothing about what the
referenced object does afterwards.

```java
private final List<String> hosts = new ArrayList<>();
hosts.add("a.example");   // legal: the variable never changed
```

So immutability is built at the boundaries. Copy on the way in, so that the caller's
reference cannot reach your state; hand out something unmodifiable on the way out, so that
your caller's reference cannot reach it either. A record's compact constructor is the natural
place for the first half.

```java
record Guarded(String name, List<String> tags) {
    Guarded { tags = List.copyOf(tags); }
}
```

`List.copyOf`, `Set.copyOf` and `Map.copyOf` in Java 21 return unmodifiable collections that
reject `null` elements, and whose javadoc states that attempts to modify them cause
`UnsupportedOperationException`. They are not `Arrays.asList`, which is a fixed-size **view**
over the array you passed: `set` works, `add` throws, and writing to the array afterwards
changes what the list reports.

```java
String[] backing = { "a", "b" };
List<String> view = Arrays.asList(backing);
backing[0] = "changed";   // view.get(0) is now "changed"
```

## Common mistakes

**Believing `final` freezes the contents.** Listing 2 mutates a `final` list field and a
`final` array in place, and the compiler is right not to object: neither variable was
reassigned.

**Returning the internal collection.** An accessor that hands back the live field lets any
caller call `clear()` on your state. In listing 1 the caller does exactly that and the record
ends up empty.

**Assuming a defensive copy is expensive.** For a list that is already unmodifiable,
`List.copyOf` does not copy at all. Measure before you skip the copy.

**Reaching for `Arrays.asList` when you wanted an immutable list.** `add` throws
`UnsupportedOperationException`, which looks like immutability right up to the point where
`set` silently succeeds.

**Passing `null` into `List.of`.** It throws `NullPointerException` at construction. That is
usually what you want, but it will surface as a failure in a code path that previously
tolerated a missing value.

## Check yourself

<details><summary>A record has a <code>List</code> component and no compact constructor. Is it immutable?</summary>

Only if the list it was handed is. The record's own field cannot be reassigned, but the list
object behind it can be changed by anyone who kept a reference — including the caller who
constructed the record.

</details>

<details><summary>Why can <code>List.copyOf</code> return its argument?</summary>

Because the argument is already unmodifiable, so no caller can change it, so a second
instance would carry no additional guarantee. Sharing is safe precisely because the value
cannot change; that is the saving immutability buys.

</details>

<details><summary><code>Arrays.asList(a).set(0, "z")</code> succeeds and <code>add</code> throws. Why the difference?</summary>

`Arrays.asList` returns a fixed-size view backed by the array. Setting an element writes
through to the array, which the array can accommodate; adding one would need the array to
grow, which it cannot, so that operation is unsupported.

</details>

## Listings

1. `immutability-by-default-1.java` — a record that leaked its list, and the copy that stops it.
2. `immutability-by-default-2.java` — what `final` freezes and what it does not.
3. `immutability-by-default-3.java` — what a defensive copy actually costs.
