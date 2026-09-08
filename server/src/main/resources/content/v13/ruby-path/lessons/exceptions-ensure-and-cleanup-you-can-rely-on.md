## Why this exists

A program that opens something has to close it, and it has to close it on the path where
everything went wrong as much as on the path where it did not. Ruby's answer is four clauses —
`begin`, `rescue`, `else`, `ensure` — plus `retry`, and the useful part is knowing precisely
when each runs. `ensure` runs on success, on failure, and on an early `return`; `else` runs
only when nothing was raised; a bare `rescue` catches less than people assume; and `ensure`
has two ways of quietly destroying information that a reader of the code will not see.
Getting this exactly right is the difference between a resource leak you find in production
and one that cannot happen.

## The idea

Think of a `begin` block as a stage performance. The body is the act. `rescue` is the
understudy who steps in when the act goes wrong. `else` is the encore, performed only if the
act finished without incident. `ensure` is the stage crew, who strike the set whatever
happened — applause, disaster, or the lead walking off halfway through.

### Where the analogy breaks

The crew can also change the ending. An explicit `return` inside `ensure` discards the
exception that was travelling, so the caller sees a normal return and never learns anything
went wrong — listing 1 records a method raising `IOError` and returning `:nothing_happened`.
And an exception *raised* inside `ensure` replaces the one that was in flight; the original
survives only as `#cause`. Stage crew do not usually rewrite the play, and this crew can.

The analogy also implies the understudy takes any emergency. A bare `rescue => e` catches
`StandardError` and its descendants only. `ScriptError`, `SystemExit`, `SignalException` and
`NoMemoryError` are siblings of `StandardError` under `Exception`, not children of it, so they
pass straight through — which is the intended design, because a `Ctrl-C` or an
`exit` is not the caller's business.

## How it works

A method body is an implicit `begin`, so the clauses can be written straight into it. Listing
1 prints the order: body, then `rescue` or `else`, then `ensure`, on both paths.

```ruby
def parse(text)
  Integer(text)
rescue ArgumentError => e
  -1
else
  :parsed_cleanly
ensure
  release_something
end
```

That listing also records a trap worth knowing before you write your first `else`: when an
`else` clause is present and nothing was raised, *its* value is the value of the expression.
`parse("42")` returns `:parsed_cleanly`, not `42`. The body's result is discarded.

Exception classes form a hierarchy and that is the whole interface: a caller chooses its
granularity by choosing which class to name. Define `Store::Error < StandardError` and
`Store::NotFound < Store::Error`, and a caller can rescue one operation's failure or the whole
subsystem's. Clauses are tried in order, so specific ones come first, and one clause can name
several classes.

Re-raising inside a `rescue` records what was being handled at the time. In Ruby 3.4
`Exception#cause` is set automatically — nothing has to be passed along — so a wrapped
failure keeps its origin:

```ruby
rescue ArgumentError
  raise Store::Error, "config is unreadable"
  # e.cause => ArgumentError: invalid value for Integer(): "not a number"
```

`retry` re-runs the `begin` body from the top. It needs a bound: a counter in scope, a test
before retrying, and a bare `raise` to re-raise the last exception when the budget is spent.
Listing 3 puts that together with an `ensure` that closes the connection, and shows the open
and close counts matching on all three paths.

## Common mistakes

**Returning from `ensure`.** The exception is discarded and the caller is told everything was
fine. Listing 1 records `swallowing -> :nothing_happened` where an `IOError` was raised.

**Raising from `ensure`.** The cleanup failure replaces the real one:

```text
caller saw     -> ArgumentError: the cleanup problem
original       -> IOError: the real problem
```

**Rescuing `Exception`.** It catches `SystemExit` and `SignalException` too, so the process
stops responding to `exit` and to `Ctrl-C`. Rescue `StandardError` — which is what a bare
`rescue` already means.

**Assuming a bare `rescue` catches everything.** It does not catch `NotImplementedError`,
because that is a `ScriptError`:

```text
NotImplementedError    -> [NotImplementedError, ScriptError, Exception]
escaped to Exception -> NotImplementedError: no backend
```

**Retrying without a bound.** `retry` with no counter is an infinite loop that looks like
error handling. Count the attempts, and `raise` when they run out.

**Putting the general clause first.** `rescue StandardError` above `rescue Store::NotFound`
means the second is never reached; clauses are tried top to bottom.

## Check yourself

<details><summary>Does <code>ensure</code> run when the method returns early?</summary>

Yes. It runs before the value leaves the method, on an early `return`, on a normal fall-through,
and while an exception is unwinding.

</details>

<details><summary>What does a bare <code>rescue => e</code> catch?</summary>

`StandardError` and its subclasses. `ScriptError`, `SystemExit`, `SignalException` and
`NoMemoryError` descend from `Exception` alongside `StandardError`, so they are not caught.

</details>

<details><summary>You wrap a low-level failure in your own exception class. How does a caller reach the original?</summary>

Through `#cause`, which Ruby sets automatically when one exception is raised while another is
being handled. Nothing needs to be passed explicitly.

</details>

## Listings

1. `exceptions-ensure-and-cleanup-you-can-rely-on-1.rb` — the four clauses in order, `ensure`
   on every path, and the two ways `ensure` can rewrite the outcome.
2. `exceptions-ensure-and-cleanup-you-can-rely-on-2.rb` — the hierarchy, what a bare `rescue`
   does and does not catch, a namespaced error family, and `#cause`.
3. `exceptions-ensure-and-cleanup-you-can-rely-on-3.rb` — a bounded `retry` around a flaky
   resource, with open and close counts on the success, failure and exhausted paths.
