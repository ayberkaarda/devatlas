## Why this exists

Node.js 22 runs your JavaScript on one thread. Every request handler, every timer callback and
every promise continuation in a process takes its turn on that same thread, and only one of them
is running at any moment. People meet this fact as a warning — "Node is single-threaded, so it
cannot scale" — and then meet a Node server holding thousands of connections and conclude that
the warning was wrong. Neither reading helps. What is actually true is narrower and more useful:
your code never runs concurrently with itself, so you never need a lock, and the waiting is done
somewhere else. Knowing which orderings the runtime promises, and which it does not, is the
difference between code that behaves and code that behaves on your laptop.

## The idea

Picture a lone assistant behind a shop counter with a row of labelled baskets of slips beside her.
She serves whoever is in front of her to the end — she never abandons a customer half-served.
When the counter is clear she works the baskets in a fixed round, always in the same order, taking
every slip in a basket before moving to the next. Slow errands are not hers: she hands them to
couriers who work outside the shop and drop a slip in a basket when they return.

### Where the analogy breaks

The baskets are not priorities. A slip in the timers basket is not more important than one in the
check basket; it is only earlier in the round. Nothing gets promoted for being urgent, and there is
no manager who can interrupt a customer who will not stop talking — a function that does not
return simply owns the counter until it does.

The next-tick queue is not one of the baskets either. It is drained after every single interaction,
before the assistant looks at any basket at all, which is why `process.nextTick` is documented in
Node.js 22 as legacy in favour of `queueMicrotask`: its callbacks jump ahead of every promise
continuation in a CommonJS module, and that is rarely what anyone wanted.

And the couriers are real threads. `fs`, `crypto` and `zlib` hand work to a pool of them, so "Node
is single-threaded" is a statement about your JavaScript and not about the process.

## How it works

Synchronous code runs to completion. Then the next-tick queue is drained, then the promise
microtask queue, and only then does the loop advance to its phases: timers, then I/O callbacks,
then the check phase where `setImmediate` callbacks live.

```js
process.nextTick(() => console.log('B'));
Promise.resolve().then(() => console.log('C'));
setTimeout(() => console.log('D'), 0);
console.log('A');            // A, then B, then C, then D
```

That ordering holds in a CommonJS module. In an ES module the top level is already inside the
microtask queue, so `queueMicrotask` callbacks run before `process.nextTick` ones — one more
reason not to reach for `nextTick`.

Some orderings are simply not specified. `setTimeout(fn, 0)` against `setImmediate(fn)` scheduled
from a module's top level can come out either way; scheduled from inside an I/O callback,
`setImmediate` always wins, because the check phase follows the poll phase in the same lap.

```js
fs.readFile(file, () => {
  setTimeout(() => console.log('second'), 0);
  setImmediate(() => console.log('first'));
});
```

## Common mistakes

**Treating `setTimeout(fn, 0)` as "run this now".** The callback cannot run until the counter is
clear. If the current function takes a second, the timer fires a second late; nothing is broken and
nothing will warn you.

**Assuming two timers with the same delay resolve in a fixed order.** They may, on your machine,
today. Write the dependency down explicitly instead of encoding it in delays.

**Reaching for `process.nextTick` to "run something first".** It runs before every pending promise
continuation, so a `nextTick` callback that throws or loops starves the rest of the program.
Listing 1 shows the queues in order.

## Check yourself

<details><summary>Why can a promise continuation never interrupt a synchronous loop?</summary>

Microtasks are drained after the current call stack unwinds. A loop is still on the stack, so there
is no point at which the runtime could run a continuation.

</details>

<details><summary>Why does listing 2 refuse to print the top-level order of setTimeout against setImmediate?</summary>

That order is not specified and varies between runs, so recording it would test the machine
rather than the runtime.

</details>

<details><summary>If your JavaScript is single-threaded, what does the thread pool do?</summary>

File, DNS, compression and some crypto operations run on pool threads. Their completions are
delivered back to your one thread as callbacks.

</details>

## Listings

1. `one-thread-and-the-event-loop-1.js` — the guaranteed order of synchronous code, next tick,
   microtask and timer.
2. `one-thread-and-the-event-loop-2.js` — the ordering that is specified only inside an I/O
   callback.
3. `one-thread-and-the-event-loop-3.js` — nothing interleaves with a synchronous section.
