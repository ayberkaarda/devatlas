## Why this exists

You add a name to an array in state, the array grows, and the screen does not
change. Nothing threw. A console log proves the array now has three items. This
is the first React bug almost everyone writes, and it does not respond to
staring at it, because the code is doing exactly what it says: it changed an
array. What it did not do is give React 19 a reason to render anything.

## The idea

React watches the box, not what is inside it. Calling a set function is handing
React a box and saying "this is the state now". React compares the box you just
handed over with the one it is holding. Open the box, rearrange the contents,
and hand back the same box, and there is nothing to compare — it is the same
box, so React puts it down and carries on.

### Where the analogy breaks

React is not watching at all. Nothing observes your object, and nothing notices
late; the only moment a comparison happens is inside the set function, and it is
`Object.is` against the current value. If you never call the setter, the change
is invisible for the lifetime of the program.

The second leak is that the "box" is not a container you can point at. It is
JavaScript reference identity. Two objects with byte-identical contents are two
different boxes, which is why `setPosition({ ...position })` triggers a render
even though the screen will look the same — and why a mutation followed by
`setPosition(position)` triggers none even though everything has changed.

## How it works

`useState` returns the current value for this render and a function that asks
for the next one. The React 19 reference states the rule that produces the bug:
"If the new value you provide is identical to the current `state`, as determined
by an `Object.is` comparison, React will skip re-rendering the component and its
children."

So the fix is not to touch the value you have. Build a new one.

```js
setPosition({ ...position, x: 5 });   // a new object: React renders
setItems([...items, next]);           // a new array: React renders
setItems(items.filter((i) => i.id !== id));
```

The first listing measures this. A mutation followed by a set of the same
reference produces `renders caused by the mutation: 0`; replacing produces
`renders caused by the replacement: 1`.

There is a second, subtler consequence. The documentation calls state a
snapshot: "a state variable's value never changes within a render". Your event
handler closed over the number this render was given, so reading it three times
gives the same number three times.

```js
setNumber(number + 1);
setNumber(number + 1);
setNumber(number + 1);   // number was 0 in all three: next state is 1
```

An updater function is handed the pending state instead of the render's
snapshot, so updaters compose. The second listing applies both queues and prints
`next state from plain set calls: 1` and `next state from three updaters: 3`.

Finally, a spread copies one level. The third listing spreads an object, writes
through the nested member, and prints
`nested objects are the same object: true` alongside
`the value we meant to leave alone now reads: Nice`. Copy every level on the
path you are changing; leave the rest shared.

## Common mistakes

**Reaching for the mutating array method.** `push`, `pop`, `splice`, `sort` and
`reverse` change the array and hand back a length or the same array. Their
non-mutating counterparts — spread, `filter`, `map`, and the `toSorted` and
`toReversed` that ECMAScript 2023 added — hand back a new one.

**Three set calls expecting three increments.** The listing shows the queue is
`[1,1,1]`, because each entry was computed from the same snapshot. Pass
`n => n + 1` when the next value depends on the previous one.

**Spreading the outer object and calling it a copy.** The nested object is
shared, so the previous state is edited too, and any comparison against it is
now comparing a value with itself.

## Check yourself

<details><summary>The array in state grew but nothing re-rendered. What happened?</summary>
The array was mutated and the same reference handed to the set function.
<code>Object.is</code> reported no change, so React 19 skipped the render. Pass
a new array instead.
</details>

<details><summary>Why does <code>setCount(count + 1)</code> three times add one, not three?</summary>
<code>count</code> is fixed for the render that created the handler, so all
three calls queue the same value. An updater function receives the pending state
and therefore composes.
</details>

<details><summary>When is <code>setPosition({ ...position })</code> — a copy with no change — useful?</summary>
Almost never, and it is worth knowing why it still renders: the reference
differs, so the bail-out does not apply. If you find yourself writing it to
force a render, the value that actually changed is somewhere you are mutating.
</details>

## Full listings

1. The `Object.is` bail-out, measured in renders.
2. The update queue: three plain set calls against three updaters.
3. One level of copying, and the nested object that was never copied.
