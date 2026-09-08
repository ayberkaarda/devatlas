## Why this exists

A function declares what it needs from its argument, and the compiler decides
whether a given value qualifies. If you arrive from Java or C#, you will expect
that decision to be made by lineage: a value qualifies because its class was
declared to implement the interface. TypeScript 5.9 decides it a different way,
and the difference shows up in the first hour. You will pass an object literal
to a parameter typed with an interface the literal has never heard of, and it
will be accepted. Then you will pass an almost identical literal and be told
about a property nobody asked for. Neither behaviour makes sense until you know
what a type is in this language: a description of a shape, checked once by the
compiler, and gone by the time the program runs.

## The idea

A type is a job description, not a family name. A nominal type system asks where
a value came from — the class was declared to implement `Pet`, and that
declaration is the credential. TypeScript reads the description instead. `Pet`
says "has a `name` of type `string`", and anything that can do that gets the
job: a class that never mentions `Pet`, an object literal written on the spot,
the return value of a function from a library you did not write.

### Where the analogy breaks

A job description implies an interview — somebody, at some point, checks the
candidate. TypeScript never checks at runtime. Types are erased before the
program executes: `Pet` appears nowhere in the emitted JavaScript, and there is
no moment at which a value is compared against it. Writing `value instanceof
Pet` is not a weak check, it is not a check at all. It does not compile:
`error TS2693: 'Pet' only refers to a type, but is being used as a value here.`

The analogy leaks a second time. A job description rarely objects to a candidate
who can do more than it asks, and structural assignability does not either —
with one exception. A freshly written object literal is checked for properties
the target does not declare. That rule exists to catch typos; it is not evidence
that the system is nominal underneath.

## How it works

An object type `S` is assignable to an object type `T` when every member `T`
requires is present in `S` with an assignable type. Nothing else is consulted:
not the class name, not the declaration site, not an `implements` clause. The
handbook calls this structural subtyping, and it is why the assignment below
needs no ceremony.

```ts
interface Pet {
  name: string;
}
class Dog {
  constructor(
    public name: string,
    public breed: string,
  ) {}
}
const pet: Pet = new Dog("Rex", "collie"); // accepted
```

The exception is freshness. An object literal written directly in an argument or
initialiser position carries the extra check; the same object reached through a
variable does not.

```ts
interface Options {
  width: number;
  colour?: string;
}
function area(o: Options): number {
  return o.width * o.width;
}
area({ width: 3, colur: "red" });
// error TS2561: Object literal may only specify known properties,
// but 'colur' does not exist in type 'Options'. Did you mean to write 'colour'?
const opts = { width: 3, colur: "red" };
area(opts); // accepted: the literal is no longer fresh
```

Because compatibility is decided on members, a type alias creates a name and
nothing more. `Metres` and `Seconds` below are both `number`, so they are the
same type, and swapping the arguments is silent. Adding a member no runtime
value carries — a brand — makes them structurally different at no runtime cost.

```ts
type Branded<T, B extends string> = T & { readonly __brand: B };
type BrandedMetres = Branded<number, "metres">;
type BrandedSeconds = Branded<number, "seconds">;
// error TS2345: Argument of type 'BrandedSeconds' is not assignable
// to parameter of type 'BrandedMetres'.
```

## Common mistakes

**Trusting an alias to keep two things apart.** With `type Metres = number` and
`type Seconds = number`, calling `speed(9.58, 100)` instead of
`speed(100, 9.58)` produces `0.0958` rather than `10.438413361169102`, and the
compiler says nothing, because the two parameters have the same type.

**Reaching for `instanceof` on an interface.** It fails with TS2693, quoted
above. Interfaces do not exist at runtime; only classes and other values do.

**Silencing the excess property check with an assertion.** Writing
`area({ width: 3, colur: "red" } as Options)` makes TS2561 go away and keeps the
typo. Fix the spelling, or widen the declared type if the extra property is real.

## Check yourself

<details><summary>Why does <code>Dog</code> satisfy <code>Pet</code> with no <code>implements</code> clause?</summary>
Because assignability is decided on members. <code>Pet</code> requires a
<code>name</code> of type <code>string</code>; <code>Dog</code> has one. The extra
<code>breed</code> member is irrelevant, and so is the fact that neither
declaration mentions the other.
</details>

<details><summary>The same object is rejected as a literal and accepted through a variable. Why?</summary>
The excess property check applies only to a fresh object literal in an argument
or initialiser position. Once assigned to a variable, the value is judged by the
ordinary structural rule, which only asks that the required members are present.
</details>

<details><summary>What does <code>type Metres = number</code> buy you?</summary>
A name for readers, and nothing for the checker. If you need the compiler to
refuse a seconds value where metres are expected, intersect a phantom member
onto the type so the two are structurally different.
</details>

## Full listings

1. Structural acceptance from a class and from an object literal.
2. The excess property check, and the same value passing through a variable.
3. Aliases are names; a brand is a type.
