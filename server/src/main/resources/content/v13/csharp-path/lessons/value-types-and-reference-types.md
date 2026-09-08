## Why this exists

Two variables of the same C# type can behave in opposite ways when you assign one to the other,
and nothing at the assignment tells you which you are getting. `var b = a;` either duplicates
the data or hands out a second name for it, and the answer lives in the declaration of `a`'s
type, possibly in another file. C# 14 draws that line once: every type is either a **value
type** — `struct`, `enum`, and the built-in numerics — or a **reference type** — `class`,
`interface`, `delegate`, `record class`, arrays. A value type variable *holds* its data. A
reference type variable holds a reference to data that lives somewhere else. Assignment always
copies what the variable holds, which is why the rule is one sentence and the consequences fill
a lesson.

## The idea

A value type variable is a printed form. Handing someone a copy of the form gives them their
own sheet: they can scribble on it all afternoon and your sheet stays as it was. A reference
type variable is a note with a room number on it. Copy the note and you have two notes — but
one room, and whatever either of you does in there, both of you see.

### Where the analogy breaks

The note is not a thin thing you can ignore. Copying a room number is cheap and fixed in size;
copying a form costs as much as the form is big, and a `struct` with sixteen fields is copied
sixteen fields at a time on every assignment, every argument pass and every return. That is why
the guidance in the .NET documentation is to keep a `struct` small and immutable rather than to
prefer it by default.

The analogy also suggests the room is somewhere else and the note is here. C# does not promise
that. Where a value lives — stack, inside another object, in a register — is an implementation
matter the language deliberately does not specify; a `struct` field inside a `class` lives on
the heap along with the rest of the object. The distinction the language does guarantee is
about *copying*, not about location.

And a form can be turned into a room. Assigning a `struct` to `object` or to an interface
**boxes** it: the runtime allocates, copies the fields in, and from then on you are holding a
reference to a copy. The original and the box drift apart silently.

## How it works

Assignment and argument passing follow the same rule, because a parameter is just a variable
initialised from the argument:

```csharp
var a = new PointStruct { X = 1 };
var b = a;      // two independent sets of fields
b.X = 99;       // a.X is still 1

var c = new PointClass { X = 1 };
var d = c;      // two names, one object
d.X = 99;       // c.X is now 99
```

`ref` changes what the parameter is: an alias for the caller's variable rather than a copy of
it. Reassigning a reference parameter inside a method, on the other hand, changes only the
callee's own variable — the caller keeps pointing at the same object.

Storage inherits the rule. A `List<T>` of structs returns a copy from its indexer, so mutating
what you got back changes nothing; an array of structs returns a *variable*, so it can be
mutated in place. The two look identical at the call site:

```csharp
list[0].Hits++;   // does not compile: the indexer returns a value
arr[0].Hits++;    // compiles and works: the array element is a variable
```

Equality differs too. A `struct` inherits memberwise equality from `ValueType`, so two structs
with equal fields are `Equals`. A `class` inherits reference equality, so two separately
constructed objects with identical fields are not.

## Common mistakes

**Mutating the loop variable of a `foreach` over structs.** The compiler stops it outright:

```text
Program.cs(4,5): error CS1654: Cannot modify members of 'p' because it is a 'foreach iteration variable'
```

**Expecting `==` to work on a `struct` you wrote.** Memberwise `Equals` comes for free; the
operator does not:

```text
Program.cs(3,19): error CS0019: Operator '==' cannot be applied to operands of type 'PointStruct' and 'PointStruct'
```

**Mutating a struct that a property returned.** This one is the same mistake as the `List`
indexer, and the compiler names the reason precisely:

```text
Program.cs(2,1): error CS1612: Cannot modify the return value of 'Box.Size' because it is not a variable
```

**Calling a mutating method through an interface.** No diagnostic at all — the call boxes, the
box is mutated, and the original struct keeps its old value. That is the failure this lesson
exists to make visible.

## Check yourself

<details><summary>Why does mutating an element of <code>List&lt;SomeStruct&gt;</code> through the indexer not compile, while the same line on an array does?</summary>

`List<T>.this[int]` is a property, and a property returns a value. Mutating a returned value
would write to a temporary that is discarded, so the compiler refuses (CS1612). An array
element access is a variable, not a call, so it can be assigned to directly.

</details>

<details><summary>A method takes a class reference and assigns a new object to the parameter. Why does the caller see nothing?</summary>

The parameter is a copy of the reference. Reassigning it repoints the callee's own variable.
Mutating the object through it would have been visible; replacing the reference is not, unless
the parameter is declared `ref`.

</details>

<details><summary>What does boxing copy, and when does the copy stop tracking the original?</summary>

Boxing copies the struct's fields into a freshly allocated object at the moment of conversion.
From that instant there are two independent sets of fields, and neither assignment to the
original nor mutation of the box is visible to the other.

</details>

## Listings

1. `value-types-and-reference-types-1.cs` — assignment, argument passing, `ref`, and defaults.
2. `value-types-and-reference-types-2.cs` — structs inside a `List`, an array, and `readonly struct`.
3. `value-types-and-reference-types-3.cs` — boxing, default equality, and the mutation that hit the box.
