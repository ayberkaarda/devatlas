## Why this exists

Two components need to agree about something. An accordion where only one panel
may be open; a filter box and the list it filters; a total in a header and the
cart that produced it. Each component keeping its own copy is the obvious first
move and it cannot work, because two copies of one fact can disagree. The repair
is to move the fact to the nearest component that contains both readers and pass
it down. The interesting question is not how to do that — it is a small
refactor — but where to stop, because the same move applied once too often
produces the codebase where every value lives at the root.

## The idea

State ownership is a shared diary. Two people keeping separate diaries will
disagree about when the meeting is; one diary in the room they both use settles
it. Move the diary to head office because another branch might one day need it,
and every branch now telephones head office to ask what day it is.

### Where the analogy breaks

Consulting a diary costs something wherever it lives, and that is what makes the
"move it upstairs" decision feel balanced. Reading lifted state costs nothing at
the point of reading. The cost is entirely on the write side: whoever owns the
value decides how much of the tree runs again when it changes. The third listing
puts numbers on it — a draft string owned by `SearchBox` renders one component
per keystroke; the same string owned by `App` renders all nine.

The analogy's second failure is that anyone may walk into the room and read the
diary. React state reaches only the components you hand it to, so lifting always
arrives with prop passing attached, and lifting too far shows up as a value
threaded through six components that do not use it.

## How it works

The documentation names the principle: "for *each* piece of state, there is a
*specific* component that holds that piece of information." Find the lowest
component that contains every reader, put the value there, pass it down, and
pass a callback back up.

```jsx
function Accordion() {
  const [openPanel, setOpenPanel] = useState("about");
  return (
    <>
      <Panel isActive={openPanel === "about"} onShow={() => setOpenPanel("about")} />
      <Panel isActive={openPanel === "etymology"} onShow={() => setOpenPanel("etymology")} />
    </>
  );
}
```

The first listing runs the accordion both ways over the same click and reports
`duplicated: at most one panel open: false` against `lifted: … true`. The rule
the duplicated version breaks is not aesthetic: a panel that owns its own flag
has no way to learn that another panel opened.

That move changes what the child is. A component whose important information is
its own state is uncontrolled — the second listing prints
`uncontrolled: parent can close it: false`, because there is nothing for the
parent to call. Driven by a prop it is controlled, and the parent can close it.
The documentation states the trade directly: uncontrolled components "are easier
to use within their parents because they require less configuration. But they're
less flexible when you want to coordinate them together." The listing counts the
configuration: one prop against three.

Where to stop is the same question read backwards. The third listing computes
the lowest component containing each value's readers: a draft string read only
by the search box belongs in the search box; a submitted query read by the box,
the results and the pagination belongs in `App`. Lifting past that point buys
nothing and costs the subtree.

When the chain of intermediate components gets long, the documented first
answers are not a store. "Just because you need to pass some props several
levels deep doesn't mean you should put that information into context": try
passing props, and try extracting a component and passing JSX as `children`, so
there are fewer layers between the owner and the reader.

```jsx
<Layout posts={posts} />                      {/* Layout does not use posts */}
<Layout><Posts posts={posts} /></Layout>      {/* one fewer layer to thread */}
```

Context comes after those, and `useReducer` is for state whose *updates* have
outgrown a handful of setters — a different problem from where the state lives.

## Common mistakes

**Two copies initialised from the same source.** They agree until the first
edit. The listing's `two copies of one fact agree: false` is that moment.

**Lifting to the root by default.** Measured above as nine components rendered
per keystroke instead of one, and a prop threaded through components that ignore
it.

**Passing a setter down instead of an intention.** `onChange={setOpenPanel}`
hands a child the ability to write the parent's state to anything. A named
callback keeps the decision with the owner.

## Check yourself

<details><summary>Where does a value belong when exactly one component reads it?</summary>
In that component. Lifting is a response to two readers disagreeing, not a
default posture.
</details>

<details><summary>Why can two sibling panels not coordinate on their own?</summary>
Neither can reach the other's state, and neither is told when the other changes.
Coordination requires something that contains both, which is the parent.
</details>

<details><summary>Six components pass a prop through and only the last uses it. Context?</summary>
Consider extracting a component and passing JSX as <code>children</code> first,
so the intermediate layers disappear rather than being bypassed. Context is the
answer when neither passing props nor restructuring works.
</details>

## Full listings

1. Duplicated state against one owner, over the same click.
2. Uncontrolled and controlled, and the configuration each costs.
3. Where a value belongs, and what each choice renders.
