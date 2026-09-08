## Why this exists

You have a screen, and the screen changes. Every toolkit before this one asked
you to find the element that is now wrong and correct it: set the text, add the
class, remove the row. React 19 asks for something else. You write a function
that takes data and returns a description of what the screen should look like
for that data. React calls the function, compares the description with the one
it has, and works out which parts of the document have to change.

That inversion only pays if the function can be called again at any moment
without side effects. React decides when to call it, how often, and whether to
keep the result. Most of what goes wrong in a React codebase in the first month
is a component that quietly assumed it would be called once.

## The idea

A component is a recipe card, and rendering is reading it aloud. The card names
what it needs — flour, water, salt — and reading it produces a description of a
loaf. Hand the same card the same ingredients and you get the same description,
every time. Reading the card twice costs you nothing, because reading is not
baking.

### Where the analogy breaks

A recipe is written to be *performed*: its steps change the kitchen, and after
the last one there is bread where there was none. A component's return value is
performed by nobody. It is a value handed back to React, which decides what to
do with it and may decide to do nothing at all. The React 19 reference for
`createElement` is explicit that building an element "does not render the
component or create any DOM elements"; it is "a description — an instruction for
React to later render the component."

The analogy leaks a second time, and this is the leak worth guarding. A cook
reads a recipe once. React 19 may call your component more than once for the
same data: Strict Mode calls it twice in development precisely so that a
component which changes something outside itself gives a different answer the
second time and gets caught.

## How it works

Props are the information you pass to a JSX tag. Inside the component they
arrive as one object, which is why the parameter list is nearly always a
destructuring pattern, and why a default value is just a JavaScript default.

```jsx
function Greeting({ name, punctuation = "!" }) {
  return <h1>Hello, {name}{punctuation}</h1>;
}
```

The default applies when the prop is missing or when `undefined` is passed
explicitly. `null` and `0` are values, so they win over the default.

Props are read-only. The React 19 documentation puts it as "props are read-only
snapshots in time: every render receives a new version of props," and the
`createElement` reference adds that React freezes an element and its props
shallowly in development to enforce it. A child that wants different props asks
its parent to pass different ones.

What comes back is an ordinary object — a `type`, the `props`, a `key`, a `ref`
— so anything can walk it, which is how the first listing renders a tree to a
string with no browser in sight.

The rule that makes all of this safe is purity. React 19 assumes every component
is a pure function: it returns the same output for the same input, and it does
not change anything that existed before it was called. Values created *during*
the render are fair game — the documentation calls that local mutation and calls
it "your component's little secret."

```js
const shown = [...guests, guest]; // created here: fine
guestList.push(guest);            // existed before: not fine
```

## Common mistakes

**Changing a value that outlived the call.** The second listing pushes onto a
module-level array during render. Called twice, as Strict Mode calls it, it
prints `Alice, Bob` and then `Alice, Bob, Bob`, and reports
`impure stable across two calls: false`. The second answer is the one on screen.

**Assigning to a prop.** What that does depends on the mode the code is running
in, and the second listing writes to a frozen props object twice to show both.
It prints `sloppy mode: assignment ignored, name still reads Taylor` and then
`strict mode: threw TypeError`. The second line is the one that describes your
application: a React component lives in an ES module, modules are always strict,
and the write throws where you can see it. The first line applies to a plain
script — including the listing itself, which is why the strict block has to ask
for strict mode explicitly.

**Assuming one call per update.** Counting renders by incrementing a variable
outside the component gives a number that depends on the build. Nothing you can
observe should depend on how many times React chose to call you.

## Check yourself

<details><summary>Why can React call a component twice without changing what the reader sees?</summary>
Because a call only produces a description. The element object is inert until
React commits it, so a second call produces a second description and no second
effect — unless the component changed something outside itself, which is exactly
what the doubled call is there to reveal.
</details>

<details><summary>Where is <code>size = 100</code> in <code>function Avatar({ size = 100 })</code> applied?</summary>
When the prop is absent or explicitly <code>undefined</code>. Passing
<code>null</code> or <code>0</code> does not fall back to the default, because
both are values the caller chose.
</details>

<details><summary>A component sorts the array it received in props before mapping over it. What breaks?</summary>
<code>Array.prototype.sort</code> sorts in place, so the parent's array is
reordered by a child's render. Copy first — <code>[...items].sort(…)</code> —
and the mutation is local to the call.
</details>

## Full listings

1. Props in, a description out — and the description inspected as plain data.
2. A pure component and an impure one, each called twice.
3. The React 19 version, with `ref` arriving as an ordinary prop.
