## Why this exists

Ownership alone would be exhausting. If every function that reads a value had to be handed
it, every caller would either give the value up or clone it, and a program would spend its
time copying data it only wanted to look at. A reference borrows instead: it names a value
somebody else owns, costs a machine word, and ends without doing anything. What makes
borrowing safe rather than merely cheap is a rule Rust 1.98 enforces at compile time — at any
point in a program, a value may have any number of shared references or exactly one exclusive
reference, and never both. That single rule is what removes the data race and the iterator
invalidated underneath a loop.

## The idea

Borrowing is a reading room. A book can sit on the long table with several people reading it
at once, and nobody is disturbed. If somebody wants to annotate it, the book goes to the
annotation desk, and while it is there nobody else may read it. The book never leaves the
building, and the reading room does not need to know who bought it.

### Where the analogy breaks

The librarian watches and intervenes. Rust's rule is enforced by the compiler before the
program runs, and the compiler is conservative: it rejects code it cannot prove safe, not
merely code that is unsafe. Two exclusive references into different halves of one vector are
perfectly sound, and the compiler still refuses to work that out from indexing arithmetic —
which is why `split_at_mut` exists, to hand you the two halves with the proof attached.

The reading-room frame also mislabels the rule as readers-versus-writer, and it is not. `&mut`
means *exclusive*, not *mutating*: a function can take one and never write through it. In the
other direction, `Cell` and `RefCell` mutate through shared references on purpose, moving the
same exclusivity check to run time. What the rule really forbids is aliasing plus mutation at
once, whichever side you approach it from.

And the reader leaves the table the moment they stop reading. A borrow's region ends at its
last use, not at the closing brace, so a value can be borrowed exclusively and then shared
again a line later with both names still in scope. Code written for an older mental model
wraps borrows in blocks that Rust 1.98 does not need.

## How it works

`&value` produces a shared reference and `&mut value` an exclusive one; `*` reads or writes
through either. Method calls take the reference for you, which is why `vec.len()` needs no
ampersand. A `&String` coerces to `&str` and a `&Vec<T>` to `&[T]`, so a function should ask
for the narrower type and accept both.

```rust
fn total(values: &[i64]) -> i64 { values.iter().sum() }
fn bump(counter: &mut i64) { *counter += 1; }

let readings = vec![3, 9, 4];
let sum = total(&readings);      // borrowed, so `readings` is still ours

let mut attempts = 0;
bump(&mut attempts);             // the exclusive borrow ends when `bump` returns

let mut buffer = vec![1, 2, 3, 4, 5, 6];
let (left, right) = buffer.split_at_mut(3);   // two exclusive borrows, proved disjoint
```

Taking an exclusive reference also requires the binding itself to be declared `mut`. That is
a separate check from the borrow rule, and it catches a different mistake: a value nobody
declared as changeable being handed to something that changes it.

## Common mistakes

**Modifying a collection while a reference into it is alive.** The push would reallocate the
buffer the reference points into:

```text
error[E0502]: cannot borrow `states` as mutable because it is also borrowed as immutable
3 |     let first = &states[0];
  |                  ------ immutable borrow occurs here
4 |     states.push(String::from("downloading"));
  |     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ mutable borrow occurs here
5 |     println!("{first}");
  |                ----- immutable borrow later used here
```

The third annotation is the important one: the borrow is a problem because it is *used later*.
Delete line 5 and the program compiles, with nothing left but an unused-variable warning —
the borrow region ending early, in action.

**Two exclusive references at once.** The second is refused as soon as the first is still
wanted afterwards:

```text
error[E0499]: cannot borrow `log` as mutable more than once at a time
3 |     let a = &mut log;
  |             -------- first mutable borrow occurs here
4 |     let b = &mut log;
  |             ^^^^^^^^ second mutable borrow occurs here
```

**Forgetting `mut` on the binding.** A different check with a different code:

```text
error[E0596]: cannot borrow `log` as mutable, as it is not declared as mutable
7 |     append(&mut log);
  |            ^^^^^^^^ cannot borrow as mutable
```

## Check yourself

<details><summary>Does <code>&amp;mut</code> mean the function will modify the value?</summary>

No. It means no other reference to that value may exist while this one does. A function may
take `&mut` and only read; the guarantee is exclusivity, and mutation is what exclusivity
makes safe.

</details>

<details><summary>Why does wrapping a borrow in a block often stop being necessary?</summary>

Because a borrow's region ends at its last use rather than at the end of the enclosing block.
The block was a way of forcing an early end that the compiler now works out on its own.

</details>

<details><summary>Two exclusive references into one vector — always wrong?</summary>

Not wrong, just unprovable from indexing. `split_at_mut` returns two disjoint slices, and the
standard library carries the proof so the borrow checker does not have to reconstruct it.

</details>

## Listings

1. `borrowing-1.rs` — shared references, and the owner surviving them.
2. `borrowing-2.rs` — an exclusive reference, and where its region actually ends.
3. `borrowing-3.rs` — disjoint exclusive borrows, and mutation through a shared reference.
