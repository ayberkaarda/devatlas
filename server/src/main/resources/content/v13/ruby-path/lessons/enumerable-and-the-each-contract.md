## Why this exists

Most of the Ruby you write against collections — `map`, `select`, `group_by`, `reduce`,
`min_by`, `tally`, `sum`, `partition`, `lazy` — is not defined on `Array` or on `Hash`. It is
defined once, in the `Enumerable` module, in terms of a single method the collection must
supply: `each`. `Array` includes it. `Hash`, `Range`, `Set`, `Struct` and `IO` include it. So
can a class of yours, and the moment it does, the whole vocabulary arrives. Knowing that this
is one interface rather than a long list of methods per class changes what you reach for: you
stop writing loops that build arrays and start naming the operation you meant.

## The idea

`Enumerable` is a socket with one pin. Supply `each` — a method that yields the collection's
elements one at a time — and roughly sixty methods plug into it. They are all written in
terms of that one call, so `Enumerable` does not need to know whether the elements come from
an array, a file, a database cursor or a generated sequence.

### Where the analogy breaks

A socket carries power in one direction; `each` and its callers negotiate. `each` yields a
value and receives whatever the block returned, which is how `map` builds a result and
`select` decides what to keep. And a socket is either connected or not, whereas `Enumerable`
consumes lazily or eagerly depending on the method: `first(2)` on an `Enumerator` runs the
source only until it has two values, while `select` on a Range runs it to the end.

The image also suggests supplying `each` is all there is to it. Two obligations come with it.
Return an `Enumerator` when no block is given — `return to_enum(:each) unless block_given?` —
or `each` with no block will yield nothing where every standard collection would hand back an
iterator. And accept that ordering is yours to guarantee: `Enumerable` visits in the order
`each` yields, so a class that yields in an arbitrary order gives arbitrary results, and
`sort_by` is not promised to be stable when keys tie.

## How it works

The contract is one method. Every method `Enumerable` defines in Ruby 3.4 is written in terms
of `each` and nothing else, so listing 1 defines a `Shelf` whose only instance method is
`each`, and then sorts it, groups it, slices it, reduces it and asks it for its minimum.

```ruby
class Shelf
  include Enumerable

  def each
    return to_enum(:each) unless block_given?

    @books.each { |book| yield book }
    self
  end
end
```

Building an answer out of a collection comes in three shapes, and choosing the right one is
most of what makes Enumerable code readable. `reduce` carries one accumulator and returns it.
`each_with_object` carries a mutable accumulator so the block does not have to hand it back
each time. `group_by` and `partition` split rather than fold. `filter_map` and `flat_map`
exist to skip the intermediate array that `map` followed by `compact` or `flatten` would
build.

An `Enumerator` is an `each` that has not run yet, and it is what an iterator returns when you
give it no block. It supports external iteration — `next`, and `StopIteration` at the end —
and `Enumerator.new` builds one from a block that is handed a yielder, running nothing until
something asks.

`lazy` is where this stops being tidy and starts being necessary. It turns a chain into one
that pulls a single element through every step, so a chain over `(1..Float::INFINITY)`
terminates. Listing 3 counts the block calls: eager `select` over `(1..20)` calls the block
twenty times to produce a result the caller takes two elements from; the lazy version calls
it four.

## Common mistakes

**Including `Enumerable` without defining `each`.** The module has nothing to call, and the
diagnostic names the missing method rather than the include:

```text
no-each.rb:5:in 'Enumerable#sort': undefined method 'each' for an instance of Empty (NoMethodError)
```

**Writing a loop that `map` already is.** `result = []; list.each { result << f(it) }` is
`list.map { f(it) }`, and the difference is not only length: the second names what is
happening and cannot leave a half-built accumulator behind on an early return.

**Recording the output of `sort_by` when keys tie.** Ruby does not promise a stable sort, so
two elements with equal keys may come out in either order. Make the key unique —
`sort_by { [it.length, it] }` — when the order has to be reproducible.

**Chaining eagerly over something endless.** `(1..Float::INFINITY).select(&:even?)` never
returns. `lazy` before the chain is what makes it terminate.

**Expecting `each` to resume where the last one stopped.** Each call builds a fresh
`Enumerator`, so `c.each.next` twice returns the first element twice. Keep the enumerator in
a variable to iterate externally; listing 3 records both.

## Check yourself

<details><summary>What is the minimum a class must provide to gain <code>Enumerable</code>?</summary>

`include Enumerable` and an `each` that yields the elements. Everything else in the module is
written in terms of that call. Returning `to_enum(:each)` when no block is given is what makes
it behave like the built-in collections.

</details>

<details><summary>When would you choose <code>each_with_object</code> over <code>reduce</code>?</summary>

When the accumulator is mutable and you would otherwise have to return it from the block on
every iteration — building a Hash or an Array. `reduce` fits when each step produces a new
value, such as a running total.

</details>

<details><summary>What does <code>lazy</code> change about a chain?</summary>

It pulls one element through the whole chain at a time instead of materialising an array at
each step, so a chain over an endless source can terminate and a chain that only needs a few
elements does only the work those elements require.

</details>

## Listings

1. `enumerable-and-the-each-contract-1.rb` — one `each`, and the vocabulary that arrives with
   it, plus what happens when `each` is missing.
2. `enumerable-and-the-each-contract-2.rb` — `reduce`, `each_with_object`, `group_by`,
   `filter_map` and `tally` over a small ledger.
3. `enumerable-and-the-each-contract-3.rb` — `Enumerator`, `StopIteration`, `to_enum`, and
   `lazy` with the block calls counted.
