## Why this exists

A thread that is waiting for a disk or a network is a thread doing nothing while holding
somewhere between a hundred kilobytes and a megabyte of stack. `async` and `await` let a method
give the thread back at the point it starts waiting and pick up where it left off when the wait
ends. The keyword is small and the transformation is not: the compiler rewrites the method into
a state machine, and "pick up where it left off" turns out to mean "resume somewhere", where
*somewhere* is decided by a rule most people never read. That rule — the captured context — is
behind the deadlock, the wrong-thread exception and the "it works in a console app but hangs in
the desktop build" that this lesson is about.

## The idea

`await` is a bookmark, not a pause button. The reader closes the book at a marked page, walks
away, and the library is free to give the chair to somebody else. When the book is ready again,
someone picks it up at the bookmark and reads on.

### Where the analogy breaks

The bookmark records more than the page. It also records **which chair** the reader wants back —
the `SynchronizationContext` that was current when the `await` executed. In a UI application
that chair is the one thread allowed to touch the interface, so the resumption is queued back to
it. In a console application there is no chair at all: the second listing prints
`console app starts with no context: True`, which is why console programs never reproduce the
deadlock they are meant to demonstrate.

And the reader does not always leave. If the awaited thing is already finished, `await` reads
straight on without closing the book — the language checks `IsCompleted` first, so an async
method runs synchronously up to its first genuinely incomplete `await`. The first listing shows
the method body's own log entry appearing *before* the line after the call.

The last leak is that the analogy suggests one reader. `Task.WhenAll` waits for several, and the
only ordering it promises is that the **results come back in argument order**. Which one finished
first is not part of the contract, is not reproducible, and is not printed by any listing here.

## How it works

An `async` method returns a `Task` immediately, at its first suspension. Everything after that is
a continuation the compiler registered. When the awaited task completes, the continuation is
scheduled — by default onto the captured `SynchronizationContext`, and onto the thread pool when
there is none:

```csharp
var gate = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var pending = Work(gate);        // body has already run up to its await
gate.SetResult();                // continuation is queued to the captured context
```

`ConfigureAwait(false)` declines the capture for that one `await`, so the continuation runs
wherever the runtime finds convenient. It is per-await, not per-method: a method that configures
its first `await` still captures at the second one unless that is configured too. Library code
configures every await, because a library cannot know whether its caller's context is a UI thread
it would be rude to queue work onto.

Failure has a shape of its own. An exception thrown inside an async method does not escape the
call — it is stored in the returned task, and the shape of the wait decides what you catch:

```csharp
await failing;                          // rethrows InvalidOperationException
failing.Wait();                         // throws AggregateException wrapping it
failing.GetAwaiter().GetResult();       // rethrows the original, unwrapped
```

`Task.WhenAll` collects every failure but `await` rethrows only one of them; the rest are reachable
through the task's own `Exception.InnerExceptions`. Cancellation is separate again: the task ends
`IsCanceled`, not `IsFaulted`, and `await` throws an `OperationCanceledException`.

## Common mistakes

**Calling an async method and not awaiting it.** The compiler warns, and the warning is precise
about the consequence:

```text
Program.cs(1,1): warning CS4014: Because this call is not awaited, execution of the current method continues before the call is completed. Consider applying the 'await' operator to the result of the call.
```

**Awaiting inside a method that is not `async`.** The compiler names the fix:

```text
Program.cs(6,12): error CS4032: The 'await' operator can only be used within an async method. Consider marking this method with the 'async' modifier and changing its return type to 'Task<int>'.
```

**Blocking on a task with `.Result` or `.Wait()`.** No diagnostic. Two things go wrong: the
exception arrives wrapped in an `AggregateException`, and on a thread with a
`SynchronizationContext` the continuation is queued to a thread that is blocked waiting for it.
That is the classic deadlock, and it does not reproduce in a console program.

**Depending on which of several tasks finished first.** No diagnostic, and a test that passes on
your machine. `WhenAll` guarantees argument order for the results and nothing about completion
order.

## Check yourself

<details><summary>An async method logs a line before its first <code>await</code>. Does that line run before or after the caller's next statement?</summary>

Before. The method body runs synchronously until it awaits something that has not completed. The
call only returns at that point, so the caller's next statement is later.

</details>

<details><summary>Why does the deadlock caused by <code>.Result</code> never appear in a console application?</summary>

There is no `SynchronizationContext` to capture, so the continuation is scheduled on the thread
pool rather than back onto the blocked thread. Install a single-threaded context — as a UI
framework does — and the same code hangs.

</details>

<details><summary><code>Task.WhenAll(a, b, c)</code> returns an array. What is guaranteed about it?</summary>

That element zero is `a`'s result, element one is `b`'s and element two is `c`'s. Nothing is
guaranteed about which task completed first, so a test that asserts completion order is asserting
an accident.

</details>

## Listings

1. `async-await-and-the-captured-context-1.cs` — synchronous start, completed awaits, `WhenAll` order.
2. `async-await-and-the-captured-context-2.cs` — a real context, the post it receives, and `ConfigureAwait(false)`.
3. `async-await-and-the-captured-context-3.cs` — faulted tasks, `AggregateException`, and cancellation.
