## Why this exists

A discriminated union is a closed set of cases, and the code that consumes it
usually has one branch per case. The set grows. Somebody adds a fourth shape, a
new event kind, another payment method — and the `switch` that handled three of
them keeps compiling, keeps running, and quietly falls through to a default that
returns zero. That is the failure this lesson is about: not a wrong branch, a
missing one. TypeScript 5.9 can turn it into a build failure at the place where
the branch is missing, and the mechanism costs three lines. It is the highest
return on effort in the language, and it is opt-in, which is why so much code
does not have it.

## The idea

The `never` assignment is a dead man's switch wired into the build. While every
case is handled, control flow analysis reduces the value in the default branch to
`never`, the assignment is legal, and the switch stays quiet. Add a member to the
union without adding a branch, and the value reaching the default is no longer
`never`. The assignment fails, and the build stops at the exact `switch` that has
fallen behind.

### Where the analogy breaks

A dead man's switch fires because a human stopped responding, and it fires at the
moment of failure. This one fires at compile time, over the union as declared,
and it can only see what the type system was told. A value that entered the
program through an assertion — `JSON.parse(body) as Shape` — was never checked
against that union, so a payload with a fourth `kind` reaches the default branch
at runtime with the build entirely green. The switch protects you against your
own edits to a type, not against the world disagreeing with it.

## How it works

`never` is the empty type: it is assignable to every type, and no type is
assignable to it except `never` itself. So an assignment to a `never`-typed
binding succeeds exactly when the compiler has proved the value cannot occur.

```ts
type Shape = Circle | Square | Rect;
function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
    case "rect":
      return shape.width * shape.height;
    default: {
      const exhaustive: never = shape; // legal: nothing reaches here
      return exhaustive;
    }
  }
}
```

Remove the `rect` branch, or add a `Triangle` member to `Shape`, and the same
line reports the fault and names the case you forgot.

```ts
const exhaustive: never = shape;
// error TS2322: Type 'Triangle' is not assignable to type 'never'.
```

A helper reads better once you have more than one such switch, and it also gives
you a runtime failure for the values the type system never saw.

```ts
function assertNever(value: never): never {
  throw new Error("unhandled shape: " + JSON.stringify(value));
}
// default: return assertNever(shape);
```

This is the pairing worth remembering: the `never` parameter fails the build when
your own code falls behind the type, and the `throw` fails loudly at runtime when
data falls outside it. Neither replaces the other.

## Common mistakes

**Writing a default branch that returns a plausible value.** `default: return 0`
handles the missing case in the sense that nothing crashes. Adding `Triangle` to
the union and calling `area({ kind: "triangle", base: 3, height: 4 })` then
returns `0` — an answer, silently wrong, with no diagnostic anywhere.

**Suppressing the `never` error instead of adding the branch.** The build goes
green and the value is still wrong; the observed output for that triangle is
still `0`. The diagnostic was the whole benefit.

**Believing the check covers external data.** A payload asserted into the union
reaches `assertNever` and throws `unhandled shape: {"kind":"triangle",...}`. That
is the correct behaviour, and it is a runtime failure, not a compile-time one.
Validate at the boundary if you want it caught earlier.

## Check yourself

<details><summary>Why is the assignment to <code>never</code> legal when every case is handled?</summary>
Because control flow analysis has removed every union member by the time the
default branch is reached, leaving <code>never</code>, and <code>never</code> is
assignable to <code>never</code>. Any remaining member is not.
</details>

<details><summary>Which change breaks the build: adding a union member, or adding a <code>case</code>?</summary>
Adding a union member. The new member survives into the default branch and can no
longer be assigned to <code>never</code>. Adding the matching <code>case</code>
is the repair.
</details>

<details><summary>Why keep <code>assertNever</code>'s throw if the compiler already checked?</summary>
The compiler checked the type as declared. Values asserted or cast into that type
were never compared with it, so a fourth kind can still arrive at runtime; the
throw makes that arrival visible instead of silent.
</details>

## Full listings

1. A complete switch, with the `never` assignment that stays quiet.
2. The same switch after a union member is added: the diagnostic, and the wrong
   answer that appears if you silence it.
3. `assertNever`, and the payload that reaches it because it was asserted rather
   than validated.
