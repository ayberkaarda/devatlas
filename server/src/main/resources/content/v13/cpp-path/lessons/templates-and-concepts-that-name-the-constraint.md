## Why this exists

A template is a function or class written once for many types, and before C++20 its
requirements on those types were invisible. `largest(const std::vector<T>&)` needs `T` to be
comparable, but nothing in the declaration says so; the requirement lives wherever the body
happens to use `<`. Pass a type that does not qualify and the compiler is not able to complain
at your call — it substitutes, compiles the body, and reports a failure at the line inside the
template, with a note explaining that the instantiation was requested somewhere else. C++20
concepts let the requirement be written where the reader looks, and C++23 keeps them
unchanged. The failure then arrives at the call, naming what was missing.

## The idea

A template is a recipe written for "a fruit". Instantiation is making it with a particular
fruit, and a concept is the note at the top saying which fruits qualify.

### Where the analogy breaks

A cook reading a recipe notices straight away that a coconut cannot be whisked. A compiler
does not look at an unconstrained template until it substitutes a type, so the objection
arrives from inside the recipe and quotes a step the caller never wrote. The two diagnostics
below are the same mistake, once without the note at the top and once with it.

A recipe is also one document, whereas a template is a family — one function per type, each
compiled separately. `if constexpr` takes that further: a branch that would not compile for
this type is not compiled for this type. A recipe cannot have a step that ceases to exist for
coconuts.

And a note at the top of a recipe is advice. A constraint takes part in overload resolution:
where two candidates both match, the more constrained one is chosen. Listing 3 has `kind(1)`
and `kind(1u)` calling different functions with no cast and no tag, because `int` satisfies
one more clause than `unsigned` does. Adding a requirement can therefore change which
function runs, not merely whether the program builds.

## How it works

A constraint attaches to a template parameter, either as `template <std::integral T>` or as a
trailing `requires` clause. Both mean the same thing, and a concept is just a named
compile-time predicate over types:

```cpp
template <typename T>
concept Named = requires(const T& value) {
    { value.name() } -> std::convertible_to<std::string_view>;
    { value.id() }   -> std::same_as<int>;
};
```

The braces-and-arrow form says two things at once: the expression must be valid, and its type
must satisfy the concept on the right. A `requires`-expression yields `true` or `false`, so a
concept is a value — listing 2 prints `Named<User>` and `Named<Blob>` and asserts them with
`static_assert`. That printability is the practical reason to split a large concept into named
clauses: a failing constraint can then be located rather than guessed at.

One rule catches people out. A `requires`-expression only reports `false` where substitution
is allowed to fail, which means inside a template. Written against a concrete type in ordinary
code, an invalid expression is a hard error rather than a `false`, which is why listing 2
names its clauses as concepts instead of asking inline.

`if constexpr` is the other half of writing generic code: the discarded branch is not
instantiated, so a body may contain code that is meaningless for some of the types that reach
it. A fold expression, `(values + ... + 0)`, does the same job for parameter packs: it expands
to the sum of however many arguments arrived, with the trailing `0` supplying the value for an
empty pack rather than leaving the expression ill-formed.

Constraints are not restricted to function templates: a class template takes them the same
way, and a third spelling puts the concept where `auto` would go, as
`std::string describe(const Named auto& value)`.

## Common mistakes

**Leaving a template unconstrained.** The error is reported at the line in the body that
failed, and the note says who asked for it:

```text
t2.cpp:8:18: error: invalid operands to binary expression ('Point' and 'const Point')
    8 |         if (best < value) { best = value; }
t2.cpp:17:18: note: in instantiation of function template specialization 'largest<Point>'
requested here
```

**The same call against a constrained template.** The error is now at the call, and every note
narrows the requirement until it reaches the expression that failed:

```text
t3.cpp:18:18: error: no matching function for call to 'largest'
t3.cpp:6:3: note: candidate template ignored: constraints not satisfied [with T = Point]
t3.cpp:5:11: note: because 'Point' does not satisfy 'totally_ordered'
concepts:231:27: note: because 'Point' does not satisfy 'equality_comparable'
concepts:186:11: note: because '__x == __y' would be invalid: invalid operands to binary
expression ('const Point' and 'const Point')
```

Machine paths are stripped from both quotations. The second one is longer, and that is the
improvement: it is a chain of named requirements ending at the missing operator, rather than
one line of somebody else's code.

**Asking a `requires`-expression about a concrete type outside a template.** It does not
evaluate to `false`; it fails to compile, for the same reason that substitution failure is
only an option where substitution happens.

## Check yourself

<details><summary>Where is the error reported for an unconstrained template that is misused?</summary>

At the line inside the template body that could not be compiled for that type, with a note
pointing at the instantiation. The caller's line is the note, not the error.

</details>

<details><summary><code>kind(1)</code> and <code>kind(1u)</code> call different overloads. Why?</summary>

Because both candidates' constraints are satisfied for `int`, and the more constrained one
wins. `unsigned` satisfies only the weaker constraint, so it selects the other overload.

</details>

<details><summary>Why can a function body contain a branch that would not compile for one of its types?</summary>

Because `if constexpr` discards the branch not taken before it is instantiated. Only the
selected branch has to be valid for that instantiation.

</details>

## Listings

1. `templates-and-concepts-that-name-the-constraint-1.cpp` — a constraint at the declaration, checked at the call.
2. `templates-and-concepts-that-name-the-constraint-2.cpp` — a concept is a named, printable predicate.
3. `templates-and-concepts-that-name-the-constraint-3.cpp` — the more constrained overload wins, and `if constexpr`.
