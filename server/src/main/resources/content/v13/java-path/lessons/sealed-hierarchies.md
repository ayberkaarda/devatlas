## Why this exists

An interface is an open invitation: anyone may implement it, including code you have never
seen. That is exactly what you want for a plugin point and exactly wrong for a fixed set of
alternatives — a payment that is either a card, a transfer or a voucher, a command that is
one of five. When the set is closed but the language does not know it, every piece of code
that dispatches over the set needs a fallback branch, and that branch is where a case you
forgot goes to die quietly. Java 21 lets you tell the compiler the set is closed.

## The idea

A sealed type is an interface with a guest list. `permits` names, at the declaration site,
exactly which types may implement it, and the compiler is the doorman: nothing that is not on
the list gets in, whatever else it does.

### Where the analogy breaks

A doorman only ever says no. The compiler also uses the list to say something useful in the
other direction: because it knows the guests are exactly these three, it can tell you that
your `switch` handled only two. That positive use is the entire reason to seal a type, and no
guest list does it.

The list is also more demanding than a doorman's. Each permitted subtype must state what it
does with the sealing, using exactly one of `final`, `sealed` or `non-sealed`. And a guest
list can be quietly amended, whereas `permits` is a compilation-wide commitment: adding a
subtype breaks every exhaustive `switch` that has not been updated. That is the mechanism
working, but if a `switch` is compiled against an old version of the hierarchy and run against
a new one, the check moves to run time and JLS §14.11.3 says a `MatchException` is thrown.

## How it works

The Java 21 language documentation describes the declaration: add `sealed`, then after any
`extends` and `implements` clauses add `permits`, naming the classes that may extend it.

```java
sealed interface Shape permits Circle, Square, Rectangle { }
record Circle(double radius) implements Shape { }
```

Permitted subtypes must be accessible to the sealed type at compile time and must be in the
same module as it, or in the same package if the sealed type is in the unnamed module. When
every subtype is declared in the same source file, the `permits` clause may be left out and
the compiler infers it; listing 2 omits it and then prints what was inferred.

Each permitted subtype takes exactly one of three modifiers, and they mean different things.
`final` closes that branch. `sealed` continues the list further down. `non-sealed` reopens
that branch to anyone, which the documentation describes as the point where a sealed class
cannot prevent its permitted subclasses from being extended.

The payoff is a `switch` with no `default` label at all:

```java
static double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Square s -> s.side() * s.side();
        case Rectangle r -> r.width() * r.height();
    };
}
```

At run time the list is still there: `Class.isSealed()` and `Class.getPermittedSubclasses()`
report it, which listing 1 uses to show that the compiler and the class file agree.

## Common mistakes

**Forgetting the modifier on a subtype.** `javac --release 21` answers `error: sealed,
non-sealed or final modifiers expected`. A record satisfies it automatically, because records
are implicitly `final`.

**Leaving a case out.** The diagnostic is `error: the switch expression does not cover all
possible input values`, pointing at the `switch` rather than the missing type.

**Adding a `default` branch anyway.** This is the mistake with no diagnostic. A `switch` over
a sealed type that carries a `default` compiles without complaint — compiling one under
`javac --release 21 -Xlint:all` produced no warning — and from then on a newly added subtype
silently takes the default. Listing 3 shows the same failure in an open hierarchy, where an
added case is reported as `ignored`.

**Sealing across packages in the unnamed module.** Permitted subtypes may live in other
packages only when the sealed type is in a named module.

## Check yourself

<details><summary>Why does sealing let you delete the <code>default</code> branch?</summary>

Because the compiler can enumerate the permitted subtypes and check that the labels cover
them. Without a closed set it has to assume some unknown implementation exists, so a
`default` is required for the `switch` to be exhaustive.

</details>

<details><summary>What does <code>non-sealed</code> buy, given that it gives the guarantee away?</summary>

It confines the openness. `Event` stays closed to three alternatives while one of them,
`Custom`, is an extension point. A `switch` still only needs the three labels, and everything
below `Custom` arrives through that one branch.

</details>

<details><summary>An exhaustive <code>switch</code> is compiled, then a subtype is added and only the hierarchy is recompiled. What happens?</summary>

The stale `switch` no longer covers the type. JLS §14.11.3 specifies that a `MatchException`
is thrown at run time, and the fix is to recompile the class containing the `switch`.

</details>

## Listings

1. `sealed-hierarchies-1.java` — a sealed interface and a `switch` with no `default`.
2. `sealed-hierarchies-2.java` — an inferred `permits` clause and one reopened branch.
3. `sealed-hierarchies-3.java` — the case an open hierarchy dropped without a word.
