## Why this exists

Every language draws a line between the values a condition treats as yes and the values it
treats as no, and every language draws it somewhere slightly different. JavaScript makes
`0` and `""` falsy. Python adds every empty collection. C has no booleans at the language
level at all. Ruby 3.4 draws the shortest line available: `nil` and `false` are the only
values a condition treats as no, and everything else — `0`, `0.0`, `""`, `[]`, `{}`, even the
string `"false"` — is yes. The rule takes one sentence and is worth a lesson because the
short line has a sharp edge: `||` and `||=`, the idioms most Ruby programs use for defaults,
cannot tell an absent value from a present `false`.

## The idea

Think of a condition as a doorman who has been given exactly two names to turn away. He does
not inspect anyone's luggage, count their party, or care whether their bag is empty. If your
name is not on his short list, you are in. An empty suitcase gets in. A suitcase containing
the word "false" gets in.

### Where the analogy breaks

A doorman gives a yes or a no. Ruby's `&&` and `||` give back one of their operands, not a
boolean: `nil || 8080` is `8080`, `0 || 8080` is `0`, and `1 && 2` is `2`. That is what makes
them useful for defaulting and it is exactly where the sharp edge lives, because
`false || 8080` is also `8080` — the operator cannot distinguish "nobody supplied a value"
from "somebody supplied `false`". The doorman image also suggests the decision is made about
the value; it is made about truthiness only, so a method ending in `?` that returns `nil` or
a String reads as a boolean at the call site while being nothing of the kind. Nothing in Ruby
3.4 enforces that a `?` method returns `true` or `false`.

## How it works

The list of falsy values has two entries and does not grow.

```ruby
[nil, false, 0, 0.0, "", " ", [], {}, :sym].reject { it }
# => [nil, false]
```

They are not equal to each other or to anything else: `nil == false` is `false`, and
`0 == false` is `false`. To get an actual boolean from a value, ask for one with `!!` or with
a predicate that means what you want — `nil?` for absence, `empty?` for an empty collection,
`zero?` for a number.

The defaulting idiom is where the rule bites. `settings[:verbose] || true` gives `true` when
the stored value is `false`, silently reversing the user's setting. `Hash#fetch` asks about
the key instead of the value, so it can tell the two cases apart, and with no default
argument it raises rather than handing back a `nil` that travels a long way before failing.

```ruby
settings = { retries: 0, verbose: false }
settings[:verbose] || true        # => true    (wrong)
settings.fetch(:verbose, true)    # => false   (right)
settings.fetch(:colour)           # raises KeyError
```

`||=` inherits the same blind spot, and memoisation is where it costs most. `@ok ||= expensive_check` never
caches a `false`, so the expensive call runs on every access. Listing 2 measures it: four
calls where one was intended. The repair is `return @ok if defined?(@ok)`, which asks whether
the variable was assigned rather than whether its value is truthy.

Safe navigation, `&.`, is the third tool. It skips the call when the receiver is `nil` and
evaluates to `nil`; it does not suppress anything else, so `false&.upcase` still raises.

## Common mistakes

**Defaulting a boolean with `||`.** No diagnostic, only a wrong answer — listing 2 records
`stored verbose -> false` next to `|| gave -> true`.

**Treating `nil` and `false` as the same.** `record[:admin]` being `false` and
`record[:nickname]` being absent both fail a bare truth test, and only `nil?` separates them.

**Expecting `""` or `[]` to be falsy.** Both are truthy, so a blank form field passes an
`if submitted` check. `empty?` is the question that was meant.

**Reaching for a key that is missing and letting the `nil` travel.** `fetch` turns it into a
failure at the point of the mistake:

```text
fetch-missing.rb:2:in 'Hash#fetch': key not found: :colour (KeyError)
```

**Assuming `&.` makes a chain safe.** It guards against `nil` only. On `false` it calls the
method and raises:

```text
NoMethodError: undefined method 'upcase' for false
```

## Check yourself

<details><summary>Which values does Ruby 3.4 treat as false in a condition?</summary>

`nil` and `false`. Nothing else — not `0`, not `0.0`, not `""`, not an empty array or hash.

</details>

<details><summary>Why is <code>config[:debug] || true</code> a bug?</summary>

Because `||` tests truthiness, not presence. When `config[:debug]` is `false` the expression
yields `true`, inverting a setting the caller made deliberately. `config.fetch(:debug, true)`
asks about the key and gives `false`.

</details>

<details><summary>What is wrong with <code>@ready ||= compute</code> when <code>compute</code> can return <code>false</code>?</summary>

`false` never sticks, so the memoisation never takes and `compute` runs on every call. Guard
with `defined?(@ready)` instead, which reports whether the variable was assigned.

</details>

## Listings

1. `truthiness-nil-and-false-1.rb` — the whole table of truthiness, and what `&&` and `||`
   actually return.
2. `truthiness-nil-and-false-2.rb` — the `||` defaulting bug, `fetch` as the repair, and a
   memoisation counted to show `||=` failing.
3. `truthiness-nil-and-false-3.rb` — `nil?`, `&.` and `empty?`, and what an `if` with no
   matching branch evaluates to.
