## Why this exists

Fetching looks like the easiest thing in the lesson list and produces the
subtlest bugs in it. The naive version works on your machine, against your fast
local server, with one request at a time. It fails on a slow connection, where
two requests overlap and the older one answers last; it fails when the component
unmounts mid-flight; and it fails quietly on the screen, showing results for a
query nobody is asking any more. None of those failures raises anything.

## The idea

Fetching without cancellation is ordering at a busy counter without a ticket
number. You order a coffee, change your mind and order tea. Two drinks are being
made. Whichever appears in front of you, you take, because you have no way to
say "that one is not mine any more" — and the coffee, started first, may well
arrive second.

### Where the analogy breaks

At a counter the drink arrives and you may refuse it. A network response does
not arrive in front of anyone; it arrives inside a callback that writes to state
without asking. The refusal has to be built in advance, which is what the flag
in the cleanup function is.

The counter also has a queue with an order, and that is the thing a network
categorically does not have. The first listing settles the two responses by hand
in the order that produces the bug and prints
`without cleanup, the screen ends up showing: results for wo` — the answer to
the abandoned query, arriving after the answer to the current one.

Last, refusing the coffee does not stop it being made. `AbortController` does
stop the request, which the counter has no counterpart for; ignoring the
response and cancelling it are two different repairs, and the second is
strictly better when the transport supports it.

## How it works

The shape the React 19 documentation gives for fetching inside an effect is a
flag closed over by the cleanup function.

```jsx
useEffect(() => {
  let ignore = false;
  fetchResults(query, page).then((json) => {
    if (!ignore) {
      setResults(json);
    }
  });
  return () => { ignore = true; };
}, [query, page]);
```

React runs that cleanup before the next setup and on unmount, so a response that
belongs to a superseded render finds `ignore` already true. With the flag, the
first listing prints `with cleanup, the screen ends up showing: results for
wolf`. The third listing pairs the flag with an `AbortController`, so the
request itself is dropped rather than merely disregarded.

The second half of the problem is what the component stores. Loading, error and
data as three independent booleans describe eight combinations, and the second
listing counts three of them as states no screen can be in — a spinner beside an
error, an error beside results. It then walks a request that fails and is
retried, updating the flags one at a time, and reports
`frames showing a spinner and an error at once, or an error beside results: 2`.
One status value with four names has no such frames.

```js
{ status: "loading" }
{ status: "error", message }
{ status: "success", data }
```

React 19 also offers a route where the component does not manage any of this.
`use` reads a promise during render and suspends until it resolves, with a
`<Suspense>` boundary supplying the fallback and an error boundary catching a
rejection — the reference notes that `use` "cannot be called inside a try-catch
block" for that reason. The constraint to remember is that the promise must not
be created during the render that reads it: React 19 warns if it is, because a
new promise every render can never settle.

## Common mistakes

**No cleanup.** Measured above: the abandoned query's results win. This is the
same defect as a missing unsubscribe, and it is why the development double mount
exists.

**Three booleans.** Two contradictory frames in a four-frame sequence, above,
and the stale error is the one users report.

**Forgetting that an effect fetch runs again.** Any dependency that is a new
object each render refetches on every render; the fix is the dependency array,
not a guard flag.

**Creating the promise in render and handing it to `use`.** Each render creates
a different promise, so the component suspends indefinitely.

## Check yourself

<details><summary>Why does the older request win when there is no cleanup?</summary>
Because both callbacks write to the same state and the network imposes no
order. The listing resolves the newer response first and the older one second,
and the older one is written last.
</details>

<details><summary>What does <code>AbortController</code> add over the <code>ignore</code> flag?</summary>
The flag stops a stale response being used; the controller stops the request
being finished at all, freeing a connection and avoiding work on the server. Use
both: the flag still covers responses already in flight when the abort lands.
</details>

<details><summary>Where do the three impossible boolean combinations come from?</summary>
From modelling one thing as three. Two booleans give four combinations and three
give eight, while the screen has four states, so the extra combinations are
reachable and meaningless. A single status value cannot express them.
</details>

## Full listings

1. The race, settled by hand, with and without the cleanup flag.
2. Three booleans against one status value, counted over the same sequence.
3. The React 19 versions: an effect that aborts, and `use` with `<Suspense>`.
