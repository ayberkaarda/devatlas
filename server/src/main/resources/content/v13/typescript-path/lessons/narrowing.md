## Why this exists

A union type is honest about uncertainty: `string | string[] | null` says the
value is one of three things and does not say which. That is useful at the
boundary and useless in the body, where you need to call `join` or `toUpperCase`
on something definite. Narrowing is how TypeScript 5.9 gets from one to the
other. It reads the checks you already write — an `if`, a `switch`, an early
return — and tracks, statement by statement, which members of the union are
still possible. Learn what it can follow and you write ordinary JavaScript and
get precise types for free. Learn it badly and you either add assertions
everywhere or trust a narrowing the compiler never actually performed.

## The idea

The compiler keeps a running dossier on each value in scope, and every check you
write crosses possibilities off it. At the top of a function `strs` has three
entries. After `if (strs === null) return`, one entry is struck out for the rest
of the block. After `typeof strs === "object"`, the string entry goes. What
remains in the dossier is the type at that point in the program.

### Where the analogy breaks

A dossier is a record of facts, and facts stay true. What the compiler holds is
a claim about a program point, and it can be wrong in both directions. It is
conservative where JavaScript is strange: `typeof null` is `"object"`, so an
`typeof x === "object"` test does not remove `null`, and the dossier still lists
it. And it is optimistic where it cannot see: a narrowing established on an
object property is not discarded by an intervening function call, even a call
that assigns `null` to that very property. A real dossier gets updated when the
facts change. This one is updated only by what the compiler can follow inside the
current function.

## How it works

The handbook lists the checks that narrow: `typeof`, truthiness, equality, `in`,
`instanceof`, assignment, user-defined type predicates and assertion functions.
Control flow analysis threads them together, so an early return narrows the rest
of the function body.

```ts
function describe(strs: string | string[] | null): string {
  if (typeof strs === "object") {
    return strs.join(", ");
    // error TS18047: 'strs' is possibly 'null'.
  }
  return strs;
}
```

The fix is to test for `null` first, which removes it from the union before the
`typeof` check runs. For object unions, a shared member with literal types
discriminates them, and `in` narrows by property presence.

```ts
type Shape = { kind: "circle"; radius: number } | { kind: "square"; side: number };
function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
  }
}
type Outcome = { data: string } | { error: string };
const text = "data" in outcome ? outcome.data : outcome.error;
```

A type predicate lets you name a check and reuse it. The signature
`value is User` tells the compiler what a `true` return means. Nothing verifies
that the body actually establishes it.

```ts
function isUser(value: unknown): value is User {
  return typeof value === "object" && value !== null; // says nothing about `name`
}
```

## Common mistakes

**Testing `typeof x === "object"` to mean "not null".** It does not, and the
compiler will tell you so with TS18047. Ignore it and `null.join` throws
`Cannot read properties of null (reading 'join')`.

**Writing a predicate that checks less than it promises.** `isUser` above
accepts `{ nickname: "ada" }`. Inside the guarded block, `candidate.name` is
typed `string`, prints `undefined`, and `candidate.name.toUpperCase()` throws.
The compiler reported nothing, because you told it not to.

**Assuming a call invalidates a narrowing.** After `if (box.value !== null)`, a
call to a function that sets `box.value = null` leaves the narrowing in place;
`box.value.toUpperCase()` then compiles and throws. Copy the property into a
local `const` before the call if you need the guarantee to hold.

## Check yourself

<details><summary>Why does <code>typeof strs === "object"</code> leave <code>null</code> in the type?</summary>
Because <code>typeof null</code> evaluates to <code>"object"</code> in
JavaScript, and the compiler models the operator exactly rather than
idealising it. Test for <code>null</code> explicitly first.
</details>

<details><summary>What makes a union discriminated?</summary>
A member present in every branch whose type is a distinct literal in each —
here <code>kind</code>. Testing it in a <code>switch</code> or <code>if</code>
selects exactly one branch of the union.
</details>

<details><summary>A type predicate compiles. What has been verified?</summary>
That the function returns <code>boolean</code>, and nothing else. The
relationship between the body's checks and the asserted type is your
responsibility; a predicate that under-checks narrows to a type the value does
not have.
</details>

## Full listings

1. `typeof` narrowing and the `null` that survives it.
2. Discriminated unions, `in`, and `instanceof`.
3. Two holes: a predicate that promises too much, and a narrowing that outlives
   the assignment that broke it.
