## Why this exists

Most TypeScript 5.9 code carries far fewer annotations than a newcomer expects,
and the reason is not that the compiler is lenient. It is that the compiler
derives the type from what you already wrote, by rules you can learn and
predict. Until you know those rules, inference feels arbitrary: the same string
literal is a `"dark"` in one line and a `string` in the next, an array of two
values acquires a union element type nobody asked for, and a callback parameter
needs no annotation while an identical parameter one line above does. None of
this is guesswork, and treating it as guesswork leads to two bad habits —
annotating everything out of distrust, or annotating nothing and being surprised.

## The idea

Inference is a surveyor working from the fence you already built. The surveyor
does not imagine where the boundary ought to be; they measure what is there and
write it down. `const answer = 42` has a fence around exactly one value, so the
recorded boundary is the literal type `42`. `let counter = 42` has a gate in it —
the binding can be reassigned — so the boundary is drawn around `number`.

### Where the analogy breaks

A surveyor measures a fixed physical object, and the same fence measured twice
gives the same answer. The compiler's answer depends on where the expression
appears. The very same function literal is typed one way as an argument to
`Array.prototype.map`, where the parameter type is read from the array, and
refused outright when written in a position that supplies no context. The
handbook calls the first case contextual typing, and it means information flows
into an expression as well as out of it. A surveyor never has that problem, and a
reader who keeps the surveyor picture will look for the missing type inside the
expression when it is actually in the expression's surroundings.

## How it works

Three rules cover most of what you will meet. First, widening: an immutable
binding keeps the literal type, a mutable one widens to the primitive. Object
properties are mutable, so they widen too; `as const` marks the members
`readonly` and stops the widening.

```ts
const mode = "dark"; // "dark"
let theme = "dark"; // string
const config = { theme: "dark" }; // { theme: string }
const frozen = { theme: "dark" } as const; // { readonly theme: "dark" }
apply(theme);
// error TS2345: Argument of type 'string' is not assignable to parameter of type 'Theme'.
```

Second, best common type. When several expressions must share one type — array
elements, the return paths of a function — the compiler looks for a candidate
that covers the rest, and forms a union when there is none.

```ts
const mixed = [0, 1, null]; // (number | null)[]
mixed.reduce((a, n) => a + n, 0);
// error TS18047: 'n' is possibly 'null'.
```

Third, contextual typing. A function literal in a position whose type is already
known takes its parameter types from that position. In a position with no such
type, `noImplicitAny` refuses to invent one.

```ts
words.map((w) => w.length); // w: string, read from words
const lengthOf = (w) => w.length;
// error TS7006: Parameter 'w' implicitly has an 'any' type.
```

## Common mistakes

**Passing a mutable string where a literal union is required.** This is the
TS2345 above. The value is right and the type is not: `theme` was widened at its
declaration. Annotate the binding as `Theme`, or freeze the source with
`as const`.

**Assuming an array of literals infers a union of literals.** It does not;
`[0, 1, null]` infers `(number | null)[]`, and the `null` you thought was
incidental is now in every element's type. Under `--strict` that is the point:
running the same reduction in plain JavaScript yields `1`, silently treating
`null` as zero.

**Annotating a callback parameter because the editor showed `any` once.** If the
`any` came from a missing contextual type, adding an annotation hides the real
problem, which is usually that the surrounding value is itself untyped.

## Check yourself

<details><summary>Why does <code>const mode = "dark"</code> keep the literal type while <code>let theme = "dark"</code> does not?</summary>
A <code>const</code> binding can never hold a different value, so the literal
type stays accurate for the life of the binding. A <code>let</code> binding can
be reassigned, so the compiler widens to the primitive that the later
assignments would need.
</details>

<details><summary>What type does <code>[0, 1, null]</code> get, and why not <code>number[]</code>?</summary>
<code>(number | null)[]</code>. Best common type looks for a candidate that
covers all the others; <code>number</code> does not cover <code>null</code> under
<code>strictNullChecks</code>, so the compiler forms the union instead.
</details>

<details><summary>Why does one arrow function need a parameter annotation and another does not?</summary>
Because contextual typing supplies the parameter type only when the function
literal sits in a position whose type is already known. Assigned to a bare
<code>const</code> with no annotation, there is nothing to read, and
<code>noImplicitAny</code> reports TS7006.
</details>

## Full listings

1. Widening, `as const`, and the literal union that refuses a widened string.
2. Best common type over an array, and over two unrelated classes.
3. Contextual typing, the implicit `any` it does not supply, and return inference.
