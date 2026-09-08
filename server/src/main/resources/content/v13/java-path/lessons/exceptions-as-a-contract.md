## Why this exists

`throws` is part of a method's signature, which makes it part of its published contract, and
it is the part most often filled in by accident. A method declares `throws Exception` because
that made the compiler stop complaining; a `catch` block turns a specific failure into a
generic one and drops the cause; a `finally` returns a value and the failure vanishes
entirely. Each of these is a decision about what a caller is allowed to do, taken without
noticing that a decision was being made.

## The idea

An exception is what the waiter comes back and says. Out of an ingredient, and you are asked
to choose again: a situation, and the answer is yours to give. A mistyped table number is a
mistake in the process, not a choice offered to you. The kitchen on fire is neither — nobody
is asking anyone to reorder.

### Where the analogy breaks

The conversation in a restaurant is between two parties who are both present. An exception
travels up an arbitrary number of frames, and the frame that catches it is very often nowhere
near the one that could act on it. That distance is why `throws Exception` on a signature is a
contract nobody can honour: the caller is told something may go wrong and given nothing to
decide with.

A waiter's second sentence also does not delete the first. In Java it can. A `catch` that
throws a new exception without passing the original along leaves `getCause()` returning
`null`, and a `return` inside `finally` discards the exception on its way out — listing 3
calls a method that throws and prints `-1`, with no failure anywhere in the output.

## How it works

JLS §11.1.1 draws the line: the unchecked exception classes are the run-time exception classes
and the error classes, and the checked exception classes are all the others — `Throwable` and
its subclasses except `RuntimeException`, `Error` and their subclasses. The specification also
says why you would define a new checked class: to take advantage of compile-time checking for
exception handlers.

That gives a rule of thumb with a reason behind it. Declare a checked exception when the
caller has a decision to make — a missing file, a malformed configuration value. Throw an
unchecked one when the caller has a bug — a null argument, an index out of range. There is
nothing a caller can usefully do about a bug except stop.

```java
static int parsePort(String raw) throws ConfigurationException {
    try { return Integer.parseInt(raw); }
    catch (NumberFormatException e) { throw new ConfigurationException("port: " + raw, e); }
}
```

Passing `e` as the cause is the whole difference between a report and a rumour. `Throwable`
carries the cause and exposes it through `getCause`, so translating a low-level failure into a
contract-level one keeps the evidence.

`try`-with-resources, specified in JLS §14.20.3, closes resources in the reverse of their
declaration order and settles the awkward case: if the body throws and a `close` also throws,
the body's exception propagates and the close failure is attached to it as a suppressed
exception, readable through `getSuppressed`.

```java
catch (RuntimeException e) {
    e.getMessage();                        // the body failed
    e.getSuppressed()[0].getMessage();     // close failed for resource
}
```

If the body succeeds and only `close` fails, there is nothing to suppress it under, so the
close failure is the one the caller sees. Listing 2 runs all three arrangements.

## Common mistakes

**Returning from `finally`.** The method completes normally and the exception is discarded.
`javac` accepts it; listing 3 shows the caller receiving `-1` instead of the failure.

**Catching and rethrowing without the cause.** `throw new IllegalStateException("failed")`
inside a `catch` block leaves `getCause()` as `null` and the original stack trace unrecorded.

**Declaring `throws Exception`.** Every caller must now handle or redeclare something they
cannot distinguish, so the checking survives and the information does not.

**Catching an exception to log it and continue.** The method returns as if it succeeded, and
the caller acts on a result that was never produced.

**Assuming a failing `close` is always suppressed.** It is suppressed only when something else
is already propagating. With a clean body, the close failure is thrown.

## Check yourself

<details><summary>Should a method that validates its arguments throw a checked exception?</summary>

No. A caller passing an invalid argument has a bug, and there is no recovery to offer them.
`IllegalArgumentException` is unchecked for that reason.

</details>

<details><summary>The body throws and <code>close</code> throws. Which one does the caller catch?</summary>

The body's. The close failure is attached to it as a suppressed exception and is available
from `getSuppressed()`, so neither one is lost.

</details>

<details><summary>What is wrong with <code>catch (NumberFormatException e) { throw new IllegalStateException("bad config"); }</code>?</summary>

It drops the cause. The caller sees that configuration failed and cannot see which value, or
where. Passing `e` to the constructor keeps both.

</details>

## Listings

1. `exceptions-as-a-contract-1.java` — checked out, unchecked in, and the caller's options.
2. `exceptions-as-a-contract-2.java` — closing order, suppression, and the three arrangements.
3. `exceptions-as-a-contract-3.java` — two ways to lose a failure completely.
