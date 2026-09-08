## Why this exists

Every codebase accumulates small operations that belong to a type you do not own: turning a
`String` into a domain identifier, formatting an `Int` as money, taking the second element
of a list. The usual home for them is a `StringUtils` class, which reads backwards at the
call site — the helper's name comes first and the value it operates on is buried in an
argument list. Kotlin 2.4's extension functions let you write those operations with the
value in front, so `amount.asMoney()` reads the way the operation is thought about, without
modifying `Int` and without inheriting from anything.

## The idea

An extension is a helper function with a nicer address. The compiler rewrites
`amount.asMoney()` into a call to a static function with `amount` as its first argument, so
what you gain is notation.

### Where the analogy breaks

Because it stops there, and readers keep going.

The rewrite is decided at compile time from the *declared* type of the expression, not from
the object's runtime class. An extension on `Shape` and one on `Circle` are two unrelated
functions; a variable declared `Shape` holding a `Circle` calls the `Shape` one. Members
behave the opposite way, and the first listing puts the two side by side: `virtual()`
through a `Base` reference gives `Derived.virtual`, while `label()` through a `Shape`
reference gives `Shape` even though the object is a `Circle`. Nothing about the syntax hints
at the difference.

A member always wins. Declaring an extension with the same name and signature as an existing
member does not override it; the extension becomes unreachable through that receiver, and
the compiler says so as a warning rather than an error.

And an extension has no privileged access. It sees exactly what any caller in that file
sees, so a `private` property is out of reach. Extensions are a notation for outsiders,
which is also why adding one to a class you do not own cannot break that class.

## How it works

The receiver type goes before the function name, and `this` inside the body is the receiver:

```kotlin
fun Int.asMoney(): String = "${this / 100}.${(this % 100).toString().padStart(2, '0')}"

fun <T> List<T>.secondOrNull(): T? = if (size >= 2) this[1] else null

val <T> List<T>.penultimate: T?      // an extension property: accessors, no field
    get() = if (size >= 2) this[size - 2] else null
```

The receiver may itself be nullable, in which case the null check moves inside the function
and callers stop needing `?.`:

```kotlin
fun String?.orPlaceholder(): String = this ?: "<none>"

val missing: String? = null
println(missing.orPlaceholder())   // no safe call needed; prints <none>
```

An extension declared inside a class has two receivers at once — the extension receiver it
names and the instance of the enclosing class — so its body can read both. The third listing
uses that to give a `Row` a `line()` function that can also see the `Report` it belongs to,
which is the one case where an extension is not merely notation.

## Common mistakes

**Expecting an extension to be overridden.** No diagnostic; the result is simply the wrong
function. The first listing prints `[Shape, Shape]` for a `List<Shape>` that holds two
`Circle` objects, alongside `[Circle, Circle]` for their runtime classes.

**Redeclaring a member.** Here the compiler does speak, and it names the member that wins:

```text
warning: this extension is shadowed by a member: 'fun greet(): String' defined in 'Greeter'.
```

That is a warning rather than an error because the extension may still be reachable from
elsewhere — through a different receiver type, or from Java as the static function it
compiles to.

**Reaching for private state.** The message names the property and the class:

```text
error: cannot access 'val hidden: Int': it is private in 'Secretive'.
```

**Importing nothing.** An extension is only in scope where it is imported, so the same call
that compiles in one file fails to resolve in the next. The import is of the function, not
of the type it extends.

## Check yourself

<details><summary>Why can an extension not be <code>open</code> or <code>override</code>?</summary>

Because those words describe entries in a class's virtual method table, and an extension has
no entry — it compiles to a static function chosen at the call site. There is nothing for a
subclass to replace.

</details>

<details><summary>An extension on <code>String?</code> and one on <code>String</code>: which does a <code>String</code> receiver pick?</summary>

The one on `String`, as the more specific receiver type. That is why the nullable-receiver
form is written deliberately for the cases where handling the null inside the function is
the point, rather than being the default shape.

</details>

<details><summary>Where should an extension live?</summary>

Next to the code that uses it, at file scope, unless it is genuinely general. An extension
imported across a whole codebase is public API you did not review; one declared in the file
that needs it is a local convenience with a visible scope.

</details>

## Listings

1. `extension-functions-1.kt` — static dispatch beside a virtual member.
2. `extension-functions-2.kt` — nullable receivers, generics, and extension properties.
3. `extension-functions-3.kt` — extensions in ordinary use, including a member extension.
