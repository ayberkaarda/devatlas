## Why this exists

Most of what a program does to a collection is one of a handful of things: keep some of it,
turn each item into something else, add the results up, find the first that satisfies a test.
Written as loops, each of those is four or five lines in which the interesting part is one
condition and everything else is bookkeeping — an index, an accumulator, a `break`. Rust 1.98
has a trait, `Iterator`, with one required method and a long list of provided ones, and a
`for` loop is defined in terms of it. The chain is not a slower way of saying the loop: the
adapters are ordinary structs whose `next` calls the one before it, and an optimising build
inlines the whole thing away.

## The idea

An iterator is a conveyor with stations bolted along it, and nothing moves until somebody at
the far end pulls. Bolting on another station is free. Pulling one item pulls it through every
station in turn, and pulling can stop early.

### Where the analogy breaks

A conveyor exists whether or not anyone pulls. An adapter chain with no consumer does not run
at all — it is a value of a type nobody looked at, and the compiler says so, warning that an
unused iterator must be used and noting that iterators are lazy and do nothing unless
consumed. A chain of `map` whose closure has a side effect is not a loop that ran; it is a
loop that never started.

A conveyor's stations each cost a handoff, and adapters do not — but "compiles to the same
thing as a loop" is folklore, and it is worth being precise about how far it holds. Compiled
with optimisation by Rust 1.98, the summing chain in listing 1 and the equivalent `for` loop
do not produce identical instructions: the loop stays a tight scalar loop, while the chain is
unrolled to handle four entries per iteration. Neither pays a per-station cost, which is the
claim that matters; they are not the same code, which is the claim people repeat.

And a conveyor runs one way. `Iterator` requires only `next`, so an iterator that goes
backwards, knows its own length or can be split has to say so through further traits —
`DoubleEndedIterator` for `rev`, `ExactSizeIterator` for `len`. A chain that drops one of
those is not broken, but the method you wanted is gone from it.

## How it works

Three methods, three relationships to ownership. `iter` yields shared references and leaves
the collection alone; `iter_mut` yields exclusive references and edits in place; `into_iter`
consumes the collection and yields items by value. `for x in &c`, `for x in &mut c` and
`for x in c` are those three, spelled with the loop.

```rust
let total: i64 = entries.iter().filter(|e| e.done).map(|e| e.size_bytes).sum();

let kept: Vec<String> = ids.into_iter().filter(|id| id.starts_with('a')).collect();
// `ids` cannot be named after this line: it was moved into the iterator

let mut counts: HashMap<&str, i64> = HashMap::new();
for state in observed { *counts.entry(state).or_insert(0) += 1; }
```

`collect` builds whatever the target type asks for — a `Vec`, a `String`, a `HashMap` from
pairs — and the annotation is what chooses. The entry API is the one `HashMap` method worth
learning early: it looks the key up once and inserts or updates in one step, instead of a
lookup followed by a second lookup to write.

Choosing between the two maps is a question about ordering. `HashMap` visits its entries in
arbitrary order — that is the documented wording in Rust 1.98, and the order also differs
between runs of the same program, because the default hasher is seeded randomly. `BTreeMap`
keeps its keys sorted, which costs a comparison per level and buys reproducible iteration and
range queries. A test or a printed report that iterates a `HashMap` is recording an accident;
either ask the map questions instead, or use the ordered one.

## Common mistakes

**Using a collection after `into_iter` took it.** The message names the method that did it:

```text
error[E0382]: borrow of moved value: `ids`
3 |     let upper: Vec<String> = ids.into_iter().map(|id| id.to_uppercase()).collect();
  |                                  ----------- `ids` moved due to this method call
4 |     println!("{} from {}", upper.len(), ids.len());
  |                                         ^^^ value borrowed here after move
note: `into_iter` takes ownership of the receiver `self`, which moves `ids`
```

**Changing a collection while iterating it.** In other languages this throws halfway through;
here it does not build:

```text
error[E0502]: cannot borrow `states` as mutable because it is also borrowed as immutable
3 |     for state in &states {
  |                  -------
  |                  immutable borrow occurs here
5 |             states.push(String::from("downloading"));
  |             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ mutable borrow occurs here
```

**Building a chain and never consuming it.** Nothing runs, and there are two warnings about
it — the second one guessing correctly at what was meant:

```text
warning: unused `Map` that must be used
3 |     ids.iter().map(|id| println!("visiting {id}"));
  |     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  = note: iterators are lazy and do nothing unless consumed
help: you might have meant to use `Iterator::for_each`
```

## Check yourself

<details><summary>Why is printing a <code>HashMap</code>'s contents a poor thing to assert on?</summary>

Its iteration order is arbitrary and varies between runs, so the assertion tests the hasher's
seed rather than the program. Assert on a lookup, a length or a `BTreeMap` built from it.

</details>

<details><summary>A chain ends in <code>find</code>. How much of the collection is visited?</summary>

As much as it takes and no more. Laziness means each item is pulled through the whole chain
individually, so a `find` that succeeds on the first item never touches the second.

</details>

<details><summary>When is the loop still the better choice?</summary>

When the body does several unrelated things, or mutates something outside the collection, or
wants to `break` out of two levels. A chain that needs a mutable capture and a flag is a loop
wearing a costume.

</details>

## Listings

1. `collections-and-iterators-1.rs` — a loop and a chain producing the same answer.
2. `collections-and-iterators-2.rs` — `iter`, `iter_mut` and `into_iter`.
3. `collections-and-iterators-3.rs` — the entry API, and the ordering guarantee that separates the two maps.
