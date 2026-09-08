## Why this exists

`strict` is a single line in a configuration file and it is the difference
between a type checker that catches real bugs and one that mostly renames your
JavaScript. It is not one setting: in TypeScript 5.9 it turns on nine separate
checks, and each refuses a different class of program. Knowing which is which
matters twice — when you are deciding whether to enable it on an existing
codebase and need to explain the errors, and when you assume `strict` covers
something it does not. Two of the most valuable checks in the compiler are not
part of it.

## The idea

Think of the flags as smoke detectors, one per room. Turning on `strict` installs
a set of them in one go: one in the room where `null` lives, one where implicit
`any` lives, one where uninitialised class properties live. Rooms without a
detector are not safer; nobody has been listening there. `noUncheckedIndexedAccess`
and `exactOptionalPropertyTypes` are two such rooms — real checks, off by
default, and not switched on by `strict`.

### Where the analogy breaks

Smoke detectors are independent: adding one in the kitchen does not change what
the one in the hall hears. These flags are not independent. `strictNullChecks`
changes the types the standard library reports — with it off, `undefined` is
removed from the types it appears in — so enabling it changes what every other
check sees, and the count of new errors is not the sum of the individual flags.
The analogy also invites the idea that a room is either covered or not. A flag is
a property of a whole compilation, not of a file: one loose module can hand an
`any` to a strict one, and the strict module's detectors never sound.

## How it works

The nine checks `strict` enables are `noImplicitAny`, `strictNullChecks`,
`strictFunctionTypes`, `strictBindCallApply`, `strictPropertyInitialization`,
`noImplicitThis`, `useUnknownInCatchVariables`, `alwaysStrict` and
`strictBuiltinIteratorReturn`. Three of them account for most of what you will
see on a first run.

```ts
const nope: string = undefined;
// error TS2322: Type 'undefined' is not assignable to type 'string'.  [strictNullChecks]
function double(x) { return x * 2; }
// error TS7006: Parameter 'x' implicitly has an 'any' type.            [noImplicitAny]
class Session { token: string; }
// error TS2564: Property 'token' has no initializer and is not definitely
// assigned in the constructor.                       [strictPropertyInitialization]
```

`useUnknownInCatchVariables` is the one people meet last and argue with. A
`throw` can carry any value, so the caught binding is `unknown`, and reading
`error.message` from it is a diagnostic rather than a habit.

```ts
catch (error) {
  return error.message;
  // error TS18046: 'error' is of type 'unknown'.
}
```

The two flags outside `strict` are worth enabling deliberately. Under `--strict`
alone, `names[9]` on a `string[]` has type `string`, and its observed runtime
value is `undefined`. Adding `--noUncheckedIndexedAccess` reports it.

```ts
const missing = names[9];
missing.toUpperCase();
// with --strict alone: compiles, then TypeError: Cannot read properties of undefined
// with --noUncheckedIndexedAccess: error TS18048: 'missing' is possibly 'undefined'.
```

`exactOptionalPropertyTypes` closes the other one: with it, `{ theme: undefined }`
is no longer assignable to `{ theme?: string }`, and the difference between
"absent" and "present and undefined" becomes visible.

## Common mistakes

**Reading `strict` as one check.** It is nine, and the errors on a first run are
mostly `TS2322`, `TS7006` and `TS2564` in that order. Fixing them is separable
work; enabling the flags one at a time is a legitimate migration.

**Silencing `TS2564` with a definite assignment assertion.** `token!: string`
compiles and asserts nothing. Constructing the object and calling a method that
reads `this.token` throws `Cannot read properties of undefined (reading
'length')`. Assign in the constructor instead.

**Assuming array and record access is checked.** It is not, under `strict`.
Every index expression is a possible `undefined` that the type does not mention,
which is precisely the bug `noUncheckedIndexedAccess` exists for.

## Check yourself

<details><summary>Which flags does <code>strict</code> not turn on?</summary>
Among others, <code>noUncheckedIndexedAccess</code> and
<code>exactOptionalPropertyTypes</code>. Both are documented in the compiler
option reference and both must be enabled separately.
</details>

<details><summary>Why is a caught error typed <code>unknown</code>?</summary>
Because <code>throw</code> accepts any value, not only an <code>Error</code>.
<code>useUnknownInCatchVariables</code> makes that fact visible, so an
<code>instanceof Error</code> test comes before <code>.message</code>.
</details>

<details><summary>What does <code>token!: string</code> promise?</summary>
Nothing checkable. It tells the compiler to stop asking, so the property is typed
<code>string</code> while holding <code>undefined</code> until something assigns
it — and the failure surfaces at the first read.
</details>

## Full listings

1. `strictNullChecks`, and the index access it does not cover.
2. `useUnknownInCatchVariables` and `noImplicitAny`, over three thrown values.
3. `strictPropertyInitialization`, the assertion that silences it, and the
   constructor that fixes it.
