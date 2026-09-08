## Why this exists

Write a function that returns the first element of an array. Type the parameter
`any[]` and the caller loses everything: the result is `any`, and the next twenty
lines are unchecked. Type it `string[]` and it works for one array. What you
actually want is to say "whatever the caller put in, that is what comes out" —
a relationship between the argument type and the result type. That relationship
is what a type parameter expresses in TypeScript 5.9. Most people meet generics
as syntax to be tolerated when using a library; they are worth understanding as
the ordinary way to write a function that does not throw away what the caller
knew.

## The idea

A generic function is a shipping form with a blank in it. The form is printed
once and is the same for every shipment; the blank marked "contents" is filled in
per shipment, and the label that comes out the other end repeats exactly what was
written in the blank. `firstElement<T>(arr: T[]): T | undefined` is that form.
Fill the blank with `string` and the result is `string | undefined`; fill it with
`Date` and it is `Date | undefined`. The body of the function never changes.

### Where the analogy breaks

Three ways. A form's blank is filled in by hand every time; a type parameter is
usually inferred from the arguments and never written at the call site. A form
accepts whatever you scribble; a type parameter can be constrained with `extends`
so only some fills are legal, and the compiler rejects the rest before the
function runs. And a form has no opinion about consistency between fields — the
same blank appearing in two places on one form must be filled with one value,
which is precisely why a type parameter is useful and why `pairSame("a", 1)`
needs a type that covers both arguments.

## How it works

A type parameter is declared in angle brackets and then used like any other type
inside the signature. It relates positions to each other: here, the element type
of the argument to the element type of the result.

```ts
function firstElement<T>(arr: T[]): T | undefined {
  return arr.length > 0 ? arr[0] : undefined;
}
const first = firstElement(["ada", "grace"]); // string | undefined
```

`extends` constrains what the blank may be filled with, and one parameter may be
constrained by another. `K extends keyof T` is the standard way to say "a key
this object actually has", and `T[K]` is the type stored under it.

```ts
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}
getProperty({ name: "ada", age: 36 }, "email");
// error TS2345: Argument of type '"email"' is not assignable
// to parameter of type '"name" | "age"'.
```

Where the parameter sits matters. The handbook's rule is to push type parameters
down: use the parameter as the element type rather than constraining it to the
whole array, or the element type is lost.

```ts
function firstElementLoose<T extends any[]>(arr: T) {
  return arr[0]; // inferred as any
}
const wrong: number = firstElementLoose(["ada"]); // compiles; it is a string
```

## Common mistakes

**Constraining to `any[]` instead of parameterising the element.** The loose
version above compiles and `typeof wrong` reports `string` at runtime, from a
binding annotated `number`. The type parameter was in the wrong position, so it
related nothing.

**Constraining too loosely and relying on the body.** `longest<T extends
HasLength>(a: T, b: T)` refuses a number with `error TS2345: Argument of type
'number' is not assignable to parameter of type 'HasLength'`. Drop the constraint
and the same call compiles, compares two `undefined` lengths, and returns the
second argument.

**Adding a type parameter that appears once.** `function greet<S extends
string>(s: S)` relates nothing to anything; it is `s: string` with extra
ceremony. The handbook's rule of thumb is that a type parameter earns its place
by appearing in at least two positions.

## Check yourself

<details><summary>Why does <code>firstElement</code> return <code>string | undefined</code> without being told about strings?</summary>
The argument's type is <code>string[]</code>, so inference fills the blank with
<code>string</code>. The declared return type <code>T | undefined</code> is then
read back with that fill.
</details>

<details><summary>What does <code>K extends keyof T</code> buy over <code>key: string</code>?</summary>
It restricts the argument to keys the object actually has, so a misspelled key is
a compile error, and it lets the return type be <code>T[K]</code> — the type of
that specific property rather than a union of all of them.
</details>

<details><summary>Why is <code>&lt;T extends any[]&gt;(arr: T)</code> worse than <code>&lt;T&gt;(arr: T[])</code>?</summary>
The blank is filled with the array type rather than the element type, so
indexing it produces <code>any</code> and the caller's element type is discarded.
Put the parameter where the varying part is.
</details>

## Full listings

1. Keeping the caller's type, and the version that loses it.
2. Constraints, `keyof`, and the two diagnostics they produce.
3. Two independent parameters, one parameter in two positions, and `pluck`.
