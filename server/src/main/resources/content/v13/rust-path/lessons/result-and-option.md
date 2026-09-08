## Why this exists

Two facts a function may have to report are that something is absent, and that something
failed. Most languages answer with a value that lies — a null reference that has the type of
a thing but is not one — or with an exception, which leaves the signature saying nothing about
what can go wrong and the caller free to ignore it. Rust 1.98 has neither null nor
exceptions for this purpose. Absence is `Option<T>`, which is `Some(T)` or `None`; failure is
`Result<T, E>`, which is `Ok(T)` or `Err(E)`. Both are ordinary enums, both appear in the
return type, and neither can be used as the thing inside without saying which case you are in.

## The idea

A `Result` is a returned parcel with the delivery outcome attached, not a parcel plus a
separate note filed somewhere. You cannot get at the contents without first reading which of
the two outcomes it is, and there is no drawer of undelivered items you might forget to check.

### Where the analogy breaks

A delivery note is *about* an outcome; a `Result` *is* the outcome, and there is no other
copy of the value hiding behind it. That matters for `unwrap`, which readers treat as
"reading the note". It is not: it asserts the good case and, when wrong, ends the program.
The failure is real and observable, and it is a decision rather than a formality: the
program aborts and says that `unwrap` was called on a `None` value.

A courier lets you ignore the note. Rust does not, quite: `Result` is marked as a value that
must be used, so discarding one produces a warning saying that an unused `Result`
must be used, and noting that it may be an `Err` variant. It is a warning rather than an error, so it can be
ignored, but not silently.

And a failed delivery leaves nothing behind. `Err` makes no such promise. It says the
function did not produce its value; it says nothing about what the function already did on the
way, and a function that writes a file and then fails has still written the file. Nothing in
the type system tells you which functions those are.

## How it works

`match` covers both cases and binds the inside of each. When only one case does anything,
`if let` is shorter. When the failing case can be dealt with immediately, `let ... else`
handles it and leaves the binding available for the rest of the function.

The `?` operator is the propagation you would otherwise write by hand: on `Ok` or `Some` it
unwraps, and on `Err` or `None` it returns from the enclosing function immediately, converting
the error type through `From` on the way out.

```rust
fn parse_version(raw: &str) -> Result<i64, VersionError> {
    let value: i64 = raw.trim().parse()
        .map_err(|_| VersionError::NotANumber(raw.to_string()))?;
    if value < 1 { return Err(VersionError::OutOfRange(value)); }
    Ok(value)
}

let kib = entry.size_bytes.map(|bytes| bytes / 1024).unwrap_or(0);
let versions: Result<Vec<i64>, VersionError> = raw.iter().map(|r| parse_version(r)).collect();
```

The combinators are worth learning as a group: `map` transforms the inside and leaves the
other case alone, `and_then` chains another fallible step without nesting, `ok_or` turns
absence into a failure with a reason attached, and `unwrap_or_else` supplies a default that is
only computed when it is needed. `collect` into a `Result` is the one people are surprised by:
a sequence of fallible steps becomes one `Result` holding the whole sequence, short-circuiting
at the first `Err`.

## Common mistakes

**Using `?` in a function that does not return one of these types.** The message names the
requirement and offers the signature:

```text
error[E0277]: the `?` operator can only be used in a function that returns `Result` or
`Option` (or another type that implements `FromResidual`)
3 |     let version: i64 = raw.parse()?;
  |                                   ^ cannot use the `?` operator in a function that returns `()`
```

**Calling a fallible function and discarding the answer.** Only a warning, and it is the one
worth turning into an error in a real project:

```text
warning: unused `Result` that must be used
6 |     store(0);
  |     ^^^^^^^^
  = note: this `Result` may be an `Err` variant, which should be handled
```

**Reaching for `unwrap` because the code will not compile otherwise.** It compiles, and then
it aborts at run time:

```text
thread 'main' panicked at ...:
called `Option::unwrap()` on a `None` value
```

`unwrap` earns its place where the invariant is genuinely local — a literal you just parsed,
a slot you just filled. Everywhere else it is a compile error postponed until a user finds it.

## Check yourself

<details><summary>What is the difference between <code>map</code> and <code>and_then</code>?</summary>

`map` applies a function that returns a plain value, so the result stays one level deep.
`and_then` applies a function that itself returns an `Option` or `Result`, and flattens, which
is what you want when the next step can also fail or be absent.

</details>

<details><summary>Why does <code>?</code> need a <code>From</code> impl?</summary>

Because the error leaving the function has the function's own error type, not the callee's.
`?` performs that conversion, so a missing impl is a compile error at the `?` rather than a
runtime surprise.

</details>

<details><summary>When is <code>expect</code> better than <code>unwrap</code>?</summary>

Almost always: the message becomes the panic text, so the failure says which invariant was
violated instead of only which method was called.

</details>

## Listings

1. `result-and-option-1.rs` — absence, and the combinators that respect it.
2. `result-and-option-2.rs` — a custom error type and `?` propagation.
3. `result-and-option-3.rs` — many fallible steps gathered into one answer.
