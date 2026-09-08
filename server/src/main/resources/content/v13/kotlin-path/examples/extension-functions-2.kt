// Extensions can take a nullable receiver, can be generic, and can be properties.
// What they cannot do is beat a member with the same signature.

class Greeter {
    fun greet() = "member"
}

// Different signature, so this one is reachable.
fun Greeter.greet(loudly: Boolean) = if (loudly) "EXTENSION" else "extension"

// The receiver may be null: the check happens inside the function.
fun String?.orPlaceholder(): String = this ?: "<none>"

fun <T> List<T>.secondOrNull(): T? = if (size >= 2) this[1] else null

// An extension property has no backing field, only accessors.
val <T> List<T>.penultimate: T?
    get() = if (size >= 2) this[size - 2] else null

fun main() {
    println(Greeter().greet())
    println(Greeter().greet(loudly = true))

    val missing: String? = null
    println("null receiver: ${missing.orPlaceholder()}")
    println("real receiver: ${"here".orPlaceholder()}")

    println("secondOrNull of three: ${listOf("a", "b", "c").secondOrNull()}")
    println("secondOrNull of none:  ${emptyList<String>().secondOrNull()}")
    println("penultimate: ${listOf(1, 2, 3, 4).penultimate}")
    println("penultimate of one: ${listOf(1).penultimate}")
}
