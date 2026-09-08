## Why this exists

Dispatching on the runtime shape of a value used to take three steps that could disagree:
test with `instanceof`, cast to the same type, then pull the parts out with accessors. The
cast repeats the test, the accessors repeat the structure, and nothing checks that the chain
of `else if` covered everything. Java 21 folds all three into one construct, and over a
sealed hierarchy it also checks the coverage.

## The idea

A `switch` with patterns is a mail sorting frame. Each slot has a shape cut into it, a parcel
drops through the first slot it fits, and the slot pulls the contents out on the way — the
shape of the hole and the unpacking are the same act.

### Where the analogy breaks

A physical sorter is indifferent to slot order and to overlapping holes; parcels find
somewhere to land. Java tests labels top to bottom and refuses to compile a label a preceding
one would always catch, reporting `error: this case label is dominated by a preceding case
label`. Order is part of the meaning, not an implementation detail.

The sorter also has no opinion about an empty conveyor. A `switch` does: if the selector is
`null` and there is no `case null` label, a `NullPointerException` is thrown, exactly as
`switch` has always behaved. And the frame does not know what parcels exist in the world,
whereas over a sealed type the compiler does, and will not let the frame be incomplete.

## How it works

A type pattern tests and binds in one step, and the binding is in scope only where the test
is known to have succeeded — which is why the compiler rejects a use of the variable after
the `if` with `cannot find symbol`.

```java
if (value instanceof String s && !s.isBlank()) return "length " + s.length();
```

A record pattern takes that further: it matches a record and destructures it in the label,
and the Java 21 documentation states that a record pattern may be nested inside another
record pattern. The nesting is what lets a single label recognise a whole shape.

```java
case Circle(Point(int x, int y), int r) -> "circle at " + x + "," + y + " radius " + r;
```

Two Java 21 details are easy to get wrong. A `null` case label is allowed, and the
documentation is explicit that it may not be combined with anything except a `default` label —
`case null, String s` is answered with `error: invalid case label combination`. And a guard,
written `when`, refines a pattern rather than replacing it, so a guarded label does not
dominate the unguarded one that follows it.

```java
case Integer i when i < 0 -> "negative";
case Integer i            -> "non-negative " + i;
```

Pattern matching for `switch` and record patterns are both permanent features of Java 21.
Unnamed patterns and variables — the `_` that stands for a component you do not need — were a
preview feature in Java 21, so nothing in this track uses them.

## Common mistakes

**Ordering the general case first.** `case Object x` before `case String s` is
`error: this case label is dominated by a preceding case label`. Specific first, general last.

**Combining `null` with a pattern label.** `case null, String s` does not compile. Write
`case null` on its own, or combine it only with `default`.

**Assuming a `default` label protects against `null`.** It does not. Listing 2 calls a
`switch` that has a `default` and no `null` label, and the call throws
`NullPointerException` before any label is considered.

**Using a pattern variable outside the branch that bound it.** After
`if (o instanceof String s) { }`, referring to `s` is `error: cannot find symbol`. The scope
is the region where the pattern matched.

**Adding `default` to a switch over a sealed type.** It compiles, and it turns the
exhaustiveness check off for every subtype added later.

## Check yourself

<details><summary>Why does <code>case Integer i when i &lt; 0</code> not dominate <code>case Integer i</code>?</summary>

Because the guard means the first label does not match every `Integer`. Dominance is about
labels that always match; a guarded label makes no such promise, so the unguarded one after
it is still reachable — and still needed for exhaustiveness.

</details>

<details><summary>A <code>switch</code> over <code>Object</code> has a <code>default</code>. What does it do with <code>null</code>?</summary>

It throws `NullPointerException`. The `default` label does not cover `null`; only an explicit
`case null` does.

</details>

<details><summary>What does a nested record pattern save over destructuring by hand?</summary>

The test and the extraction stay in one place, so they cannot drift apart, and the compiler
checks the component types against the record's declaration. Written by hand, the accessors
are a second statement of the same structure.

</details>

## Listings

1. `pattern-matching-1.java` — the three-step form, the pattern form, and record patterns.
2. `pattern-matching-2.java` — the `null` label, guards, and what happens without either.
3. `pattern-matching-3.java` — an evaluator and a simplifier over a sealed hierarchy.
