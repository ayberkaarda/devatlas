## Why this exists

Ruby has two types that both look like text and are not interchangeable. `"status"` is a
String: a mutable container of characters that can be built up, sliced and appended to.
`:status` is a Symbol: a name, frozen, and the same object everywhere it appears in the
program. Choosing badly does not usually raise. It produces a hash lookup that returns `nil`,
a comparison that is always false, or a constant that grew an extra element every time a
method ran. Every one of those is a real bug that a reader has to trace back through the code
rather than read off a stack trace.

## The idea

A Symbol is a label printed on a filing cabinet; a String is a sheet of paper. There is one
label per drawer and it never changes — that is what makes it useful for finding the drawer.
Paper is for writing on: you can add to it, cross things out, and you may have many sheets
bearing the same words without any of them being the same sheet.

### Where the analogy breaks

Labels and paper are different materials; `Symbol` and `String` share most of an interface.
`:name.upcase`, `:name.length` and `:name.start_with?("na")` all work, and `upcase` returns
a `Symbol` rather than a String. What Symbol lacks is the mutating half: there is no
`upcase!`, and asking for one raises `NoMethodError`.

The image also suggests a label is inert, which understates what "same object" buys. Two
occurrences of `:name` anywhere in a program are one object, so comparing them is an identity
check rather than a character-by-character one, and using them as hash keys avoids hashing
text. Two occurrences of `"name"` are two objects in Ruby 3.4 — `"name".equal?("name")` is
`false` — and each is separately mutable, which is where shared-state bugs come from.

Finally, a label and a sheet of paper with the same word on them are not equal:
`:name == "name"` is `false`, and a Hash keyed by one will not find the other.

## How it works

Frozen is the property that separates them, and in Ruby 3.4 it is worth measuring rather than
remembering: `:name.frozen?` and `1.frozen?` and `nil.frozen?` and `(1..3).frozen?` are all
`true`, while `"name".frozen?`, `[].frozen?` and `{}.frozen?` are all `false`.

```ruby
:name.equal?(:name)        # => true
"name".equal?("name")      # => false
:name.frozen?              # => true
"name".frozen?             # => false
"name".to_sym.equal?(:name) # => true
```

A String literal can therefore be mutated in place, and a shared one mutated by accident. The
two unary operators say which you want: `+str` gives a mutable copy of a frozen string,
`-str` gives a frozen, deduplicated one. Adding `# frozen_string_literal: true` at the top of
a file freezes every string literal in it.

Hash keys are where the choice shows. A Hash compares keys with `eql?`, so `{ name: "Ada" }`
and `{ "name" => "Grace" }` are two entries, and data that arrived from outside the program —
parsed JSON, form parameters — carries String keys. Reaching for `parsed[:name]` returns
`nil`; `transform_keys(&:to_sym)` converts, and `fetch` turns a wrong guess into a `KeyError`
rather than a `nil` that travels.

One property of Hash is worth stating because programmers arriving from other languages will
not expect it: Ruby specifies that a Hash presents its entries in the order they were
created. Printing a Hash's keys is therefore a fact about the program, not an accident of one
run — unlike a hash map in most languages. Reassignment keeps an entry's place; deleting a
key and adding it again moves it to the end.

## Common mistakes

**Using a Symbol key on data that arrived with String keys.** No diagnostic, just `nil`.
`fetch` converts it into a failure at the point of the mistake:

```text
KeyError: key not found: :name
```

**Trying to mutate a Symbol.** There is no mutating half of the interface:

```text
NoMethodError: undefined method 'upcase!' for an instance of Symbol
```

**Mutating a frozen String.** Under `# frozen_string_literal: true`, appending to a literal
raises:

```text
frozen-literal.rb:3:in '<main>': can't modify frozen String: "row" (FrozenError)
```

Ruby 3.4 will warn about the same code without the magic comment when run with
`-W:deprecated`:

```text
chilled.rb:2: warning: literal string will be frozen in the future (run with --debug-frozen-string-literal for more information)
```

**Mutating a mutable constant.** A default argument is evaluated per call, so each caller
gets a fresh array; a constant is one object shared by every caller. Listing 3 records the
constant growing to `["ruby", "new", "new"]` while the default argument stays
`["ruby", "new"]`. Freezing the constant turns the second call into a `FrozenError`.

**Expecting `freeze` to be deep.** It is not. `["ruby"].freeze` freezes the array; the String
inside it is still mutable, and listing 3 mutates it.

## Check yourself

<details><summary>Why is <code>:name.equal?(:name)</code> true while <code>"name".equal?("name")</code> is false?</summary>

A symbol literal always denotes the same object, so the two occurrences are identical. A
string literal builds a new, unfrozen String each time it is evaluated, so the two are equal
in content and different objects.

</details>

<details><summary>Which should a Hash key be?</summary>

A Symbol when the key names something fixed in the program. A String when the key came from
outside it — parsed input, user data — because that is what the data already carries;
convert deliberately with `transform_keys` rather than guessing at the lookup.

</details>

<details><summary>Is it safe to print a Hash's key order in a test expectation?</summary>

Yes. Ruby specifies that a Hash presents entries in creation order, so the order is
reproducible. Note that deleting a key and re-adding it moves that entry to the end.

</details>

## Listings

1. `symbols-and-strings-two-ways-to-name-a-thing-1.rb` — identity, frozen-ness and the shared
   half of the interface, measured rather than asserted.
2. `symbols-and-strings-two-ways-to-name-a-thing-2.rb` — Hash keys, the String-keyed
   boundary, insertion order, and `Symbol#to_proc`.
3. `symbols-and-strings-two-ways-to-name-a-thing-3.rb` — `freeze`, `+str` and `-str`, the
   shared mutable constant, and the shallowness of `freeze`.
