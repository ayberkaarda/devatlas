## Why this exists

Kotlin's standard library ships five functions — `let`, `run`, `with`, `apply` and `also` —
that all do roughly the same thing: run a block with an object in scope. They are used
constantly, chosen by feel, and swapped for one another during review by people who cannot
say why one is better. That is a bad state for a construct this common, because the choice
is not cosmetic: two of them return the object and three return whatever the block
evaluated to, so picking the wrong one changes the value of the expression rather than only
its style.

## The idea

Five doors into the same room. What differs is what the object is called once you are inside
— `this` or `it` — and what you are carrying when you come out — the object, or the block's
result.

### Where the analogy breaks

A door is a neutral choice; these are not, and two of the differences bite.

`this` and `it` are not two spellings of one idea. A `this`-receiver block can read the
object's members without a prefix, which is what makes `apply` good for configuration, but
it also *shadows*: inside a nested `apply` the inner receiver hides the outer one, and a
bare `append` goes somewhere you did not intend. The third listing prints `outer-1-3`, with
the `-2` having gone into a builder that was discarded. An `it`-block can be given a name,
and a named parameter cannot be shadowed by accident.

And the return value is not a detail you can decide later. `let` returns the block's result,
so a block ending in `println` returns `Unit`; the second listing prints `kotlin.Unit` where
a reader expected `ada`. `also` returns the receiver and is the one that belongs in a chain.
This is the difference that survives into production, because nothing about the code looks
wrong.

## How it works

Two questions settle every case. Is the object the subject of the block, or an argument to
it? And does the expression need the object back, or a new value?

- `apply` — the object is `this`, and the expression evaluates to the object.
- `also` — the object is `it`, and the expression evaluates to the object.
- `run` and `with` — the object is `this`, and the expression evaluates to the block.
- `let` — the object is `it`, and the expression evaluates to the block.

```kotlin
val configured = Server().apply { host = "example.invalid"; port = 8080 }  // Server
val url = configured.run { "https://$host:$port" }                        // String
val logged = configured.also { println("port ${it.port}") }               // Server
val hostLength = configured.let { it.host.length }                        // Int
val summary = with(configured) { "$host/$port" }                          // String
```

`with` is the odd one: it is not an extension, so it takes the receiver as an argument and
cannot be used after `?.`. That is the practical reason `let` and not `with` is the one you
see guarding a nullable value:

```kotlin
lookup(id)?.let { it.uppercase() } ?: "not found"
```

`takeIf` and `takeUnless` sit alongside them and answer a related question: they turn a
predicate into a nullable value, so a condition can be written as a link in the same chain
rather than as an `if` around it.

## Common mistakes

**Using `it` inside an `apply`.** There is no `it` in a `this`-block, and the compiler is
blunt about it:

```text
error: unresolved reference 'it'.
```

**Ending a `let` with a side effect.** No diagnostic; the value is simply wrong. The second
listing prints:

```text
let with a println returned: kotlin.Unit
also with a println returned: ada
```

**Nesting `this`-blocks.** Also silent. The inner receiver wins for the length of its block,
so an unqualified member call inside a nested `apply` targets the inner object. Naming the
parameter — `let { req -> ... }` — removes the ambiguity, and it is worth doing as soon as
two objects are in scope.

**Using a scope function to save a line.** `person.let { it.name }` is `person.name` with
extra ceremony. These functions earn their place when they change the shape of an expression
— guarding a null, configuring an object at its declaration, inserting a side effect into a
chain — and cost readability everywhere else.

## Check yourself

<details><summary>Which one would you use to add a header to a request and keep the request?</summary>

`apply`. The block is configuration, the members are used unqualified, and the expression
has to evaluate to the request so it can be assigned or passed on.

</details>

<details><summary>Why can <code>with</code> not be used after a safe call?</summary>

Because it is a plain function taking the object as its first argument, not an extension on
it. `?.` needs a receiver to skip. That is the mechanical reason the null-guard idiom is
written with `let`.

</details>

<details><summary>Two chained steps, one logging and one transforming. Which is which?</summary>

`also` for the logging step: it hands the value on unchanged. `let` for the transformation:
its result is the value the chain continues with. Swapping them turns the log line into the
chain's value and quietly drops the transform.

</details>

## Listings

1. `scope-functions-1.kt` — all five side by side with what each returns.
2. `scope-functions-2.kt` — the null guard, and the return-value trap.
3. `scope-functions-3.kt` — nesting, shadowing, and a chain that reads in order.
