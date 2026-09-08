## Why this exists

Somebody adds a `console.log` to a component and discovers it runs forty times a
second while a text field is being typed into. The reaction is usually to wrap
things in `memo` until the number goes down. That works about a third of the
time and hides the cause the other two thirds, because "why did this run" has
only a few possible answers and none of them is "React felt like it". Knowing
the three answers turns a performance mystery into a two-minute reading of the
tree.

## The idea

A render spreads like dominoes falling from wherever the row was pushed.
Something pushes one domino — a set function — and every domino downstream of it
goes over. Nothing upstream moves. `memo` is a gap left in the row: the fall
stops there, provided nothing else disturbs the pieces beyond it.

### Where the analogy breaks

A fallen domino stays fallen; that is the whole of the event. A render is a
calculation whose result may be discarded. React 19 "only changes the DOM nodes
if there's a difference between renders", so a component running again is not a
component repainting, and the log line you added counts calls, not work the
browser did. The first listing renders three components and reports
`DOM nodes the commit had to touch: 1`.

The second break is the reason the gap trick keeps failing. Context does not
travel along the row. React 19 re-renders every component that reads a context,
starting from the provider whose value differed, and the reference is explicit
that "skipping re-renders with `memo` does not prevent the children receiving
fresh context values." The gap is simply not in that path.

## How it works

After the initial render, a component runs again for one of three reasons.

**Its own state changed.** A set function was called with a value that
`Object.is` says differs from the current one.

**An ancestor rendered.** Rendering is recursive: React calls the component, then
the components it returned, down to the leaves. The documentation's list of
triggers is short — the initial render, or "the component's (or one of its
ancestors') state has been updated" — and the recursion is what turns the second
into a subtree. The first listing walks an eight-component tree and prints the
subtree for each starting point: state in `Nav` renders one component, state in
`Feed` renders four, state in `App` renders all eight.

**A context it reads received a different value.** The provider's previous and
next values are compared with `Object.is`, and every reader below it runs.

`memo` addresses only the second reason. It compares the incoming props
shallowly, member by member, with `Object.is`, and skips the call if they all
match.

```jsx
const Row = memo(function Row({ item, onSelect }) { /* … */ });
```

That comparison is defeated by anything built during the parent's render. The
second listing reports `inline props shallow-equal across two renders: false`
for an object and an arrow function written in place, and `true` once the same
values are hoisted or produced by `useMemo` and `useCallback` with unchanged
dependencies. It also prints the honest limit:
`memoised, props equal, own state changed: true`.

The third listing does the same for a provider. A value object rebuilt inline
re-renders both consumers, including the memoised one; the same value kept
stable re-renders none. React 19 lets the context itself be rendered as the
provider, and the value is the thing to stabilise:

```jsx
const value = useMemo(() => ({ theme, setTheme }), [theme]);
return <ThemeContext value={value}>{children}</ThemeContext>;
```

## Common mistakes

**Treating a render count as a cost.** Rendering a pure function that returns
the same description costs a function call and a comparison. The listing's
`components rendered in that update: 3` against
`DOM nodes the commit had to touch: 1` is the shape of most updates.

**Adding `memo` before finding the cause.** The reference is blunt: "You should
only rely on `memo` as a performance optimization. If your code doesn't work
without it, find the underlying problem and fix it first."

**Memoising a component and passing it an inline arrow.** Measured above as
`false`: the props are never equal, so the wrapper does nothing but add a
comparison.

**Building the context value in the provider's body.** Every render of the
provider hands every consumer a new object, and `memo` cannot intervene.

## Check yourself

<details><summary>A child with no props re-renders whenever its parent does. Is that a bug?</summary>
No. Rendering is recursive, so an ancestor rendering is one of the three
reasons. It becomes worth addressing only when the render is measurably
expensive, and then <code>memo</code> is the tool — after the measurement.
</details>

<details><summary>A memoised component re-renders on every keystroke elsewhere. Where do you look?</summary>
At the props it receives, for an object, array or function created during the
parent's render, and at any context it reads. Those are the two paths
<code>memo</code> does not close.
</details>

<details><summary>Why does keeping a provider's value stable matter more than memoising its children?</summary>
Because a changed context value re-renders every reader regardless of
<code>memo</code>. Stabilising the value removes the reason; memoising the
children cannot.
</details>

## Full listings

1. The subtree a state change renders, and the smaller set the commit touches.
2. `memo`'s shallow comparison, and what defeats it.
3. Context: a provider value rebuilt inline against one kept stable.
