## Why this exists

Node.js 22 ships a test runner and an assertion library, so a project needs no dependency to have
tests. What it does not ship is protection from the one failure mode that asynchronous tests
invite: a test that reports success without having checked anything. A green suite is evidence only
if every assertion in it actually ran and every failure had somewhere to be reported. Both of those
are things you arrange, and both are easy to get wrong in ways that look exactly like passing.

## The idea

A test suite is an exam with an invigilator who collects papers at the bell. A test that hands in a
blank paper and leaves early is not marked wrong. It is marked as containing no errors.

### Where the analogy breaks

An invigilator would notice a blank paper. A runner cannot: an assertion that never ran leaves no
trace at all, so a test that checked nothing and a test that checked everything produce the same
green line. There is nothing to detect, only something absent.

The bell is also more forgiving than the analogy suggests. Node's runner does wait — if the test
function returns a promise it waits for it to settle, and if an assertion throws after the test ended
it fails the file rather than ignoring it. The failure is not the runner's patience running out. It is
the hand-off you never made: an assertion left inside a callback the runner was never told about.

## How it works

A test function may be synchronous or `async`. If it returns a promise, the runner awaits it, and a
rejected promise fails the test. That single rule is what the whole lesson rests on: a failing
assertion inside an `async` test is only a failure if the promise carrying it reaches the runner.

```js
test('totals add up', async () => {
  assert.equal(await total(), 5);   // the promise is returned; the runner waits
});
```

Assertions about failure have two forms and they are not interchangeable. `assert.throws` only sees
a synchronous throw, so it refuses an `async` call outright — a real diagnostic, and the helpful
case. `assert.rejects` is the asynchronous form and it returns a promise, which means an
un-awaited `assert.rejects` checks nothing and says nothing.

```js
await assert.rejects(() => loadUser(-1), { code: 'USER_NOT_FOUND' });
```

For an assertion that lives inside a callback, the fix is to make the test wait for it — wrap the
callback in a promise and await it, or use the promise-returning form of the API. Where waiting is
awkward, `t.plan(n)` declares how many assertions should run and fails the test when fewer did.

```js
test('the handler fired', (t) => {
  t.plan(1);
  emitter.on('done', () => t.assert.equal(status, 'ok'));
});
```

## Common mistakes

**A hand-rolled runner that calls the test and does not await it.** Listing 1 runs the same three
tests through two runners differing in one `await`; the first reports one failure and the second
two.

**`assert.rejects` without `await`.** It returns a promise that rejects when the assertion fails.
Nothing awaited it, so the test passes and the failure escapes as an unhandled rejection.

**An assertion inside a listener for an event that never fires.** Listing 3 runs exactly this test
under `node --test`: the run is green and nothing was checked. The same test with `t.plan(1)`
fails.

**Asserting on an error's message.** Messages are phrased by the runtime and change between
releases. Assert on `error.code`, or on the documented `actual` and `expected` fields of an
`AssertionError`.

## Check yourself

<details><summary>Why does a failing assertion inside an <code>async</code> test sometimes not fail the test?</summary>

Because the promise the assertion rejected was never handed to the runner. The runner awaits
what the test function returns; anything else is invisible to it.

</details>

<details><summary><code>assert.throws</code> around an <code>async</code> call — what happens?</summary>

It fails. The call returns a rejected promise rather than throwing, so the assertion reports a
missing exception. That is the good case: it tells you to use `assert.rejects`.

</details>

<details><summary>How do you catch a test that asserted nothing?</summary>

Declare the count with `t.plan(n)`. The runner compares it against the assertions that actually ran
and fails when fewer did.

</details>

## Listings

1. `testing-and-the-async-assertion-1.js` — two runners, one `await` apart, over the same tests.
2. `testing-and-the-async-assertion-2.js` — `assert.throws` against `assert.rejects`, awaited and
   not.
3. `testing-and-the-async-assertion-3.js` — the built-in runner judged by exit status, including the
   green run that checked nothing.
