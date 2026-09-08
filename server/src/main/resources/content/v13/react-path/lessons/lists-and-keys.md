## Why this exists

A list renders. You add an item at the front. The rows shift down as expected,
and the half-typed reply that was sitting in the third row is now sitting in the
fourth — attached to a different person. Or a checkbox is ticked on the wrong
line, or a row animates in that should not have. No error is reported, because
nothing failed. React 19 simply matched the rows of the new list to the rows of
the old one using the only information you gave it, and what you gave it was
their positions.

## The idea

A key is a coat-check ticket. You hand over a coat, you get a ticket, and the
ticket is what gets your coat back — no matter how the rail was rearranged while
you were at dinner. Numbering coats by their position on the rail works
perfectly until somebody hangs a new one at the left-hand end, at which point
every number describes a different coat.

### Where the analogy breaks

The cloakroom issues the ticket. React does not: you supply the key, and if you
supply nothing React falls back to the position, which is exactly the numbering
the analogy warns about. The documentation says so plainly — the index "is what
React will use if you don't specify a `key` at all."

The ticket also never reaches the coat. `key` is not passed through to your
component: "Note that your components won't receive `key` as a prop. It's only
used as a hint by React itself." If the component needs the identifier, pass it
a second time under another name.

```jsx
<Row key={person.id} rowId={person.id} person={person} />
```

And a ticket is unique across the whole cloakroom, where a key only has to be
unique among one parent's children. Two separate lists may use the same keys
without any interference at all.

## How it works

When a list renders again, React 19 pairs each item in the new output with the
item in the previous output that carries the same key, reuses what it finds and
builds what it does not. The documentation states the purpose directly: "Keys
tell React which array item each component corresponds to, so that it can match
them up later."

```jsx
{people.map((person) => (
  <Row key={person.id} person={person} />
))}
```

The first listing runs both pairings over the same edit — a name inserted at the
front of a two-item list. With identifiers it reports
`id keys, reused instances handed different data: 0`. With indexes it reports
`2`, and then names them: key `0` held `"Ada Lovelace"` and now holds
`"Linus Pauling"`.

Reused instances are the point, because React keeps a component's state for as
long as the same component is rendered at the same position, and the key is what
decides which position an item now occupies. State the data does not carry —
a draft in an input, a scroll offset, an open menu — travels with the key. The
second listing types a draft into Grace Hopper's row, inserts a row above, and
prints `index keys: draft stayed with Grace Hopper: false`.

Two rules follow, and the documentation gives both. Keys must be unique among
siblings. And keys must not change: "do not generate keys on the fly, e.g. with
`key={Math.random()}`. This will cause keys to never match up between renders,
leading to all your components and DOM being recreated every time." The third
listing measures that as `instances reused: 0 of 3`.

## Common mistakes

**Reaching for the index because the data has no identifier.** The fix is to
give the data an identifier when it is created — the documentation suggests an
incrementing counter or `crypto.randomUUID()` — not to key on the position and
hope the list never moves.

**Generating a key during render.** Every render produces new keys, so nothing
matches, every DOM node is rebuilt and every uncontrolled input is emptied.
Measured above: nothing is reused.

**Repeating a key among siblings.** The third listing renders two rows with the
key `person` and reports `distinct keys: 1`, so one of the rows has no slot of
its own.

## Check yourself

<details><summary>The list is append-only and never reordered. Is an index key safe?</summary>
For that list, today, it behaves. The cost is that the safety is a property of
the data's history rather than of the code, and nothing in the file records it —
the first sort, filter or insertion turns it into the bug above. A stable
identifier costs one field.
</details>

<details><summary>Why does a half-typed input move to another row?</summary>
The text lives in the DOM node React reused, not in the data. The key decided
which row that node now belongs to, and an index key pointed it at a different
person's row.
</details>

<details><summary>The component needs the item's id. Can it read <code>props.key</code>?</summary>
No — <code>key</code> is consumed by React and is not forwarded. Pass it again
under another name, as in <code>&lt;Row key={id} rowId={id} /&gt;</code>.
</details>

## Full listings

1. Matching by key: identifiers against indexes, over the same insertion.
2. What travels with the key — a draft that ends up on the wrong row.
3. Keys generated during render, and keys repeated among siblings.
