## Why this exists

Everything the compiler knows, it knows about the code you wrote. A response
body, a message from another process, a row read out of local storage — none of
these were written by you, none were checked, and all of them arrive as `any` or
`unknown`. This is where most runtime failures in a TypeScript 5.9 codebase come
from, and the reason is almost always the same: somebody wrote `as User` because
it made the red squiggle disappear. The type then propagates through the
program, and the first symptom appears several files away, in code that was
correct given what it was told.

## The idea

Two people can move a bag through customs. One writes a declaration and hands it
over; the other opens the bag and looks. `as User` is the declaration — a claim
by the person carrying the bag, accepted on the spot. A validating function is
the inspection: it opens the value, checks each member, and either produces a
`User` or reports why it could not.

### Where the analogy breaks

Customs eventually opens some bags. TypeScript never opens any. The declaration
is not a claim that might be spot-checked later; it is the only thing that
happens, it happens at compile time, and it produces no record and no runtime
code. There is no second line of defence behind it. The analogy leaks in the
other direction too: an inspection that only looks at the top of the bag still
feels like an inspection, and a validator that checks `typeof value.id ===
"string"` and `Array.isArray(value.lines)` really has checked something — while
leaving every element of `lines` entirely unexamined.

## How it works

`JSON.parse` is declared to return `any`, so the annotation on the result is
accepted without comparison. The value that comes back is whatever the sender
sent.

```ts
const user = JSON.parse('{"id":7,"name":"ada"}') as User;
user.id.toUpperCase(); // TypeError: user.id.toUpperCase is not a function
user.roles.length; // TypeError: Cannot read properties of undefined (reading 'length')
```

The alternative is a function that takes `unknown`, narrows it member by member,
and returns a discriminated result. The union makes the failure path impossible
to ignore: there is no `value` to reach for until `ok` has been tested.

```ts
type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

function parseUser(text: string): ParseResult<User> {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return { ok: false, error: "not JSON" };
  }
  if (typeof raw !== "object" || raw === null) return { ok: false, error: "not an object" };
  const record = raw as Record<string, unknown>;
  if (typeof record.id !== "string") return { ok: false, error: "id must be a string" };
  // ...one check per member, then construct the value
}
```

Note the single `as` that remains: `raw as Record<string, unknown>` widens an
object to an index signature of `unknown` values. It asserts nothing about
contents, and every member is still checked individually afterwards.

Depth is the part people skip. A guard that stops at the first level lets a
wrongly typed element through, and the wrongness shows up as arithmetic.

```ts
// lines: [{"sku":"a","qty":"2"},{"sku":"b","qty":"3"}], qty declared number
const total = order.lines.reduce((sum, line) => sum + line.qty, 0);
// observed: total is the string "023"
```

## Common mistakes

**Using `as` to cross the boundary.** It is the fastest way to make the editor
quiet and it moves the failure to a place with no clue about its cause. Reserve
`as` for widening to `unknown` or `Record<string, unknown>` inside a validator.

**Validating the shell and not the contents.** The shallow guard above returns
`true` for the payload, and the sum of two numeric quantities is observed as the
string `"023"`. Recurse into arrays and nested objects, or accept that the type
is a hope.

**Letting `any` in through a helper.** A wrapper returning `unknown` instead of
`any` — the handbook's `safeParse` — costs one line and forces the check at every
call site.

## Check yourself

<details><summary>What exactly does <code>as User</code> do at runtime?</summary>
Nothing. It is erased along with every other type annotation. Its whole effect is
to stop the compiler from disagreeing with you at that line.
</details>

<details><summary>Why return a discriminated result instead of throwing?</summary>
Because the union makes the failure part of the type. The success branch is the
only place <code>value</code> exists, so a caller cannot reach the parsed data
without first testing <code>ok</code>.
</details>

<details><summary>The shallow guard returns <code>true</code>. What did it establish?</summary>
That the value is a non-null object with a string <code>id</code> and an array
under <code>lines</code>. It established nothing about the array's elements,
which is why summing their <code>qty</code> produced a string.
</details>

## Full listings

1. An assertion, and the two runtime failures it permits.
2. A validator returning a discriminated result, over five inputs.
3. A shallow guard, the wrong total it allows, and the deep guard that refuses.
