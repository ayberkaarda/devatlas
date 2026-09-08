## Why this exists

`std::vector` is the container to reach for by default, and it is also the one that will hand
you a pointer into its storage and then move that storage out from under you. Every standard
container documents exactly which operations invalidate which iterators, references and
pointers, and those rules are not the same from one container to the next. Learning them is
not trivia: an invalidated iterator is not a stale value that gives a wrong answer, it is a
read of memory the allocator has taken back, and — as with every lifetime bug in this
language — the run where it prints the right number is the dangerous one.

## The idea

An iterator is a seat number in a theatre. Renovate the theatre and the seats are renumbered.

### Where the analogy breaks

A renumbered seat still exists, and sitting in the wrong one is an embarrassment rather than a
catastrophe. An invalidated iterator names storage that has been handed back to the allocator.
Using it is undefined behaviour, and the most likely outcome is that the old row of seats is
still standing and you get a plausible number out of it.

Renovations are announced months in advance. `push_back` announces nothing. Whether it
relocates depends on the capacity at that moment, so the same line of code is harmless for one
input size and fatal for another, and the failing case is usually the larger one that only
production sees.

And a theatre has one seating plan. Each container has its own rules, per operation: a
`std::list` and a `std::map` keep every element in its own node, so inserting never moves
anything and references to existing elements stay valid. Listing 1 shows a `std::map`
reference surviving two insertions — the same code against a `std::vector` would be undefined.

## How it works

For `std::vector`, the rule is capacity. Insertion that keeps `size() <= capacity()` moves
nothing, and everything you hold stays valid; insertion past the capacity reallocates and
invalidates all of it. `reserve` is therefore not only a performance tool: it is how you
create a window in which iterators are guaranteed to survive. Erasing invalidates from the
erased position onward.

For the node-based containers — `std::list`, `std::map`, `std::set` — insertion invalidates
nothing, and erasing invalidates only the iterator to the erased element. That is the whole
reason to choose one: not speed, which usually favours the vector even where the asymptotics
do not, but the guarantee that a reference you handed out remains valid. `std::unordered_map`
sits in between — a rehash invalidates every iterator while references to the elements
survive it — and that rule is stated per operation on the container's reference page.

`erase` returns an iterator to the element after the one removed, and that returned value is
the only iterator you may keep using. The loop shape follows from that, and its distinguishing
feature is the missing `++` in the header:

```cpp
for (auto it = values.begin(); it != values.end(); ) {
    if (shouldRemove(*it)) { it = values.erase(it); } else { ++it; }
}
```

Most of the time you do not need the loop at all: `std::erase_if(container, predicate)`,
added in C++20, removes every matching element and returns how many it removed. It replaces
the older erase-remove pairing, whose failure mode was forgetting the second call and keeping
the stale tail.

The last part of the lesson is what a container promises about *order*. `std::map` and
`std::set` iterate in key order, which is specified, so printing them is reproducible on any
implementation. `std::unordered_map` iteration order is unspecified — listing 3 therefore
records its size, the total of its values and the fact that every key is findable, and never
the order it happened to produce here. The same distinction reaches the algorithms:
`std::sort` may reorder elements that compare equal, `std::stable_sort` may not, so only the
stable one has an order worth writing down.

## Common mistakes

**Keeping a pointer or iterator across a `push_back`.** No compile-time diagnostic. Compiled
without sanitizers, the program below printed `1` — the value that was there before the
reallocation, read out of freed storage:

```text
ERROR: AddressSanitizer: heap-use-after-free
READ of size 4 thread T0
    #0 in main v1.cpp:7
```

**Indexing past the end with `operator[]`.** `at()` throws; `operator[]` does not check, and
the read goes wherever the arithmetic lands:

```text
ERROR: AddressSanitizer: heap-buffer-overflow
READ of size 4 thread T0
    #0 in main v2.cpp:5
```

**Erasing while iterating with a range-based `for`.** The range-based loop holds iterators it
does not let you replace, so the returned iterator has nowhere to go — which is why the
explicit loop above exists.

**Assuming an index inside `capacity()` is inside `size()`.** Reserving sixteen and reading
element nine of a one-element vector is undefined behaviour a heap sanitizer cannot see: the
storage really was allocated, so the read lands inside it. `at()` catches this one, because it
checks `size()` rather than the allocation.

Addresses, thread numbers and machine paths are stripped from the quoted reports, and the
frame lists are cut after the first entry.

## Check yourself

<details><summary>Which <code>push_back</code> invalidates iterators?</summary>

One that grows the vector past its capacity, because that reallocates and relocates every
element. While `size()` stays within `capacity()`, nothing moves.

</details>

<details><summary>Why does an erasing loop assign the result of <code>erase</code> instead of incrementing?</summary>

Because the iterator passed to `erase` is invalidated by the call. The returned iterator names
the element after the removed one and is the only valid way to continue.

</details>

<details><summary>Why may a lesson print a <code>std::map</code> but not a <code>std::unordered_map</code>?</summary>

Because `std::map` iterates in key order, which the standard specifies, while
`unordered_map`'s iteration order is unspecified. Printing the second records one
implementation's bucket layout rather than anything about the program.

</details>

## Listings

1. `containers-and-the-iterator-you-invalidated-1.cpp` — which containers move their elements.
2. `containers-and-the-iterator-you-invalidated-2.cpp` — removing elements from a container you are walking.
3. `containers-and-the-iterator-you-invalidated-3.cpp` — order the standard promises, and order it does not.
