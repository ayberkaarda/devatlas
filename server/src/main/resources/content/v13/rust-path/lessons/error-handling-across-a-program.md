## Why this exists

One function returning `Result` is easy. A program is a stack of layers, and each layer fails
in its own vocabulary: a store fails because SQLite refused, a transfer fails because the
connection dropped, a command fails because the caller asked for something that is not there.
If every layer re-exports the layer below it, the top of the program ends up with a return
type naming every library it depends on, and changing a dependency becomes a breaking change.
If every layer flattens everything into a string, nothing above can decide anything. Rust 1.98
gives no exceptions and no stack unwinding to catch, so the shape of this has to be designed:
one error type per layer, conversions between them, and `?` doing the conversion silently.

## The idea

An error type is an escalation desk. The layer below reports a vendor fault code; the desk
translates it into an incident code this organisation uses, and the incident is what gets
passed upward. Callers learn one vocabulary, not every vendor's.

### Where the analogy breaks

A desk adds context on the way through. A `From` conversion need not, and often deliberately
loses information: a store layer can map every distinct SQLite failure onto a single
"store unavailable" code, on the grounds that no caller can do anything different about any of
them. That is a defensible design and it is lossy on purpose — but only if the detail survives
somewhere a developer can read, which is why an error usually carries both a code and a
message.

An escalation desk is staffed at run time and can be asked what it did. `From` is resolved
when the program is compiled, and a missing conversion is a build failure at the `?` rather
than a surprise in production. There is nothing to query and nothing that can be misrouted.

And a support code has one audience. An error type has two at once: a code that a program
branches on, which must be stable because changing it changes behaviour elsewhere, and a
message a person reads, which must be free to be reworded. Conflating them is the failure
mode. Code that matches on message text breaks when somebody fixes a typo.

## How it works

An error type is usually an enum with one variant per way the layer can fail. Implementing
`Display` gives it a rendering; implementing `std::error::Error` marks it as an error and lets
it report a `source`, which is the error beneath it. `From<Underlying>` is what `?` uses, so
the call site never mentions the conversion.

```rust
impl std::error::Error for StoreError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self { StoreError::BadNumber(inner) => Some(inner), _ => None }
    }
}

impl From<ParseIntError> for StoreError {
    fn from(error: ParseIntError) -> Self { StoreError::BadNumber(error) }
}

let version: i64 = raw.trim().parse()?;   // converts through the impl above
```

`Box<dyn Error>` is the type to reach for at the very top, where nothing above the function
branches on the failure and the only remaining job is to report it. Below that, a named type
is what lets a caller decide — retry, fall back, or give up. The rule of thumb is that a
function whose caller has to choose returns a named type, and a function whose caller only
has to report can box.

Note what `Err` does not tell you. It says the function did not produce its value. It says
nothing about what already happened: a function that wrote a partial file and then failed has
still written it, and cleaning that up is the function's own job, not the error's.

## Common mistakes

**Using `?` when no conversion exists.** The error is at the `?`, and it names both types:

```text
error[E0271]: type mismatch resolving `<i64 as FromStr>::Err == StoreError`
5 |     let version: i64 = raw.parse()?;
  |                            ^^^^^ expected `StoreError`, found `ParseIntError`
```

The repair is a `From` impl, not a `map_err` at every call site — one impl serves every `?` in
the crate.

**Implementing `Error` without `Display`.** `Error` requires it, and the message points at the
impl rather than at the use:

```text
error[E0277]: `StoreError` doesn't implement `std::fmt::Display`
4 | impl std::error::Error for StoreError {}
  |                            ^^^^^^^^^^ unsatisfied trait bound
note: required by a bound in `std::error::Error`
```

**Flattening the cause into the message.** This one produces no diagnostic at all, which is
why it survives review: `format!("store failed: {inner}")` compiles, reads fine in a log, and
throws away the structured cause that `source` would have kept. The consequence shows up later
as a caller that cannot distinguish two failures it needs to treat differently, and by then the
only evidence is a string.

## Check yourself

<details><summary>When is <code>Box&lt;dyn Error&gt;</code> the right return type?</summary>

At a boundary where every failure is handled the same way — reported and abandoned. It costs
the caller the ability to match on the failure, so it is wrong anywhere a caller has to decide.

</details>

<details><summary>Why should callers branch on a code rather than a message?</summary>

Because the message is prose and will be reworded, translated or made more specific. A code is
part of the contract, and changing one is a deliberate breaking change.

</details>

<details><summary>What does <code>source</code> give you that a formatted string does not?</summary>

The underlying error as a value, so a caller can walk the chain, downcast to a concrete type,
or render as much of it as it wants. A string has already thrown that away.

</details>

## Listings

1. `error-handling-across-a-program-1.rs` — one error type per layer, and the conversions into it.
2. `error-handling-across-a-program-2.rs` — a chain of causes, and a boxed error at the edge.
3. `error-handling-across-a-program-3.rs` — a stable code and a human message, and who reads which.
