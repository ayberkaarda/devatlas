## Why this exists

Most languages keep a few values that are not quite things. A null is a hole where a
reference should be, an integer is a machine word the compiler knows about, a class is a
compile-time notion with no run-time existence. Ruby 3.4 has none of those exceptions: every
value is an object with a class, every class is itself an object, and the absence of a value
is an object too. That is not a slogan, it is a set of consequences. It is why `nil.to_a` is
a reasonable thing to write, why you can sort a class of your own by defining one operator,
why a class can be built and passed around at run time, and why the most common failure in a
Ruby program is a `NoMethodError` naming `nil` rather than a crash with nothing to read.

## The idea

Picture a hotel where every room has a telephone — including the linen cupboard. Ring any
room and something answers. The guest rooms give their names; the cupboard answers as well,
and reports that it is a cupboard with nothing in it. Because it answers, you can address it
the same way you address everything else. There is no separate procedure for "the room that
is empty", and no part of the building where the phone system stops.

### Where the analogy breaks

The cupboard's telephone takes a short list of questions. `nil` answers `to_s`, `to_a`,
`to_i`, `to_h`, `nil?`, `inspect` and the rest of what `NilClass` defines; ask it for
`upcase` and Ruby raises `NoMethodError`. So "everything is an object" does not mean
"everything accepts everything". A `nil` that reaches code expecting a String still fails —
it fails with a name and a line number rather than silently, which is the whole benefit.

Two more leaks. There is exactly one linen cupboard: `nil` is the single instance of
`NilClass`, `NilClass.new` raises, and `nil.equal?(nil)` is true by construction. And a hotel
suggests rooms are interchangeable containers you can put things in; `nil` is frozen in Ruby
3.4, so it holds no state and never will. Code that treats it as a mutable placeholder has
misread the model.

## How it works

Every value answers `class`, and every answer is itself a value with a class of its own. The
chain terminates at `Class`, which is its own class.

```ruby
3.class          # => Integer
nil.class        # => NilClass
Integer.class    # => Class
Class.class      # => Class
```

`ancestors` is the list a method call walks, in order, until something answers. `NilClass`
and `Integer` share the tail of that list with every other class, which is where `nil?`,
`inspect`, `frozen?` and `respond_to?` come from.

```ruby
NilClass.ancestors  # => [NilClass, Object, Kernel, BasicObject]
Integer.ancestors   # => [Integer, Numeric, Comparable, Object, Kernel, BasicObject]
```

Because `nil` is an object, its conversions are ordinary method calls, and they let a missing
value join a computation without a branch: `nil.to_a` is `[]`, so `[*tags, "ruby"]` works
whether `tags` holds an array or nothing. When you want the failure instead, ask for the
method that is not there and Ruby names it.

A class you define sits on the same chain, so the same mechanisms apply. Define `<=>` and
include `Comparable` and you get `<`, `>`, `between?`, `clamp`, `sort` and `min`; call
`freeze` in the constructor and mutation raises `FrozenError`. Nothing about that is special
to the standard library.

## Common mistakes

**Reading a method off a value that turned out to be `nil`.** This is the failure a Ruby
program produces most often, and Ruby 3.4 underlines the exact call:

```text
nil-method.rb:2:in '<main>': undefined method 'upcase' for nil (NoMethodError)

puts record[:nickname].upcase
                      ^^^^^^^
```

**Misspelling a method and expecting silence.** There is no silence; there is a suggestion:

```text
typo.rb:2:in '<main>': undefined method 'lenght' for an instance of String (NoMethodError)

puts title.lenght
          ^^^^^^^
Did you mean?  length
```

**Chaining `[]` through a hash that might be shallow.** `config[:client][:host]` raises
`undefined method '[]' for nil` when `:client` is absent; `config.dig(:client, :host)`
returns `nil` and stops.

**Assuming `p obj` is a safe way to show an object.** Without a custom `inspect`, Ruby prints
the class and an address that differs on every run, so it cannot go in a recorded output or
a test expectation. Define `inspect`, or print a property.

## Check yourself

<details><summary>Why can you call methods on <code>nil</code> at all?</summary>

Because `nil` is the single instance of `NilClass`, which sits on the ordinary ancestor chain
`[NilClass, Object, Kernel, BasicObject]`. Method lookup on `nil` works exactly as it does on
any other object.

</details>

<details><summary>What does <code>Class.class</code> return, and why does it matter?</summary>

`Class`. A class is an object whose class is `Class`, and `Class` is its own class, which is
where the chain ends. It matters because it means classes can be assigned, passed as
arguments and created at run time without any special mechanism.

</details>

<details><summary>If everything is an object, why does <code>nil.upcase</code> still fail?</summary>

Being an object means having a class and a lookup chain, not having every method. `NilClass`
does not define `upcase` and neither does anything above it, so lookup ends in
`NoMethodError`.

</details>

## Listings

1. `everything-is-an-object-including-nil-1.rb` — classes of values, classes of classes,
   ancestor chains, and `nil` as a frozen singleton.
2. `everything-is-an-object-including-nil-2.rb` — what `nil` converts to, what it refuses,
   and `&.` and `dig` as the two ways to survive it.
3. `everything-is-an-object-including-nil-3.rb` — a value class of your own on the same
   chain, with `Comparable`, a custom `inspect` and `freeze`.
