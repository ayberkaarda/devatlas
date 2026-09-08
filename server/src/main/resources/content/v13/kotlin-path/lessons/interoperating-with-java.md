## Why this exists

Kotlin runs on the JVM and most of the code it will ever call was written in Java: the
platform library, the frameworks, the driver for whatever database is behind the
application. Those declarations were compiled without any notion of nullability, without
default arguments and without Kotlin's read-only collection types, so every call across the
boundary is a place where the guarantees of the previous eight lessons stop applying. They
do not stop quietly. They stop at a line the compiler will not warn you about, which is why
this is the lesson at the end rather than an appendix.

## The idea

A value arriving from Java is an unsigned document. Kotlin accepts it and files it, but
takes no responsibility for what it says, and prints its type with a `!` — `String!` — to
say so.

### Where the analogy breaks

An unsigned document is a document you distrust. A platform type is not "probably null": it
is *unknown*, and Kotlin's response is to decline to check in either direction. Assigning
`String!` to `String?` compiles. Assigning the same expression to `String` also compiles,
with no warning, and inserts a check that throws where the value arrives. Only one of those
two lines is safe and both look identical.

An unsigned document is also uniform. The boundary is not: some Java types are *mapped*,
meaning Kotlin substitutes its own fully annotated declaration. `java.util.HashMap` is one,
so `map["missing"]` is a properly nullable `String?` and declaring it `String` is a compile
error — which is what the first listing had to be rewritten around. `java.lang.System` is
not mapped, so `System.getenv(...)` really is a platform type. You cannot tell which you are
holding from the call site; you have to know, or look.

And a document says nothing about how it will be filed. Kotlin's own declarations change
shape on their way to Java: default arguments become one method unless you ask for more,
companion functions land on a `Companion` object, and none of Kotlin's exceptions are
checked. Interop runs in both directions and the surprises are different in each.

## How it works

At the boundary, name the nullability yourself, once, on the line where the value arrives:

```kotlin
val declared: String? = System.getenv("SOME_VARIABLE")     // safe, and states the risk
val withFallback: String = props.getProperty("k") ?: "-"   // safe, and supplies the answer
val hopeful: String = System.getenv("SOME_VARIABLE")       // compiles; throws if unset
```

Going the other way, three annotations cover most of it. `@JvmStatic` emits a real static
method beside the companion one; `@JvmOverloads` emits one method per default argument so a
Java caller can leave arguments off; `@Throws` puts an exception in the signature Java
compiles against, since Kotlin itself has no checked exceptions. The second listing reads
all three back through reflection: `render arities on the JVM: [1, 2, 3]`, and
`loadWithoutAnnotation declares: []` beside `load declares: [IOException]`.

Java interfaces with a single abstract method accept a lambda directly, so a `Callable` and a
`Comparator` can both be written as a lambda where the interface is expected, with no
anonymous class in sight.

## Common mistakes

**Letting a platform type infer.** `val name = someJavaCall()` gives the variable a platform
type, and it stays unknown wherever it flows next. Write the type down.

**Reading the exception and blaming the wrong line.** When a platform null is assigned to a
non-null type the compiler inserts the check, so the failure names the *call*, not the later
use:

```text
Exception in thread "main" java.lang.NullPointerException: getenv(...) must not be null
```

That is a better error than a null propagating three frames away, and it is the reason the
inserted check is worth having even though you did not write it.

**Treating a `List` parameter as immutable across the boundary.** `List` is read-only, which
is a statement about the reference and not about the object. The third listing casts past it
twice: `listOf(1, 2, 3)` refuses the write with an `UnsupportedOperationException`, and a
`java.util.ArrayList` behind the identical `List` type accepts it, after which the read-only
reference reports four elements.

**Expecting a Java caller to see default arguments.** Without `@JvmOverloads` there is one
method with every parameter, and Java has to pass them all.

## Check yourself

<details><summary>Why does Kotlin allow a platform type to be assigned to a non-null type at all?</summary>

Because refusing would make most Java APIs unusable without a cast on every call, and
treating every one of them as nullable would fill code with `?.` for values that are never
null. Kotlin puts the judgement on the author and inserts a runtime check where the
judgement was made.

</details>

<details><summary>How do you tell a mapped type from a platform type?</summary>

By what the compiler says about nullability. If it refuses `val x: String = m["k"]`, the
declaration Kotlin is using is annotated — a mapped type or a Java method with a nullability
annotation. If it accepts the same line silently, it is a platform type and the check moved
to run time.

</details>

<details><summary>Kotlin has no checked exceptions. What does that mean for a Java caller?</summary>

That nothing in the signature tells them what to catch unless you added `@Throws`. Their
compiler will not force a `try`, and it will refuse a `catch` for a checked exception it
believes cannot be thrown there. The annotation exists to make the signature honest.

</details>

## Listings

1. `interoperating-with-java-1.kt` — a platform type beside a mapped one.
2. `interoperating-with-java-2.kt` — SAM conversion, `@JvmStatic`, `@JvmOverloads`.
3. `interoperating-with-java-3.kt` — `@Throws`, and read-only that is not immutable.
