## Why this exists

A component holds `firstName` and `lastName`, and the screen needs the full
name. There is a state variable for it, and an effect that keeps it up to date.
Nobody looking at that code thinks it is wrong; it reads as careful. It is
wrong. The full name is not a fact the component owns, it is an answer to a
question about two facts it does own, and storing an answer creates a second
place where the truth lives — one that has to be maintained, one that can lag,
and one that can be edited into disagreement with the facts underneath.

## The idea

Storing a derived value is writing the total at the bottom of a receipt in pen.
The lines are the truth. The total is a summary of the lines, and once it is
inked in, every change to a line means remembering to cross it out and rewrite
it. Add the column up when somebody asks and there is only one place to be
wrong.

### Where the analogy breaks

For a person, rewriting a total is merely a chore, and a chore done carefully is
reliable. For a component it is not only a chore: the correction arrives one
render late. Setting the mirrored value from an effect means React 19 commits
the render where the inputs changed, *then* runs the effect, *then* renders
again. The first listing records every committed frame and reports
`frames where the shown name disagreed with the inputs: 3` for the mirrored
version and `0` for the computed one. Those are frames a reader can see.

The other half of the analogy misleads by making the recalculation sound
expensive. Adding a column of figures is work; recomputing a derived value
during render is almost always a string concatenation or a `filter`. Where it
genuinely is expensive there is `useMemo` — and `useMemo` is still not storage.

## How it works

The rule from the documentation is one sentence: "If you can calculate something
during render, you don't need an Effect." A component body runs on every render,
so a plain `const` is already reactive.

```jsx
function Form({ firstName, lastName }) {
  const fullName = `${firstName} ${lastName}`.trim();
  return <h1>{fullName}</h1>;
}
```

There is no update to forget and no frame in which the two disagree, because
there is only one value.

The same argument applies to anything filtered, sorted, summed or formatted from
props and state. Where the calculation is measurably slow, cache it rather than
store it:

```jsx
const visibleTodos = useMemo(() => getFilteredTodos(todos, filter), [todos, filter]);
```

The third listing models that cache — one value, one dependency array, compared
with `Object.is` — and prints `without a cache, filterTodos ran: 3` against
`with a cache, filterTodos ran: 2` over three renders where only the filter
changed. It also shows the cache defeated by a list rebuilt during render:
`with a cache and a rebuilt list, filterTodos ran: 3`.

`useMemo` is a cache and not a store. The reference says React "will not throw
away the cached value unless there is a specific reason to do that", then names
several, and adds that future features may add more. Anything whose loss would
be a bug does not belong there.

The awkward case is a stored value that must be *reset* when something changes —
a selection, when the list reloads. The second listing shows a stored item still
reading `Cheese` after the list dropped it, and two repairs: store the
identifier and resolve it during render, or, where the state must genuinely be
cleared, set it during render from the component that owns it, which React 19
handles by discarding that render's output and immediately rendering again.

## Common mistakes

**Mirroring props into state.** The state is initialised once, so later props
are ignored and the screen shows the first value forever. If the intent is to
reset on change, the documented tool is a `key` on the child.

**An effect that sets state from state.** Two renders per change, and the frame
between them is the one measured above.

**Storing the selected object rather than its identifier.** The listing prints
`stored item is still in the list: false`: a reference to a row that no longer
exists, with no error to notice.

**Reaching for `useMemo` first.** The reference is explicit that it is a
performance optimisation, and that code which does not work without it has a
different problem.

## Check yourself

<details><summary>Why is the effect version visibly worse and not merely wasteful?</summary>
Because the mirrored value is written after the first render commits. The listing
counts six frames instead of three, and three of them show a name that does not
match the inputs.
</details>

<details><summary>When is <code>useMemo</code> the right answer?</summary>
When the calculation is measurably slow and its inputs are stable. It caches;
it does not store. If losing the cached value would be a bug, the value belongs
in state or a ref.
</details>

<details><summary>A filtered list is needed by two siblings. Compute it in each, or lift it?</summary>
Compute it where the inputs already live and pass the result down. Lifting the
inputs is about ownership; duplicating the calculation is only about where the
line of code sits.
</details>

## Full listings

1. Every committed frame, mirrored in an effect against computed during render.
2. A stored selection that outlived its row, and two repairs.
3. The `useMemo` cache, and the rebuilt array that defeats it.
