## Why this exists

`List<String>` and `List<Integer>` are different types to the compiler and the same class to
the virtual machine. Almost every generics restriction that looks arbitrary — no `new T[]`,
no `instanceof List<String>`, two overloads that "have the same erasure" — follows from that
one fact. The restrictions are not a style guide. They are the compiler declining to promise
something it cannot deliver, and the failures that appear when it is talked into promising it
anyway land far from the code that caused them.

## The idea

Generics are scaffolding. While the building goes up, the scaffolding is what keeps every
piece in the right place; when the building opens, it is gone, and the walls stand because of
where the scaffolding put them, not because it is still there.

### Where the analogy breaks

Scaffolding leaves no trace, and Java's leaves a great deal. What is erased is the
**instance**: a `List` object at run time does not know it was created as `List<String>`. The
**declaration** keeps everything. A field, a method signature or a class declaration carries
its type arguments into the class file, and reflection reads them straight back —
`Field.getGenericType()` on a `List<String>` field returns `java.util.List<java.lang.String>`,
as listing 2 prints. "Erased at run time" is the half-truth that costs people an afternoon.

The analogy also mispredicts the failure. A building whose scaffolding was wrong falls down
where the mistake was. Here the compiler inserts a cast at every site that reads a value with
a specific type, so a wrongly typed element sits in the collection unnoticed and the
`ClassCastException` surfaces at whoever reads it, in a different method, possibly in a
different file.

## How it works

JLS §4.6 defines erasure as a mapping to types that are never parameterized types or type
variables: the erasure of `G<T1,...,Tn>` is the erasure of `G`, and the erasure of a type
variable is the erasure of its leftmost bound. So `<T extends Comparable<T>> T largest(T, T)`
compiles to a method taking two `Comparable` parameters, which listing 3 reads back from the
class file.

```java
new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass()   // true
```

Because the check cannot exist at run time, it has to be complete at compile time. A raw type
switches it off for one reference, and everything downstream is on trust:

```java
List raw = strings;          // strings is a List<String>
raw.add(Integer.valueOf(42));// accepted
String first = strings.get(0);
// ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String
```

The exception names the read site, not the `add`. That distance is what makes heap pollution
expensive to diagnose and why the compiler's unchecked warnings are worth reading.

One artefact you will meet in a stack trace: a bridge method. `Version implements
Comparable<Version>` must also satisfy the erased `compareTo(Object)` that `Comparable`
declares, so the compiler generates a synthetic forwarding method. Listing 3 finds both and
reports `bridge=true` for the generated one.

```java
compareTo(Version) bridge=false synthetic=false
compareTo(Object)  bridge=true  synthetic=true
```

## Common mistakes

**Creating an array of a type variable.** `new T[n]` is `error: generic array creation`. The
array would need a runtime component type, and erasure has none to give it.

**Testing a parameterized type.** `o instanceof List<String>` is answered by
`javac --release 21` with `error: Object cannot be safely cast to List<String>`. Write
`instanceof List<?>`, which is what can actually be checked.

**Overloading on type arguments.** `f(List<String>)` and `f(List<Integer>)` produce
`error: name clash: f(List<Integer>) and f(List<String>) have the same erasure`.

**Believing type arguments are unrecoverable.** They are recoverable from declarations, which
is how serialisation libraries know a field is a `Map<String, List<Integer>>`. They are not
recoverable from an instance.

**Silencing an unchecked warning to make a raw type compile.** The warning is the only notice
you get before a `ClassCastException` in unrelated code.

## Check yourself

<details><summary>Why does the <code>ClassCastException</code> appear at <code>get</code> rather than at <code>add</code>?</summary>

The `add` went through a raw reference, where no check was generated. The compiler inserts a
cast to `String` at the site that reads the element as a `String`, and that cast is where the
mismatch is finally noticed.

</details>

<details><summary>Reflection reports a field's type as <code>List&lt;String&gt;</code>. Does that contradict erasure?</summary>

No. The field's declaration is recorded in the class file, so a declaration keeps its type
arguments. The `List` object the field points at still does not know what it was declared to
hold.

</details>

<details><summary>What does <code>&lt;T extends Number&gt;</code> erase to?</summary>

`Number`, by JLS §4.6: the erasure of a type variable is the erasure of its leftmost bound.
An unbounded `<T>` erases to `Object`.

</details>

## Listings

1. `generics-and-erasure-1.java` — what the runtime does not know, and where the cast fails.
2. `generics-and-erasure-2.java` — what a declaration keeps, read back through reflection.
3. `generics-and-erasure-3.java` — a bridge method, and a type variable's leftmost bound.
