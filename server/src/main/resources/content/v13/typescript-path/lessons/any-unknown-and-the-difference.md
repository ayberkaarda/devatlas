## Why this exists

Every typed program has edges where a value's type is not yet established: the
result of `JSON.parse`, a message from a worker, an argument handed to a logging
helper that must accept anything. TypeScript 5.9 offers two types for that
position, and choosing between them decides whether the rest of your program is
checked. `any` says "stop checking"; `unknown` says "this could be anything, so
prove what it is before you use it". They look interchangeable in a signature and
they are not, and the cost of the wrong one is not paid where you wrote it — it
is paid wherever the value ends up.

## The idea

Picture a parcel arriving at a delivery desk. `unknown` is the sealed parcel:
you can accept it, store it, hand it on, but you cannot use what is inside until
you open it and look. `any` is the clerk stamping "contents as described" without
opening anything, on the strength of whatever the sender wrote on the label. The
package moves either way. The difference is whether anybody ever looks inside.

### Where the analogy breaks

A false stamp on a parcel leaves a record; somebody can go back and find the
clerk who signed it. An `any` leaves nothing. It produces no diagnostic at the
point of the lie and none at the point of the failure, and it spreads: an `any`
that flows into an otherwise well-typed expression makes the result `any` too, so
the region of your program that is no longer checked is larger than the line you
wrote. The picture leaks in a second place. A parcel is a container with
something inside it, which invites you to think of `unknown` as a wrapper you
unwrap. It is not a wrapper. `unknown` is the type of every value — the top of
the assignability lattice — so narrowing it is not unwrapping, it is finding out
which part of that set you are standing in.

## How it works

`any` is assignable to and from everything, and every operation on it is
permitted. The declaration below compiles, and so does every use of the value it
returns, including the ones that fail.

```ts
function lengthOfAny(value: any): number {
  return value.length;
}
lengthOfAny(null); // compiles; TypeError at runtime:
// Cannot read properties of null (reading 'length')
```

`unknown` is assignable from everything and assignable to nothing but `unknown`
and `any`. No property access, no call, no arithmetic, until a narrowing check
tells the compiler which types remain.

```ts
function rejected(value: unknown): number {
  return value.length;
  // error TS18046: 'value' is of type 'unknown'.
}
const s: string = someUnknown;
// error TS2322: Type 'unknown' is not assignable to type 'string'.
```

The check that unlocks it is ordinary narrowing — `typeof`, `Array.isArray`, an
`in` test, a type predicate. Once it has run, the value has a real type and
ordinary rules apply.

```ts
function lengthOfUnknown(value: unknown): number {
  if (typeof value === "string") return value.length;
  if (Array.isArray(value)) return value.length;
  return -1;
}
```

`JSON.parse` is declared to return `any`, which is why untyped data usually
enters a codebase through it. Wrapping it in a function that returns `unknown` —
the handbook's `safeParse` — moves the obligation to check back to the caller,
where the caller knows what was expected.

## Common mistakes

**Annotating an `any` and believing the annotation.** `const id: string =
payload.id` compiles when `payload` is `any`, and `typeof id` then reports
`number` at runtime. The annotation was accepted, not verified, and every later
line trusts it.

**Reaching for `as` the moment `unknown` complains.** `(value as string).length`
removes TS18046 and restores exactly the behaviour of `any`, one expression at a
time. The assertion is a claim, not a check.

**Using `any` for "accepts anything" in a public signature.** A parameter typed
`unknown` accepts every argument just as `any` does, and unlike `any` it does not
disable checking inside the function body.

## Check yourself

<details><summary>Both accept any argument. What actually differs?</summary>
What you may do with the value afterwards. Every operation on an
<code>any</code> is permitted and unchecked; an <code>unknown</code> permits
nothing until a narrowing check establishes a more specific type.
</details>

<details><summary>Why is <code>any</code> described as spreading?</summary>
Because the result of an expression involving an <code>any</code> is itself
<code>any</code>. One untyped value at a boundary can leave a whole chain of
downstream code unchecked, and none of it reports a diagnostic.
</details>

<details><summary>What does wrapping <code>JSON.parse</code> in a function returning <code>unknown</code> achieve?</summary>
It converts a silent <code>any</code> into a compile-time obligation. Every
caller must narrow or validate before use, at the point where the expected shape
is actually known.
</details>

## Full listings

1. `any` compiles everything, including the call that throws.
2. `unknown` refuses, and what narrowing gives back.
3. The `any` from `JSON.parse`, the annotation that lies, and `unknown` as the
   honest boundary type.
