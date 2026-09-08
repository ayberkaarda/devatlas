## Why this exists

A `for` loop hides three different things behind one syntax: something that can be iterated,
something that is being iterated, and something that produces values on demand. As long as
everything is a list the difference never shows. It shows the first time a function returns a
generator and the caller loops over it twice, getting results and then nothing; or the first
time a program reads a two-gigabyte file into a list comprehension and the machine starts
swapping. Python 3.13's glossary and its iterator protocol define exactly what each of the
three is, and the definitions answer both problems.

## The idea

An iterable is a book; an iterator is a bookmark. You can hand the same book to five readers
and each gets their own bookmark, starting at page one. A bookmark, though, only moves
forward: once it is at the end, it is at the end, and asking it for the next page gets you
nothing. A generator is stranger — it is a book that is being written one page ahead of the
reader, and only while somebody is reading.

### Where the analogy breaks

A book exists whether or not anyone reads it, and a generator's values largely do not. The
sequence a generator represents is never stored anywhere; only the paused function frame
exists, holding local variables and the position of the `yield` it stopped at. That is why
there is no page count: `len()` on a generator raises `TypeError`, because the answer would
require running the whole thing.

The bookmark image also implies you could move it back. You cannot. There is no rewind on an
iterator, and no way to peek at how much is left. If a second pass is needed the iterable is
asked for a fresh iterator, and a generator function is called again — which is a different
operation from re-iterating the exhausted generator object, and confusing the two is the most
common way to lose data silently.

## How it works

`iter(x)` asks an object for an iterator; `next(it)` asks the iterator for a value and raises
`StopIteration` when there are none left. A list returns a new iterator each time. An
iterator returns itself, which is what makes a generator single-pass.

```python
values = [1, 2, 3]
iter(values) is iter(values)    # False: a fresh iterator each time
it = iter(values)
iter(it) is it                  # True: an iterator is its own iterable
```

Calling a generator function runs none of its body. It builds a generator object; the body
advances only as far as the next `yield` each time a value is requested, and then stops
there, holding its state.

```python
def narrated():
    print("running")
    yield 1
g = narrated()      # nothing printed yet
next(g)             # prints "running", returns 1
```

That is where the memory goes: into one paused frame instead of into every value at once. A
list comprehension over a million items builds a million objects and a list to hold them; the
equivalent generator expression holds one at a time. `sys.getsizeof` on a generator gives the
same number whether it will yield ten values or ten million, and `tracemalloc` measures the
difference in a running program.

```python
sum([n * n for n in range(200_000)])   # peak: megabytes
sum(n * n for n in range(200_000))     # peak: kilobytes
```

Laziness also means a source can be endless. `itertools.islice` takes the first few values
from a sequence that never finishes, and the source produces exactly as many as were asked
for — listing 2 counts them.

## Common mistakes

**Iterating a generator twice.** The second pass yields nothing. It does not raise; the loop
body simply never runs, and a report comes out empty. Keep a list if you need two passes.

**Calling `len()` on a generator.** `TypeError: object of type 'generator' has no len()`.
Materialise it, or count while consuming.

**Returning `iter(self)` from `__iter__` on a stateful object.** Two loops then share one
position, and a nested loop consumes the outer one's values.

**Assuming a generator holds its values.** It holds a frame. If the underlying source is a
file that gets closed, or a list that gets cleared, the generator yields whatever is there
when it resumes — not what was there when it was created.

## Check yourself

<details><summary>Why does the second <code>list(g)</code> return <code>[]</code>?</summary>

`g` is an iterator, `iter(g)` is `g` itself, and it has already raised `StopIteration`. There
is no position to reset. Calling the generator function again makes a new one.

</details>

<details><summary>Where does a generator's memory actually go?</summary>

Into a single suspended frame: local variables and the instruction pointer. The values it
will yield do not exist yet, which is why its size does not depend on the length of the
sequence it represents.

</details>

<details><summary>How can a program loop over something infinite without hanging?</summary>

By taking a bounded prefix — `itertools.islice`, a `break`, or `zip` with a finite sequence.
The source only computes what is pulled from it.

</details>

## Listings

1. `iterables-iterators-generators-1.py` — iterable against iterator, exhaustion, and the
   single pass.
2. `iterables-iterators-generators-2.py` — laziness counted, and an endless source.
3. `iterables-iterators-generators-3.py` — memory measured with `tracemalloc`, printed as
   properties rather than byte counts.
