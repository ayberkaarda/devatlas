## Why this exists

Borrowing works inside a function because the compiler can see both the value and the
reference. Across a function boundary it cannot: a caller sees only the signature. If a
function takes two references and returns one, nothing in `fn longest(a: &str, b: &str) ->
&str` says which of the two the result points into, so the compiler cannot tell whether the
caller is allowed to keep it. Lifetime parameters are how that missing fact is written down.
They do not change how long anything lives. They state a relationship, and Rust 1.98 then
checks both sides of it: the body must honour what the signature promised, and each call site
must supply arguments that outlive what it does with the result.

## The idea

A lifetime annotation is a unit of measurement. Writing `m/s` on a quantity does not make
anything move faster; it records what the number is a measure of, so that a checker can
reject `3 m + 4 s` before anyone computes a wrong answer. `&'a str` is the same kind of
marking: `'a` does not extend the string, it records which region of the program the
reference is valid over, so the compiler can reject a use outside it.

### Where the analogy breaks

Units come from a fixed, closed list. Lifetimes do not: `'a` is a variable the compiler
solves for, and it is solved separately at every call site. The same generic function may be
instantiated with a region three lines long in one caller and the whole program in another,
and the annotation in the source is only the *shape* of the constraint, not a value.

Units have no ordering, and lifetimes do. A longer region can be used wherever a shorter one
is required, which is why a `&'static str` can be passed to a function whose parameter is
`&'a str` for some short `'a`. Nothing in dimensional analysis behaves like that, and it is
the reason `'static` never needs a special case.

Finally, unit annotations are usually documentation the compiler ignores, and these are part
of the type. Two functions differing only in which input the result is tied to have different
types and accept different callers. Where the relationship is unambiguous the compiler fills
it in for you by the elision rules the Rust 1.98 reference sets out — each elided input
lifetime becomes its own parameter; if there is exactly one, the output takes it; and if one
of the parameters is `&self`, the output takes that. `fn longest` above fits none of those
three, which is exactly why it must be written out.

## How it works

A lifetime parameter is declared like a type parameter and used on the reference types in the
signature. Using one name for two inputs says both must outlive the result; using two names
says the result is tied to only one of them.

```rust
fn either<'a>(left: &'a str, right: &'a str) -> &'a str {
    if left.len() >= right.len() { left } else { right }
}

fn prefer<'p>(primary: &'p str, _fallback: &str) -> &'p str { primary }

struct Manifest<'a> { source: &'a str }   // may not outlive the text it points into

impl<'a> Manifest<'a> {
    // elided: the result borrows from `self`
    fn first_line(&self) -> &str { self.source.lines().next().unwrap_or("") }
    // written out: the results borrow from the text, not from `self`
    fn ids(&self) -> Vec<&'a str> { self.source.lines().collect() }
}
```

Those two methods are the whole idea in miniature. `first_line` returns something that lives
as long as the borrow of the `Manifest`; `ids` returns slices of the original text, which
outlive the `Manifest` itself. Same body shape, different promise, and only the signature
says which.

## Common mistakes

**Returning a reference the signature cannot account for.** The compiler names the two
candidates rather than guessing:

```text
error[E0106]: missing lifetime specifier
1 | fn longest(left: &str, right: &str) -> &str {
  |                  ----         ----     ^ expected named lifetime parameter
  = help: this function's return type contains a borrowed value, but the signature does not
    say whether it is borrowed from `left` or `right`
```

**Keeping a reference past the value.** The annotation described a relationship; here the
caller failed to satisfy it:

```text
error[E0597]: `inner` does not live long enough
5 |         outer = &inner;
  |                 ^^^^^^ borrowed value does not live long enough
6 |     }
  |     - `inner` dropped here while still borrowed
```

**Trying to make a value live longer by writing `'static`.** The annotation is not an
instruction, so this does not work and the error says why in one line:

```text
error[E0515]: cannot return reference to local variable `owned`
3 |     &owned
  |     ^^^^^^ returns a reference to data owned by the current function
```

The repair is never a longer lifetime. It is to return an owned value, or to accept the data
as a parameter so the caller owns it.

## Check yourself

<details><summary>Does <code>&amp;'a T</code> keep the value alive for the region <code>'a</code>?</summary>

No. It says the reference is only valid within `'a`, and the compiler checks that the value
outlives it. Nothing about the value's own lifetime changes.

</details>

<details><summary>Why does a method taking <code>&amp;self</code> usually need no annotation?</summary>

Elision covers it: when a parameter is `&self`, an elided output lifetime is taken from it.
That default is right often enough to be worth having, and wrong when the result borrows from
something the struct points at rather than from the struct.

</details>

<details><summary>Two lifetime names on two parameters — what does that buy?</summary>

It lets the caller pass a short-lived argument for the one the result is not tied to. One
shared name would have forced both arguments to outlive the result for no reason.

</details>

## Listings

1. `lifetimes-are-descriptions-1.rs` — one shared lifetime, and two independent ones.
2. `lifetimes-are-descriptions-2.rs` — a struct that borrows, and two different output regions.
3. `lifetimes-are-descriptions-3.rs` — which input the result is tied to, and `'static`.
