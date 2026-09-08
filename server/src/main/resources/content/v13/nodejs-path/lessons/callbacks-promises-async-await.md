## Why this exists

Three notations for deferred work exist side by side in Node.js 22, and a working program usually
contains all three: a callback from a library written in 2013, a promise from something newer, and
`await` in the code you are writing today. They are not three competing styles to pick between.
The callback is the primitive the runtime actually delivers; a promise is a wrapper around one; and
`async`/`await` is syntax over promises. Reading them as one mechanism in three notations makes
the conversions mechanical, and it makes clear why the error handling is different in each — which
is the part that bites, because a `try`/`catch` that looks right can be catching nothing at all.

## The idea

Think of a cloakroom. You hand over a coat and get something back that stands for it. With a
callback you have said "shout my name when it is ready"; with a promise you hold a numbered
ticket you can pocket, hand to a colleague, or present twice; with `await` you are standing at the
counter holding the ticket, and the shop carries on serving the queue behind you.

### Where the analogy breaks

A dropped ticket costs you a coat and nothing else. A dropped rejected promise is louder than
that: Node.js 22 emits `unhandledRejection` and, with no listener installed, raises it as an uncaught
exception and ends the process. Losing the ticket is a crash, not a shrug.

A cloakroom ticket is also redeemed once. A promise settles once but can be read any number of
times, and every `.then` on an already-settled promise still fires. It is a record of an outcome, not
a claim on a thing.

And you are not really standing still while you `await`. The function is suspended, the thread goes
back to the loop, and everything else in the process keeps running — which is exactly why an
`await` inside a request handler does not stop the server serving other requests.

## How it works

The error-first callback convention is that the first argument is an error or `null`, and the value
comes second. `util.promisify` turns any function that follows it into one returning a promise, and
`await` unwraps that promise, turning a rejection back into a thrown exception at the `await`.

```js
const read = util.promisify(fs.readFile);
try {
  const text = await read(file, 'utf8');   // rejection becomes a throw here
} catch (error) {
  // error.code is stable; error.message contains a path
}
```

An `async` function always returns a promise, even when it never awaits and even when it throws.
`await` on a non-promise value resolves it on the microtask queue, so `await null` still yields to
the loop.

Whether two operations run one after another or together is decided by where the call is, not by
where the `await` is. Calling first and awaiting later runs them together.

```js
const a = load(1);                     // both started
const b = load(2);
const [x, y] = await Promise.all([a, b]);
```

`Promise.all` rejects at the first rejection and its results array is ordered by input position
regardless of which settled first. `Promise.allSettled` waits for all of them and reports each
outcome instead.

## Common mistakes

**Wrapping a callback API in `try`/`catch`.** The `try` block ends when the function returns, long
before the callback runs, so a throw inside the callback escapes to `uncaughtException`. Listing 2
prints both halves of that.

**`await` in a loop when the operations are independent.** Each iteration waits for the previous
one. `Promise.all` over a `map` starts them together; use the loop deliberately when you want the
sequencing, not by accident.

**Mixing a callback and a promise in one function.** A function that both calls back and returns a
promise will report a failure twice, or report success once and failure once. Convert at the
boundary and keep one notation inside.

## Check yourself

<details><summary>Why does an <code>async</code> function that only returns a number still give you a promise?</summary>

The `async` keyword changes the function's return type unconditionally: the body's return value is
wrapped, and a throw becomes a rejection.

</details>

<details><summary>Two operations must both finish. Where do you put the <code>await</code>?</summary>

After both calls, on a `Promise.all` of the two promises. Awaiting each call in turn makes the
second wait for the first for no reason.

</details>

<details><summary>What happens to a rejected promise nothing ever handles?</summary>

Node.js 22 emits `unhandledRejection`; with no listener, the rejection is raised as an uncaught
exception and the process exits non-zero.

</details>

## Listings

1. `callbacks-promises-async-await-1.js` — one operation in three notations, with the same result.
2. `callbacks-promises-async-await-2.js` — where an error goes in each, and the throw no
   `try`/`catch` can reach.
3. `callbacks-promises-async-await-3.js` — sequential against concurrent, and what `Promise.all`
   guarantees about order.
