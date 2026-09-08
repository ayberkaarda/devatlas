## Why this exists

When method lookup walks a chain and nothing answers, Ruby does not give up. It calls
`method_missing` on the receiver, passing the name and the arguments, and the default
implementation is the one that raises `NoMethodError`. Override it and an object can answer to
names nobody wrote down: columns of a database row, keys of a parsed document, every method of
some other object it is wrapping. This is the mechanism behind a good deal of what reads as
magic in Ruby libraries. It is also the mechanism behind a good deal of what makes those
libraries hard to debug, and the two are the same feature seen from different ends.

## The idea

`method_missing` is a receptionist who takes messages for people who do not work here. Most
callers get through to a real employee. When they ask for someone with no desk, the
receptionist decides: answer on their behalf, or say nobody of that name works here. The
building keeps functioning, and from outside it looks as though the staff list is much longer
than it is.

### Where the analogy breaks

A receptionist knows who is on the staff list and can be asked. This one cannot: the object's
`respond_to?`, its `methods` array and `method(:name)` are all computed from real definitions,
and `method_missing` adds none. So the object answers `row.title` while telling every
reflective question that `title` does not exist, and any caller polite enough to ask first —
which is what duck typing means in practice — walks away. Listing 1 shows a `render` method
doing exactly that.

The repair is `respond_to_missing?`, and it repairs only half. It teaches `respond_to?` and
`method` the same rule `method_missing` uses; it does not add anything to `methods`, because
nothing was defined. Define the pair together or neither — the two are one feature split
across two hooks.

## How it works

`method_missing` receives the name as a `Symbol` plus whatever arguments were passed. Handle
the names you know and call `super` for the rest, so that unknown names still raise.

```ruby
def method_missing(name, *args)
  return @fields[name.to_s] if @fields.key?(name.to_s)

  super
end

def respond_to_missing?(name, include_private = false)
  @fields.key?(name.to_s) || super
end
```

`super` here reaches `BasicObject#method_missing`, which in Ruby 3.4 is what produces the
ordinary `NoMethodError` diagnostic. Omitting it is the single most expensive mistake
available: every unknown name then returns whatever the handler returns, usually `nil`, and a
misspelling becomes indistinguishable from a legitimately absent value.

`method_missing` runs only when lookup has already failed, so it can never intercept a name
the object already has. An object carrying a field called `class` or `frozen?` cannot expose
it this way — those names resolve on `Object` long before `method_missing` is reached.

When the set of names *is* known at class-definition time, `define_method` does the same job
without the costs: the methods are real, so `respond_to?`, `methods`, `method` and every tool
built on them work, and unknown names raise without any code of yours. Reserve
`method_missing` for the case where the names genuinely cannot be enumerated in advance —
forwarding to another object's whole interface, as in listing 3.

## Common mistakes

**Forgetting `super`.** Listing 3 records the result: `s.titel` returns `nil`,
`s.frobnicate` returns `nil`, `s.save!(1, 2)` returns `nil`. Nothing ever raises again.

**Defining `method_missing` without `respond_to_missing?`.** The object works and lies:

```text
no-method-object.rb:6:in 'Kernel#method': undefined method 'title' for class 'Row' (NameError)
```

while `Row.new.title` on the line above returns a value quite happily.

**Expecting to intercept an inherited name.** `sh.class` returns the class, not the stored
field, because lookup never fails for `class` and so `method_missing` never runs.

**Forwarding without checking the target.** `@target.public_send(name, ...)` on a name the
target does not have raises from inside the wrapper, naming the target rather than the
wrapper. Guard with `@target.respond_to?(name)` and `super` otherwise; listing 3's `Audited`
then reports honestly:

```text
NoMethodError: undefined method 'explode' for an instance of Audited
```

**Reaching for it when `define_method` would do.** If you can list the names, list them. The
static version passes every reflective test and needs no hooks at all.

## Check yourself

<details><summary>Why must <code>method_missing</code> end with <code>super</code>?</summary>

Because `BasicObject#method_missing` is what raises `NoMethodError`. Without `super`, names
the handler does not recognise return the handler's value instead of failing, so typos
become silent `nil`s.

</details>

<details><summary>What does <code>respond_to_missing?</code> fix, and what does it not?</summary>

It fixes `respond_to?` and `method`, so duck-typed callers and `Method` objects work. It does
not add anything to `methods` or `instance_methods`, because no method was ever defined.

</details>

<details><summary>When is <code>define_method</code> the better tool?</summary>

Whenever the names are known when the class is defined. The methods are real, reflection is
correct without extra hooks, and unknown names raise for free.

</details>

## Listings

1. `method-missing-and-the-price-of-magic-1.rb` — dynamic attributes, and the three
   reflective questions the object gets wrong.
2. `method-missing-and-the-price-of-magic-2.rb` — `respond_to_missing?` as the repair, and
   `define_method` as the alternative that needs no repair.
3. `method-missing-and-the-price-of-magic-3.rb` — the missing `super`, the name that cannot
   be intercepted, and a forwarding wrapper where the mechanism earns its place.
