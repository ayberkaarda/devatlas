## Why this exists

Most of the confusion in a first Java program comes from one misunderstanding: what a
variable of a class type actually contains. A method "changed my object", or refused to.
Two strings that print identically compare as different. An `Integer` comparison works for
100 and fails for 1000. These are not four bugs. They are one fact seen from four angles,
and the fact is written into the language specification rather than left to folklore.

## The idea

A reference is a coat-check ticket. The cloakroom holds the coat; you hold a slip of paper
that identifies it. Copy the slip and hand it to a friend and there is still one coat, now
with two ways to reach it. Your friend can put something in its pocket and you will find it
there. If your friend throws away their slip and writes a different number on a fresh one,
your coat is untouched — they changed their paper, not the garment.

### Where the analogy breaks

Three ways, and each one matters.

A ticket eventually puts the coat in your hands. A Java reference never does: there are only
operations you send through it. Second, a cloakroom keeps a coat until somebody claims it,
while the heap keeps an object only as long as some reference can still reach it. Third, and
worst if you carry it too far: the analogy does not describe primitives at all. An `int`
variable holds the number itself, not a ticket to it, and mixing the two pictures is
precisely where boxing surprises come from.

## How it works

The Java Language Specification for Java SE 21 states it directly in §4.3.1: "The reference
values (often just references) are pointers to these objects, and a special null reference,
which refers to no object." A variable of a class type holds one of those values. Assignment
copies what is in the variable, which for a reference type means copying the reference, not
the object.

Argument passing is assignment. A parameter is a fresh variable initialised from the
argument, so a method can change what its parameter's object contains and cannot change
which object the caller's variable names.

```java
static void rename(Box box) { box.label = "renamed"; }    // the caller sees this
static void replace(Box box) { box = new Box("other"); }  // the caller sees nothing
```

`==` on reference types compares references. Two objects with identical fields are still two
objects. `equals` is an ordinary method, so a class decides what equality means for it; a
class that decides nothing inherits identity from `Object`, and its `equals` behaves exactly
like `==`.

Boxing is where this bites hardest, because the specification pins half of it and leaves the
other half open. JLS §5.1.7 requires that boxing an `int` in the range -128 to 127 always
yields values for which `a == b` holds. Outside that range nothing is required, and the run
recorded in listing 2 produced two distinct objects.

```java
Integer a = 127, b = 127;   // a == b is true
Integer c = 128, d = 128;   // c == d is false
```

## Common mistakes

**Comparing strings with `==`.** Two `"java"` literals denote the same object, because JLS
§3.10.5 interns string literals, so the comparison passes in a hand-written test and fails on
a string assembled at runtime. Listing 2 shows both outcomes in one run.

**Expecting a method to replace the caller's object.** Reassigning a parameter is invisible
outside the method. If the caller needs a different object, return one.

**Trusting `==` on boxed numbers.** It works up to 127 and then stops. Compare with `equals`,
or compare the primitives.

**Unboxing a `null` wrapper.** `int n = someInteger;` compiles to an `intValue()` call and
throws `NullPointerException`, with no cast anywhere in the source to warn you.

**Reading a message that names `<local4>`.** In Java 21 the `NullPointerException` message
names the field it could not read, but it names a local variable only when the class carries
local variable debugging information. The `javac` reference for JDK 21 records that by
default only line number and source file information is generated, so a class compiled
without `-g` reports `<local4>`. Listing 1 was compiled without it and prints exactly that.

## Check yourself

<details><summary>A method takes a <code>List&lt;String&gt;</code> and calls <code>clear()</code> on it. Does the caller's list change?</summary>

Yes. The parameter holds a copy of the reference, and both references reach the same list
object. What the caller would not see is the method assigning a brand-new list to the
parameter.

</details>

<details><summary>Two <code>Integer</code> variables both hold 1000. Why is <code>==</code> false when <code>equals</code> is true?</summary>

`==` compares references and boxing produced two objects. JLS §5.1.7 guarantees shared
boxed values only from -128 to 127; 1000 is outside that range, so nothing requires the two
boxings to yield the same object. `equals` compares the wrapped `int` values.

</details>

<details><summary>Does the coat-check picture explain <code>int x = y;</code>?</summary>

No, and that is the point of the "where it breaks" section. Primitive variables hold values,
not references, so the copy is the number and there is no shared object behind it.

</details>

## Listings

1. `objects-and-references-1.java` — what a parameter holds, and the message a missing object produces.
2. `objects-and-references-2.java` — identity against equality, for strings and boxed integers.
3. `objects-and-references-3.java` — a copy of an array that copied only the references.
