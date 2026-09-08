## Why this exists

`map`, `filter` and `take` on a Kotlin collection each build a new list. A chain of three
therefore allocates three lists and walks the whole input three times, which is invisible on
ten elements and not invisible on a million, or on an input that is being read as it
arrives. Kotlin 2.4 offers a second type with the same operator names — `Sequence` — whose
stages do nothing when they are written and pull one element at a time when something
finally asks. Choosing between the two is a real decision, and the way to make it is to know
what actually changes, which is not "speed" but *how many times each lambda runs*.

## The idea

A list chain is batch processing: every parcel is stamped, then every stamped parcel is
weighed, then the first two are taken. A sequence chain is an assembly line: one parcel goes
through stamping and weighing and out of the door, and the line stops as soon as two have
made it through.

### Where the analogy breaks

A real assembly line is faster because the stations run at the same time. A sequence has no
concurrency at all — the same single thread does the same work in a different order. What it
saves is work never done, not work done in parallel. The first listing shows the whole
effect as two counters: over ten elements the list chain calls `map` ten times and `filter`
ten times, the sequence calls each seven times, and both produce `[12, 14]`.

An assembly line also processes everything eventually. A sequence processes only what is
demanded, and if nothing is demanded it processes nothing: a chain with no terminal
operation is a value that has done no work and will never do any. Kotlin 2.4 emits no
warning for that, so a `map` whose result is discarded is a silent no-op.

And a line runs the same whatever the station. A sequence stage that must see every element
before it can emit one — `sorted`, `toList`, anything that groups — removes the
short-circuiting for everything upstream of it. The third listing visits all four words to
produce `sorted().first()` and two words to produce `first { it.startsWith("a") }`.

## How it works

`asSequence()` turns a collection into a lazy chain; a terminal operation such as `toList`,
`first`, `sum` or `count` turns it back into a value.

```kotlin
input.asSequence()
    .map { it * 2 }
    .filter { it > 10 }
    .take(2)
    .toList()      // nothing above this line has run until now
```

Because nothing runs early, a sequence can be endless. `generateSequence` takes a seed and a
step and is bounded by whatever consumes it:

```kotlin
val powers = generateSequence(1) { it * 2 }
powers.take(8).toList()        // [1, 2, 4, 8, 16, 32, 64, 128]
powers.first { it > 1000 }     // 1024
```

Some sequences may be walked once and some many times. One built from a collection can be
re-consumed, because it goes back to the collection for a new iterator. One built from an
iterator cannot: the elements are gone.

## Common mistakes

**Building a chain and never terminating it.** The second listing prints `after building the
chain, map calls: 0`. There is no diagnostic; the work simply never happens, and a
`forEach`-shaped side effect written as `map` disappears.

**Walking a one-shot sequence twice.** This one does fail, at run time, with an
`IllegalStateException` from the standard library. The third listing catches it and prints
the property rather than the library's wording:

```text
second pass threw IllegalStateException: true
```

**Reaching for `asSequence()` on a short list.** Each stage is an object and each element
crosses each stage through an iterator. Below a few hundred elements with a chain of two or
three cheap stages, the eager version does the same work with fewer moving parts. The rule
that holds up is: use a sequence when the input is large, when the chain is long, when the
chain short-circuits, or when the input is not fully available.

**Forgetting that a sequence's lambdas run on the consumer's schedule.** A `map` block that
increments a counter runs when the terminal operation pulls, so reading that counter before
the terminal call gives zero — as the second listing prints — and reading it after two
different terminals gives the sum of both traversals.

## Check yourself

<details><summary>Same chain, same result, different counts. Which counts, and why?</summary>

The number of times each lambda runs. The list version completes each stage over the whole
input before starting the next; the sequence version carries one element to the end of the
chain before fetching another, so `take(2)` stops the pulling as soon as two elements have
survived.

</details>

<details><summary>Why can an infinite sequence be declared but not an infinite list?</summary>

Because a list is its elements and would have to be built, whereas a sequence is a recipe for
producing the next element. The bound comes from the consumer — `take`, `first`,
`takeWhile` — not from the producer.

</details>

<details><summary>Does putting <code>sorted()</code> in a sequence chain change the answer?</summary>

Not the answer, only the work. `sorted` has to consume the entire upstream before it can
yield its first element, so anything downstream that would have short-circuited no longer
saves anything. If a sort is in the chain, the eager version is usually the clearer one.

</details>

## Listings

1. `collections-sequences-and-the-lazy-chain-1.kt` — the same chain twice, counted.
2. `collections-sequences-and-the-lazy-chain-2.kt` — when the work happens, and endless sequences.
3. `collections-sequences-and-the-lazy-chain-3.kt` — one-shot sequences and stages that block.
