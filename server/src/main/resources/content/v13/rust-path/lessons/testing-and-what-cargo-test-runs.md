## Why this exists

Rust puts the test runner in the build tool, so there is no framework to choose and no
convention to argue about — but that also means `cargo test` does several distinct things
under one word, and a developer who does not know which is which will eventually wonder why a
test can see a private function, or why the same test cannot see it once moved. Running it on
a crate with unit tests, an integration test and a documentation example builds and runs
**three separate binaries** and reports them separately. Knowing that is most of knowing where
to put a test.

## The idea

`cargo test` is a second, parallel build of your crate, made with a different switch thrown.
The ordinary build and the test build come off the same source but are not the same program:
the test build contains code the shipped one does not, and it has an entry point your crate
never wrote.

### Where the analogy breaks

"A second build" suggests one extra artefact, and there are three. Unit tests are compiled
*into* the crate with `cfg(test)` set, so they see private items. Each file under `tests/` is
compiled as its own separate crate that links your crate from the outside, so it sees only
what is `pub` — reaching for a private function there is a build error, not a visibility
warning. Documentation examples are extracted from doc comments and compiled as further
programs again.

The parallel-build picture also hides that the ordinary build still happens. Compiling the
crate under test on this toolchain reports dead-code warnings from the non-test build for a
function that only the unit tests call — the warning is true of the shipped library, and the
tests do not silence it.

And "throwing a switch" makes it sound free. It is not: the test binary is a different binary,
so anything conditioned on `cfg(test)` exists only there. A helper written inside
`#[cfg(test)] mod tests` cannot be called from the shipped code, and a bug that only appears
when it is absent will not be caught by any test.

## How it works

A test is a function marked `#[test]`, taking no arguments, that fails by panicking. Unit
tests conventionally live in a `#[cfg(test)] mod tests` in the same file as the code, with
`use super::*;` to bring the module's items into scope.

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn queued_is_not_terminal() { assert!(!is_terminal("QUEUED")); }

    #[test]
    #[should_panic(expected = "DIGEST_MISMATCH")]
    fn a_panic_can_be_the_expectation() { panic!("DIGEST_MISMATCH while verifying"); }

    #[test]
    #[ignore = "needs a server on the network"]
    fn a_live_test_is_opt_in() { }
}
```

`assert!`, `assert_eq!` and `assert_ne!` are the whole vocabulary; `assert_eq!` prints both
values on failure, which is why it needs `Debug`. A test body may return `Result`, which lets
it use `?` on a fallible setup step instead of unwrapping and losing the reason.

`#[ignore]` keeps a slow or network-dependent test out of the default run; `cargo test --
--ignored` runs exactly those. `#[should_panic]` expects a panic, and without an `expected`
fragment any panic at all satisfies it — including one from a typo three lines above the
assertion, which is how a `should_panic` test passes while testing nothing.

## Common mistakes

**A test module that cannot see the code it is testing.** The module is a child scope, not
the file:

```text
error[E0425]: cannot find function `is_terminal` in this scope
9 |         assert!(is_terminal("DONE"));
  |                 ^^^^^^^^^^^ not found in this scope
help: consider importing this function
7 +     use crate::is_terminal;
```

**Moving a unit test into `tests/` and expecting it to still reach inside.** It is a different
crate now:

```text
error[E0603]: function `normalise` is private
3 |     assert_eq!(testdemo::normalise(" done "), "DONE");
  |                          ^^^^^^^^^ private function
```

**Trying to parameterise a test by giving it arguments.** The harness has no way to supply
them, and says so without an error code:

```text
error: functions used as tests can not have any arguments
4 | /     fn parses(raw: &str) {
5 | |         assert_eq!(raw.len(), 2);
6 | |     }
  | |_____^
```

The answer is a table inside one test, or a helper the test calls once per case. That also
puts the failing input in the failure message, which a parameterised runner would have had to
be asked for.

## Check yourself

<details><summary>A test needs a private function. Where does it go?</summary>

In a `#[cfg(test)]` module in the same file, where `cfg(test)` compiles it into the crate and
privacy does not apply. A file under `tests/` links from outside and can only reach `pub`
items.

</details>

<details><summary>Why is <code>#[should_panic]</code> without <code>expected</code> risky?</summary>

Any panic satisfies it, including one from a mistake in the test's own setup. The test then
passes without ever exercising the failure it claims to check.

</details>

<details><summary>What does returning <code>Result</code> from a test buy?</summary>

`?` on the setup steps, so a failure reports its own cause instead of a bare unwrap panic. The
test fails when it returns `Err`.

</details>

## Listings

1. `testing-and-what-cargo-test-runs-1.rs` — what an assertion failure actually reports.
2. `testing-and-what-cargo-test-runs-2.rs` — a table of cases, and a body that returns `Result`.
3. `testing-and-what-cargo-test-runs-3.rs` — what a test build contains that a normal build does not.
