## Why this exists

Most of a React application is a calculation: data in, description out. Some of
it is not. A chat room has to hold a socket open. A map widget written by
somebody else has to be told the zoom level. A title bar has to be set. These
things live outside React and have no idea a render happened, so something has
to reach out and keep them in step. That something is `useEffect`, and the
reason this lesson insists on the phrase *escape hatch* is that its convenience
makes it the first tool reached for and the wrong one most of the time.

## The idea

An effect is a thermostat, not an alarm clock. An alarm clock fires at a moment
and is done. A thermostat has a target and keeps the room in agreement with it;
change the target and it acts again, not because time passed but because the two
sides no longer match. An effect is written the same way: given this `roomId`, a
connection to that room should exist.

### Where the analogy breaks

A thermostat is always watching, on its own initiative. An effect never watches
anything. React 19 runs it at the end of a commit, compares the dependency array
with the previous one using `Object.is`, and does nothing unless a member
differs. A value that changes without appearing in that array is invisible to
it — the effect is not late, it simply never hears.

The analogy also has one room to keep warm, and React reserves the right to set
your effect up and tear it down twice in a row. Strict Mode does exactly that in
development, so setup must tolerate running again immediately after its own
cleanup.

## How it works

The documentation frames effects as the things "caused by rendering itself,
rather than by a particular event", and says they "run at the end of a commit
after the screen updates". Setup returns a cleanup function; React runs that
cleanup before the next setup and again when the component unmounts.

```jsx
useEffect(() => {
  const connection = createConnection(serverUrl, roomId);
  connection.connect();
  return () => connection.disconnect();
}, [roomId]);
```

The first listing plays that lifecycle out: a mount on `general`, a render where
an unrelated piece of state changed, a switch to `music`, and an unmount. The
middle render prints nothing at all, and the log ends `connections still open:
0`.

Cleanup is not tidiness, it is correctness. The second listing runs the same
component with and without one. Without: `no cleanup, connections open after
unmount: 2`, and `after three real visits: 3` — the leak is not an artefact of
the development double mount, it is what a reader produces by opening the screen
three times.

Dependencies are compared by identity — the reference says React "will compare
each dependency with its previous value using the `Object.is` comparison" — so
an object or a function built during render is a new value every time.

```jsx
const options = { serverUrl, roomId };   // a new object on every render
useEffect(() => { /* … */ }, [options]); // therefore runs after every render
useEffect(() => { /* … */ }, [serverUrl, roomId]); // runs when the room changes
```

The third listing runs three renders and
prints `object dependency, effect runs over three renders: 3` against
`string dependency, effect runs over three renders: 1`. Depend on the string the
effect reads, not on the object carrying it.

The harder discipline is not writing the effect at all. If the work is caused by
an interaction, it belongs in the handler; the documentation's rule for the
distinction is that in a handler "you know exactly what happened", whereas an
effect only knows the component rendered.

## Common mistakes

**Omitting the cleanup.** Measured above: connections accumulate, one per visit.
Subscriptions, timers, observers and animation frames all belong to this family.

**Depending on an options object.** The effect re-runs after every render and,
if it also sets state, renders again — the loop people describe as "useEffect
fires infinitely".

**Removing a dependency to stop that loop.** It silences the symptom and freezes
a stale value inside the effect. Change what the effect depends on instead: move
the object out, or depend on its members.

**Using an effect to respond to a click.** A `POST` triggered by an effect
watching a flag runs again whenever the component remounts, which is why the
same order is sometimes placed twice.

## Check yourself

<details><summary>Why does Strict Mode mount, unmount and mount again in development?</summary>
To make a missing cleanup fail immediately rather than after a reader navigates
away and back. The listing shows both counts: two open connections without a
cleanup, one with.
</details>

<details><summary>An effect runs after every render even though its dependency "did not change". Why?</summary>
The dependency is a new object or function each render, and the array is
compared with <code>Object.is</code>. Depend on the primitive values inside it.
</details>

<details><summary>Sending analytics when a page is shown: effect or handler?</summary>
An effect, because the cause is the page being displayed rather than an
interaction. Sending analytics for a purchase is the other case — that is caused
by the click, and belongs in the handler that received it.
</details>

## Full listings

1. The lifecycle: setup, an unchanged render, a changed dependency, unmount.
2. The same effect with and without a cleanup function.
3. An object dependency against the string the effect actually reads.
