## Why this exists

An enum is only worth defining if something forces every case to be considered. A download
queue entry is queued, downloading, verifying, done, failed or paused; a function that turns
one into a label has six answers to give, and the failure mode is not giving five and
shipping. In Rust 1.98 `match` is an expression that must produce a value for every possible
input, and the compiler works out for itself whether the arms cover the type. When a seventh
state is added later, every match that did not use a wildcard stops compiling and names the
state it is missing. That is the feature: not the syntax, the refusal.

## The idea

`match` is a sorting office where the supervisor will not open the doors until there is a
pigeonhole for every address that can arrive. Not a hole for most of them and a tray marked
"other" — a hole for each, checked before any post is handled.

### Where the analogy breaks

A sorting office reads addresses. `match` reads *shapes*, and it unpacks while it reads: an
arm can require that a value is the `Downloading` variant, that its `total` field is greater
than zero, and simultaneously bind `received` and `total` for use in the arm's body. A
pigeonhole that also opens the parcel is not a pigeonhole.

Pigeonholes are disjoint. Match arms need not be, and are tried top to bottom with the first
match winning. Overlapping arms are legal and useful — a specific case above a general one is
the normal way to write them — but an arm that no value can reach is a mistake, and Rust 1.98
warns rather than errors, because an unreachable arm is dead code and not unsoundness.

And the tray marked "other" is the thing to be afraid of. A `_` arm makes any match exhaustive
forever, so the seventh state compiles silently into whatever the wildcard said. The
compiler's help lasts exactly as long as you refuse the wildcard. That is not an argument
against `_` everywhere: matching a string parsed from a file genuinely has an unbounded input
and needs one. It is an argument against `_` over a closed enum you control.

## How it works

Arms are `pattern => expression`. Patterns destructure structs, tuples, slices and enum
payloads; `|` joins alternatives; `..=` matches an inclusive range; `name @ pattern` binds the
whole value while the pattern constrains it; a guard `if condition` adds a test the pattern
cannot express, and does not count towards exhaustiveness.

```rust
match state {
    QueueState::Downloading { received, total } if *total > 0 => received * 100 / total,
    QueueState::Downloading { .. } => 0,
    QueueState::Failed { attempts: 0 | 1 } => -1,
    QueueState::Failed { attempts: 2..=3 } => -2,
    QueueState::Failed { attempts } => -attempts,
    QueueState::Queued | QueueState::Verifying | QueueState::Done => 100,
}
```

Outside `match`, the same patterns appear in three shorter forms. `if let` runs a block when
one pattern matches. `let ... else` binds when it matches and must diverge when it does not,
which keeps the happy path unindented — a plain `let` with a pattern that can fail is refused
as a refutable pattern in a local binding. And `matches!` reduces a match to the yes-or-no
question it sometimes is.

Edition 2024 adds two things here. Let chains join several `let` patterns and ordinary
conditions with `&&` in one `if`, evaluated left to right and stopping at the first that does
not hold. And the temporary built for an `if let` scrutinee is dropped before the `else` block
runs, so a shared borrow taken to test a value is released in time for the `else` block to
take an exclusive one — under edition 2021 the same code compiles and then fails at run time.

## Common mistakes

**Leaving a variant out.** The whole point, and the message does the work for you:

```text
error[E0004]: non-exhaustive patterns: `QueueState::Verifying` not covered
4 |     match state {
  |           ^^^^^ pattern `QueueState::Verifying` not covered
note: `QueueState` defined here
1 | enum QueueState { Queued, Downloading, Verifying, Done, Failed }
  |      ^^^^^^^^^^                        --------- not covered
```

**An arm below a wildcard.** It can never run, and the compiler says which arm swallowed it:

```text
warning: unreachable pattern
4 |         _ => "some",
  |         - matches any value
5 |         1 => "one",
  |         ^ no value can reach this
```

**Writing a let chain and building for an older edition.** The feature is edition-gated, not
version-gated, so a crate on edition 2021 compiled by Rust 1.98 still refuses it:

```text
error: let chains are only allowed in Rust 2024 or later
4 |     if let Some(x) = a && let Some(y) = b && x < y {
  |        ^^^^^^^^^^^^^^^
```

## Check yourself

<details><summary>Why does a guard not count towards exhaustiveness?</summary>

Because the compiler cannot evaluate an arbitrary condition. `Some(n) if n > 0` may fail, so
the match still needs an arm that covers `Some` without the guard.

</details>

<details><summary>When is <code>_</code> the right arm?</summary>

When the input really is open — parsing a string from a file or a network — so there is no
finite set of cases to enumerate. Over an enum you own, it trades away the compiler's help.

</details>

<details><summary>What does <code>let ... else</code> give you that <code>if let</code> does not?</summary>

The binding stays in scope for the rest of the function instead of only inside a block, at the
price of the `else` block having to diverge — return, break, continue or panic.

</details>

## Listings

1. `pattern-matching-and-exhaustiveness-1.rs` — an exhaustive match with guards and ranges.
2. `pattern-matching-and-exhaustiveness-2.rs` — patterns outside `match`.
3. `pattern-matching-and-exhaustiveness-3.rs` — let chains and the edition 2024 scope change.
