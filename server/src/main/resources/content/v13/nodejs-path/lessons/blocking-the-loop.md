## Why this exists

A Node.js 22 process handling a thousand connections and a Node.js 22 process handling one are
running the same single thread. Everything the runtime offers — concurrency, responsiveness,
throughput — rests on each callback finishing quickly and handing the thread back. A function
that computes for half a second is not slow for its own caller only; for that half second nothing
else in the process runs. No request is served, no timer fires, no health check is answered. This is
the failure that does not show up in a unit test, because a unit test has nothing else to do.

## The idea

Picture a single-lane roundabout. Traffic from every direction keeps flowing as long as nobody
stops inside it. One stalled lorry does not delay one road — it stops all of them at once.

### Where the analogy breaks

A roundabout serves several entries at the same time. This one has a single lane and a single
vehicle in it, which is closer to the truth: your JavaScript is not interleaved, it is serialised.

And a stalled lorry can be towed. A synchronous function cannot be pre-empted. There is no
timeout, no scheduler that will take the thread away, and no signal that interrupts it — the loop
resumes when the function returns and not one instruction earlier. That is why the cure is never
"make it faster"; it is to move the work somewhere the loop is not.

The analogy also suggests the blockage is visible. It is not. A blocked loop produces no error and
no warning; it produces latency in something else, measured by someone else.

## How it works

Two facts decide everything. First, a callback scheduled before a synchronous computation cannot
run until that computation returns — listing 1 prints that as a boolean rather than as a duration,
because the duration is a property of the machine and the ordering is a property of the runtime.
Second, work that can be handed to the thread pool or to another thread does not have this
problem at all.

Much of the standard library already has both forms. `crypto.pbkdf2Sync` runs on the calling
thread; `crypto.pbkdf2` hands the same computation to the libuv thread pool and calls you back.
The answers are identical, which is exactly why the synchronous one gets chosen by accident.

```js
crypto.pbkdf2(password, salt, iterations, length, 'sha256', (err, key) => { /* loop stayed free */ });
```

Where no asynchronous form exists, the work belongs on a worker thread. A worker has its own
loop and its own heap; `workerData` and messages are structured clones, so the two threads share
nothing and the worker cannot corrupt the main thread's state.

```js
const worker = new Worker(source, { eval: true, workerData: job });
worker.once('message', (result) => { /* main loop kept running throughout */ });
```

When the work is genuinely yours and genuinely synchronous, splitting it into chunks scheduled
with `setImmediate` gives the loop a turn between them. It does not make the work faster; it makes
the process responsive while the work happens.

To notice the problem in a running service, `perf_hooks.monitorEventLoopDelay` records how late
the loop's own timer fires. Its numbers belong on a dashboard, not in a lesson's expected output.

## Common mistakes

**Reaching for the `Sync` variant because it reads better.** `readFileSync`, `pbkdf2Sync` and
`execSync` are for start-up and for scripts. In a request path they stop the server.

**`JSON.parse` on a large payload without a size limit.** Parsing is synchronous and its cost is
the client's choice unless you cap the body.

**A regular expression with catastrophic backtracking.** The loop is blocked inside the regex
engine, which is unusually hard to see in a profile because no function of yours is on the stack.

**Assuming a worker is free.** Starting one costs a new isolate and a new loop, and every message
is a copy. Workers pay off for chunks of work, not per request.

## Check yourself

<details><summary>Why does listing 1 print booleans instead of milliseconds?</summary>

How long the work takes is a property of the machine. That a callback scheduled beforehand ran
afterwards is a property of the runtime, and it is the same everywhere.

</details>

<details><summary><code>pbkdf2</code> and <code>pbkdf2Sync</code> give the same key. Why prefer the callback?</summary>

The asynchronous form runs on the thread pool, so the event loop keeps turning while the
derivation happens. The synchronous one holds the only thread that matters.

</details>

<details><summary>Chunking with <code>setImmediate</code> — what does it actually buy?</summary>

Nothing in total run time. It returns the thread to the loop between chunks, so timers, I/O
callbacks and other requests are served while the work proceeds.

</details>

## Listings

1. `blocking-the-loop-1.js` — the same work in one run and in chunks, judged by ordering.
2. `blocking-the-loop-2.js` — the same derivation on the calling thread and on the thread pool.
3. `blocking-the-loop-3.js` — moving the work to a worker thread, and what crosses between them.
