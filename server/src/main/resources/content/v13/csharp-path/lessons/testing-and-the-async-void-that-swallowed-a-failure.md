## Why this exists

A test is a function that either returns or throws, and a runner is a loop that calls those
functions and writes down which threw. Everything a framework adds on top — discovery,
attributes, parallelism, fixtures, output capture — is convenience around that one idea. Knowing
it matters because the moment a test body becomes asynchronous, the "throws" half stops working
in a way no attribute repairs. An exception from an `async` method is not thrown at the caller;
it is stored in the returned `Task`. If the runner never receives that task, the failure has
nowhere to go, and a green run means nothing. The listings here build the runner in thirty lines
so the hole is visible rather than framework-shaped.

## The idea

A test runner is a proofreader working through a stack of pages. Each page is read; a page with a
mistake on it is put on the "errors" pile; at the end the two piles are counted. The count is
trustworthy exactly because every page passed through one pair of hands.

### Where the analogy breaks

An `async void` test does not hand the proofreader a page. It hands over a note saying "the page
will arrive later" and the proofreader, seeing nothing wrong with a note, files it under
"correct". The documentation is explicit that the caller of a void-returning async method cannot
catch exceptions thrown from it, and the third listing measures the consequence: the runner
reports one pass and one failure while two tests actually failed.

The failure is not lost so much as **redirected**. An async void method reports itself to the
current `SynchronizationContext` — `OperationStarted` when it begins, and its exception delivered
there when it throws. A test framework installs such a context precisely so those failures have
somewhere to land; the third listing installs one and catches the failure the runner missed. With
no context installed, as in a plain console program, there is nothing to catch it at all and the
exception reaches the thread pool.

And the proofreader cannot check a page that keeps changing. A test that reads `DateTime.Now`, a
real database, or a random seed is asserting about the world rather than the code. The first
listing injects an `IClock` and formats with the culture-independent `"s"` specifier, so the
assertion holds on any machine on any day.

## How it works

A runner that supports async tests stores `Func<Task>` rather than `Action`, and awaits it:

```csharp
foreach (var (name, body) in _tests)
{
    try
    {
        await body();                      // the fault surfaces here
        Console.WriteLine($"pass  {name}");
    }
    catch (Exception ex)
    {
        Console.WriteLine($"FAIL  {name}: {ex.Message}");
    }
}
```

The assertion that expects an exception has to await inside itself, for the same reason:

```csharp
public static async Task ThrowsAsync<TException>(Func<Task> action)
    where TException : Exception
{
    try { await action(); }
    catch (TException) { return; }
    throw new AssertionException($"expected {typeof(TException).Name}, nothing was thrown");
}
```

The trap is that an `async` lambda is perfectly happy to become an `Action`. That conversion
produces an async void method, so `Assert.Throws<T>(async () => await ...)` compiles, runs, and
tells you nothing. The signature of the assertion helper is what prevents it: a helper that only
accepts `Func<Task>` cannot be called that way.

Determinism is the other half. Inject the clock, inject the random source, and assert on values
rather than on ordering the runtime does not promise.

## Common mistakes

**Making a test `async void` and expecting the runner to see it.** The compiler blocks the
obvious repair, which is a useful signal about what the method is:

```text
Program.cs(1,1): error CS4008: Cannot await 'void'
```

**Calling an async method inside a test and not awaiting it.**

```text
Program.cs(1,1): warning CS4014: Because this call is not awaited, execution of the current method continues before the call is completed. Consider applying the 'await' operator to the result of the call.
```

**Passing an async lambda to an assertion that takes `Action`.** No diagnostic at all — the
lambda becomes async void and the assertion decides before the operation has finished. In the
second listing that produces a failure whose message is wrong; in a different helper it produces
a pass.

**Asserting on `DateTime.Now`, or on the enumeration order of a `Dictionary`.** No diagnostic,
and a test that fails once a year or once per upgrade.

## Check yourself

<details><summary>A test method is declared <code>async void</code> and its assertion fails. What does the runner report?</summary>

A pass. The method returned at its first suspension, so there was nothing for the runner to
await and nothing to catch. The exception goes to the current `SynchronizationContext`, or to the
thread pool when there is none.

</details>

<details><summary>Why must <code>ThrowsAsync</code> take <code>Func&lt;Task&gt;</code> rather than <code>Action</code>?</summary>

Because it has to await the operation to see the fault. An `Action` overload would silently
accept an async lambda, turn it into async void, and evaluate its verdict before the operation
finished.

</details>

<details><summary>What makes a test that reads <code>DateTime.Now</code> worse than one that reads an injected clock?</summary>

It asserts about the machine as well as the code, so it can fail for reasons that have nothing to
do with the change under test — a date boundary, a time zone, a regional format. An injected
clock makes the input part of the arrangement.

</details>

## Listings

1. `testing-and-the-async-void-that-swallowed-a-failure-1.cs` — the runner, assertions, and an injected clock.
2. `testing-and-the-async-void-that-swallowed-a-failure-2.cs` — async tests the runner awaits, and the assertion that does not.
3. `testing-and-the-async-void-that-swallowed-a-failure-3.cs` — the async void failure, and where it goes instead.
