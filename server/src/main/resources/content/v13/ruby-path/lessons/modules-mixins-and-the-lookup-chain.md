## Why this exists

Ruby has single inheritance: a class has one superclass and no more. That would be a severe
limit if inheritance were the only way to share behaviour, and it is not. A module is a
bundle of methods with no instances of its own, and `include`, `extend` and `prepend` each
splice it into a lookup chain at a different place. This is how `Comparable` gives you seven
comparison methods for one operator, how `Enumerable` gives you dozens for one `each`, and
how a codebase shares behaviour across classes that have no ancestor in common. It is also
where "why is *this* method being called?" becomes a real question, and the answer is always
the same list.

## The idea

Method lookup is a switchboard with a fixed list of extensions, tried in order. When you call
`article.describe`, Ruby walks that list from the top and stops at the first extension that
answers. `include`, `prepend` and `extend` do not add behaviour by magic; they insert an
entry into the list, at a position each one determines. `Module#ancestors` prints that list,
and in Ruby 3.4 it is the complete answer to which definition a call will reach.

### Where the analogy breaks

A switchboard operator stops at the first answer and hangs up. Ruby's `super` keeps the
connection open: a method can answer *and* hand the call further down the list, which is what
`prepend` exists for. The wrapping module runs first, calls `super`, and decorates whatever
comes back — listing 1 shows a module turning `"Base"` into `"BASE!"` without the class
knowing.

The image also implies one list. There are two. `include` and `prepend` alter the list used
for a class's *instances*; `extend` alters the list of one particular object, which is why
`extend` inside a class body adds class methods — the class is the object being extended.
And the switchboard has no directory: a module may call a method the including class is
supposed to provide, and nothing checks that it does. The failure arrives at the call, not at
the `include`.

## How it works

`include` inserts the module immediately behind the class, so the class wins a name clash.
`prepend` inserts it in front, so the module wins and can call `super` to reach the class.
`extend` puts it in the receiver's own chain.

```ruby
class Included < Base
  include Auditable      # [Included, Auditable, Base, Object, Kernel, BasicObject]
end

class Prepended < Base
  prepend Auditable      # [Auditable, Prepended, Base, Object, Kernel, BasicObject]
end
```

The list is the whole rule. Read `ancestors` and you can predict every dispatch without
knowing anything else about the classes involved. A module appears in a chain once however
many times it is included, and a module cannot be instantiated: `Auditable.new` raises
`NoMethodError`, because a module is not a class.

The bargain the standard library offers is one method for many. Define `<=>` and include
`Comparable` and `<`, `<=`, `>`, `>=`, `==`, `between?` and `clamp` arrive, along with `sort`,
`min` and `max` from `Enumerable` on any collection of them. Listing 2 defines a `Version`
class this way and sorts `3.4.10` after `3.4.9`, which string comparison gets wrong.

The `included` hook is how a module contributes class methods as well as instance ones. Ruby
calls `Module.included(base)` when the module is mixed in, and the usual move is
`base.extend(ClassMethods)` — the module now adds to both chains at once, and each including
class gets its own state.

## Common mistakes

**Expecting `include` to override the class.** It does not. The class sits in front of an
included module; only `prepend` puts the module first, and `ancestors` says which.

**Calling `super` from a module with nothing beneath it.** The chain runs out and says so:

```text
lost-super.rb:2:in 'Loud#describe': super: no superclass method 'describe' for an instance of Plain (NoMethodError)
```

**Including `Comparable` without defining `<=>`.** The mixin has nothing to call, and the
failure names the comparison rather than the missing method:

```text
no-spaceship.rb:5:in 'Comparable#<': comparison of Broken with Broken failed (ArgumentError)
```

**Assuming a mixin is a checked contract.** It is not. A module whose methods call
`public_send(:missing)` includes cleanly and fails at the first call:

```text
NoMethodError: undefined method 'missing' for an instance of Empty
```

**Treating `extend` as a variant of `include`.** `obj.extend(M)` gives the methods to that
object alone; instances of its class do not get them, and `Registry.extend(M)` makes
`Registry.describe` a class method while `Registry.new.describe` still raises.

## Check yourself

<details><summary>How do you find out which definition a call will reach?</summary>

Read `TheClass.ancestors`. Lookup walks that list in order and stops at the first entry that
defines the method; `super` continues from that point.

</details>

<details><summary>When would you reach for <code>prepend</code> rather than <code>include</code>?</summary>

When the module must run before the class's own method and wrap it — logging, instrumentation,
memoisation. `prepend` puts the module in front, so it can do its work and call `super`.

</details>

<details><summary>What does <code>self.included(base)</code> let a module do that instance methods cannot?</summary>

Reach the including class itself, typically to `base.extend(ClassMethods)` so the module
contributes class methods too, and to set up per-class state at include time.

</details>

## Listings

1. `modules-mixins-and-the-lookup-chain-1.rb` — `include`, `prepend` and `extend` against the
   same module, with the ancestor list for each.
2. `modules-mixins-and-the-lookup-chain-2.rb` — `Comparable` from one `<=>`, and what it
   costs when the operator is missing.
3. `modules-mixins-and-the-lookup-chain-3.rb` — the `included` hook, per-class state, and a
   mixin that fails at the call rather than the include.
