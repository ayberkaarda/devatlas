## Why this exists

Waiting is most of what an application does, and a thread that waits is a thread doing
nothing while holding a megabyte-scale stack. The usual answers — a thread pool, a callback,
a future — all work and all cost the same thing: the code stops reading in the order it
happens. Kotlin 2.4 offers `suspend` functions, which read top to bottom like ordinary calls
and release their thread while they wait. Around them sits structured concurrency: every
coroutine belongs to a scope, a scope does not finish before its children, and cancelling a
scope cancels everything under it. That second half is what stops concurrency becoming a
collection of tasks nobody owns.

## The idea

A scope is a supervisor who cannot leave the building until every subcontractor they hired
has left. Work started inside a scope is accounted for by that scope: it waits for it, it
carries its failures, and if the supervisor is sent home everyone they hired is sent home
too.

### Where the analogy breaks

Three ways, and the third is the one that costs an afternoon.

A subcontractor occupies a room. A suspended coroutine occupies nothing — no thread, no
stack — which is why thousands can be outstanding at once and why "how many can I afford"
is the wrong question to bring here. It also means suspension and blocking are not the same
act: `delay` releases the thread, `Thread.sleep` keeps it, and a suspend function that calls
the second is a suspend function that lies.

Subcontractors also finish in some order you could watch. Which of two independent coroutines
prints first is not specified by anything, so it is not a fact to build on. What *is*
specified is worth naming precisely: `awaitAll` returns results in the order of its
arguments regardless of which finished first, and `coroutineScope` has not returned until
every child inside it has completed. Both listings print those two properties and neither
prints an interleaving.

And a supervisor can drag someone out of the building. A coroutine cannot be stopped from
outside. `cancel` sets a flag; the coroutine notices at its next suspension point, or at an
explicit `isActive` check, or never. The third listing holds a coroutine in a loop with
neither, cancels it, and shows the body running on to its end with the job already marked
cancelled.

## How it works

`runBlocking` bridges ordinary code into a coroutine; inside a scope, `launch` starts work
you do not need a result from and `async` starts work you do:

```kotlin
val deferred = listOf(4, 1, 3, 2).map { async { slowSquare(it) } }
println(deferred.awaitAll())   // [16, 1, 9, 4] — argument order, not finish order

coroutineScope {
    for (n in 1..4) launch { delay(10L * n); produced += n }
}
// every child has completed by the time this line runs
```

Cancellation travels down the tree and `finally` still runs, so cleanup is expressed where
it belongs. Cleanup that itself suspends needs `withContext(NonCancellable)`, because a
cancelled coroutine's suspension points throw immediately:

```kotlin
try {
    delay(10_000)
} catch (e: CancellationException) {
    throw e                                   // rethrow: the machinery relies on it
} finally {
    withContext(NonCancellable) { flush() }   // may suspend, will not be cancelled
}
```

`withTimeoutOrNull` is the same mechanism with a deadline: it returns `null` rather than
throwing when the block overruns.

## Common mistakes

**Calling a suspend function from ordinary code.** The compiler names the function and the
two places it may be called from:

```text
error: suspend function 'suspend fun delay(timeMillis: Long): Unit' can only be called from a coroutine or another suspend function.
```

**Swallowing `CancellationException`.** A bare `catch (e: Exception)` around a suspending
call catches the cancellation signal too, and the coroutine carries on inside a scope that
has already been told to stop. Rethrow it, or catch something narrower.

**Reaching for `GlobalScope`.** It compiles, with a warning that is easy to skim past:

```text
warning: this is a delicate API and its use requires care. Make sure you fully read and understand documentation of the declaration that is marked as a delicate API.
```

Work started there belongs to no scope, so nothing waits for it and nothing cancels it — the
whole guarantee is opted out of in one word.

**Assuming `cancel` stops a computation.** It does not. The third listing prints `its body
ran on past the cancellation: true` for a loop that neither suspends nor checks, beside
`the loop that checked isActive exited: true` for the same loop with one check added.

## Check yourself

<details><summary>Why is <code>awaitAll</code>'s order safe to record but the print order of two <code>launch</code> blocks is not?</summary>

Because `awaitAll` is defined to return results positionally, matching its arguments — the
ordering is a property of the function. Two independent coroutines have no ordering
relationship at all, so any order you observe is one run of one scheduler.

</details>

<details><summary>A coroutine is cancelled while inside a <code>finally</code> block that calls a suspend function. What happens?</summary>

The suspend call throws immediately, because the coroutine is already cancelled and every
suspension point in it now fails. That is what `withContext(NonCancellable)` is for: it
gives the cleanup a context whose job cannot be cancelled.

</details>

<details><summary>What does <code>coroutineScope</code> give you that starting the same coroutines somewhere else does not?</summary>

A join point and an owner. It does not return until its children have finished, a failure in
one child cancels the siblings, and cancelling the enclosing job cancels the lot. Without it
you have tasks with no one waiting for them and no one able to stop them.

</details>

## Listings

1. `coroutines-and-structured-concurrency-1.kt` — what `awaitAll` and `coroutineScope` guarantee.
2. `coroutines-and-structured-concurrency-2.kt` — timeouts, cancellation down the tree, cleanup.
3. `coroutines-and-structured-concurrency-3.kt` — cancellation is cooperative, shown with a gate.
