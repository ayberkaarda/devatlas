## Why this exists

Ruby's collection methods, its file handling, its transaction wrappers and its test
frameworks all use the same device: a method takes, alongside its ordinary arguments, a piece
of code the caller wrote and the method decides when to run. That device is the block. It is
why `File.open` can guarantee the file is closed, why `each` does not need an index variable,
and why so much Ruby reads as though the language had a keyword for whatever you are doing.
Learning the syntax takes an afternoon; learning where the control actually sits — with the
method, not the caller — is what stops the surprises.

## The idea

A block is a set of instructions you hand to a contractor along with the job. You say what
should happen to each item; the contractor decides how many items there are, what order they
come in, and whether to do the job at all. You are not running the loop. You wrote the body
of somebody else's loop and posted it to them.

### Where the analogy breaks

Instructions on paper are inert; a block is not. It closes over the variables where it was
written, so it can read and change them while the contractor runs it, which is how
`each_with_object` and memoised counters work. Instructions also travel one way, and a block
answers back: `yield` evaluates to whatever the block returned, and methods such as `map` and
`select` are built entirely on that return value.

The sharper break is control flow. A `return` inside a non-lambda block does not end the
block; it ends the method the block was *written* in, unwinding through the contractor
mid-job. A lambda behaves like the paper instructions — its `return` ends the lambda and
nothing more. The two are both `Proc` objects in Ruby 3.4 and differ in exactly two places:
this one, and whether a mismatched argument count raises.

## How it works

A method receives a block implicitly. `yield` runs it, `block_given?` says whether there is
one, and the value of `yield` is the block's value.

```ruby
def each_word(sentence)
  return "no block given" unless block_given?

  sentence.split.each { |word| yield word }
end
```

Ruby 3.4 offers three spellings for a one-argument block: an explicit parameter, the
numbered `_1`, and `it`. All three produce the same result, and `&:upcase` is a fourth for
the case where the block only sends one message to its argument.

```ruby
[1, 2, 3].map { |n| n * 2 }   # => [2, 4, 6]
[1, 2, 3].map { _1 * 2 }      # => [2, 4, 6]
[1, 2, 3].map { it * 2 }      # => [2, 4, 6]
%w[a b].map(&:upcase)         # => ["A", "B"]
```

Prefixing a parameter with `&` captures the block as a `Proc` you can store, inspect and pass
on. Passing it to another method costs one `&` in each direction. A `Proc` built from a block
is not a lambda: it pads missing arguments with `nil`, drops extra ones, and its `return`
leaves the enclosing method.

The pattern all of this exists for is the one in listing 3: a method opens a resource, yields
it, and closes it in an `ensure` clause. The caller cannot forget the cleanup because the
caller never had the chance to skip it — the block ends and the method resumes.

## Common mistakes

**Calling a method that yields without giving it a block.** `yield` has nothing to call:

```text
no-block.rb:2:in 'block in Object#each_word': no block given (yield) (LocalJumpError)
```

Most standard-library iterators avoid this by returning an `Enumerator` instead;
`[1, 2].each` with no block is not an error.

**Mixing `it` with a named parameter.** Ruby 3.4 rejects it at parse time:

```text
it-and-param.rb:2: syntax error found (SyntaxError)
  1 | numbers = [1, 2, 3]
> 2 | puts numbers.map { |n| it * 2 }.inspect
    |                        ^~ 'it' is not allowed when an ordinary parameter is defined
```

**Using `do ... end` where `{ ... }` was needed.** `{ }` binds to the nearest method call;
`do ... end` binds to the outer one. There is no diagnostic, only a wrong answer — here the
block goes to `report` instead of `map`, so `report` receives an `Enumerator` of the original
values and sums them undoubled:

```text
sum: 6
sum: 12
```

**Giving a lambda the wrong number of arguments.** Unlike a proc, it refuses:

```text
lambda-arity.rb:1:in 'block in <main>': wrong number of arguments (given 1, expected 2) (ArgumentError)
```

## Check yourself

<details><summary>What does <code>yield</code> evaluate to?</summary>

The value of the block. That is what lets `map`, `select` and `sort_by` be written in terms
of `yield` — they collect or test what the block hands back.

</details>

<details><summary>Name the two differences between a proc and a lambda.</summary>

Argument checking — a proc pads and drops, a lambda raises `ArgumentError`. And `return` — in
a proc it returns from the enclosing method, in a lambda it returns from the lambda.

</details>

<details><summary>Why does a resource-managing method use a block rather than returning the resource?</summary>

Because the method keeps control. It can wrap the `yield` in `ensure`, so the resource is
released whether the block finishes, returns early, or raises.

</details>

## Listings

1. `blocks-the-argument-that-is-code-1.rb` — `yield`, `block_given?`, the three one-argument
   spellings, and what happens with no block.
2. `blocks-the-argument-that-is-code-2.rb` — capturing with `&`, relaying a block, and the
   two differences between procs and lambdas.
3. `blocks-the-argument-that-is-code-3.rb` — a method that lends a resource and takes it back
   in `ensure`, on both the normal and the failing path.
