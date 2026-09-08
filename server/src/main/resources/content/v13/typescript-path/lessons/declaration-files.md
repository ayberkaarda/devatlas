## Why this exists

Sooner or later you will import a package that ships no types, or reference a
global that a bundler defines, or call into a script loaded by a tag on the page.
The code exists and runs; the compiler has never seen it and will not accept a
single call. A declaration file — a `.d.ts`, or an ambient declaration inside a
normal file — is how you describe code you did not write so that the rest of your
TypeScript 5.9 program can be checked against it. It is also the one place in the
language where you can be wrong about something and hear nothing at all, so it
deserves more care than its size suggests.

## The idea

A declaration is a wiring diagram supplied for an appliance you cannot open. The
diagram names the terminals, says which is live and which is earth, and lets an
electrician work confidently without dismantling anything. It carries no current.
Remove the diagram and the appliance still works; remove the appliance and the
diagram still reads perfectly well.

### Where the analogy breaks

A wiring diagram is normally produced by whoever built the appliance and can be
checked against it by opening the case. A declaration you write is checked
against nothing. The compiler treats it as ground truth, so an error in the
diagram becomes an error in every program that trusts it, silently. And a diagram
is inert documentation, whereas a declaration actively participates in type
checking: it decides what callers are allowed to do, and it can be merged into by
other declarations elsewhere in the program, which no diagram ever does.

## How it works

`declare` introduces a name that exists at runtime but has no definition here.
Nothing is emitted for it. The declaration is the whole of what the checker
knows, so members you omit do not exist as far as your program is concerned.

```ts
declare const process: {
  readonly version: string;
  readonly argv: string[];
};
process.version.charAt(0); // fine
process.platform;
// error TS2339: Property 'platform' does not exist on type
// '{ readonly version: string; readonly argv: string[]; }'.
```

Nothing verifies that the declared name exists. Declare a build-time constant
that the build does not actually define and the program compiles, then fails at
the first read.

```ts
declare const BUILD_ID: string;
BUILD_ID.length; // ReferenceError: BUILD_ID is not defined
```

Interfaces merge. Two declarations of the same interface name in the same scope
combine their members, which is what lets a `.d.ts` extend a type it does not
own. `declare global` reaches out of a module to do the same to a global type —
and the declaration alone changes nothing at runtime, so it must be paired with
code that supplies the behaviour.

```ts
declare global {
  interface Array<T> {
    firstOrNull(): T | null;
  }
}
Array.prototype.firstOrNull = function <T>(this: T[]): T | null {
  return this.length > 0 ? this[0] : null;
};
```

Merging adds; it does not override. Redeclaring an existing member with a
different type is refused: `error TS2717: Subsequent property declarations must
have the same type. Property 'host' must be of type 'string', but here has type
'number'.` A `type` alias does not merge at all — a second declaration of the same
alias name is `error TS2300: Duplicate identifier`.

## Common mistakes

**Declaring a global the build does not define.** The compile is clean and the
first use throws `ReferenceError: BUILD_ID is not defined`. Check that whatever
you declared is actually injected before you rely on it.

**Getting the type wrong and trusting it anyway.** A declaration saying a
function returns `string` when it returns a number gives you a binding whose
`typeof` is `number` and whose `.toUpperCase()` throws `stamp.toUpperCase is not
a function`. Nothing along the way disagrees with you.

**Adding a global augmentation without an implementation.** `declare global`
makes the call site compile; only the assignment to the prototype makes it work.
The two live in different files often enough that one ships without the other.

## Check yourself

<details><summary>What does <code>declare</code> emit?</summary>
Nothing. It contributes types only. The runtime binding must already exist,
supplied by the host, a bundler, another script or a package's own JavaScript.
</details>

<details><summary>Why is a hand-written declaration riskier than an inferred type?</summary>
Because no one compares it to the implementation. An inferred type is derived
from code the compiler read; a declaration is asserted, and an assertion that is
wrong is wrong everywhere it is used.
</details>

<details><summary>What is the difference between merging an interface and redeclaring a member?</summary>
Merging combines distinct members from several declarations of the same interface
name. Redeclaring an existing member with a different type is refused with
TS2717; merging is additive, never an override.
</details>

## Full listings

1. An ambient declaration of a value the runtime supplies, and the member it
   deliberately omits.
2. A declaration with no binding behind it, and one with the wrong type.
3. Global augmentation paired with an implementation, and interface merging.
