// Where a null check carries forward into the rest of the block, and the two
// places where the compiler declines to carry it.

class Config(val name: String?)          // read-only property: smart cast applies
class MutableConfig(var name: String?)   // mutable property: smart cast refused

fun lengthOf(c: Config): Int =
    if (c.name != null) c.name.length else -1

fun lengthOf(c: MutableConfig): Int {
    val snapshot = c.name                // a local val the compiler can reason about
    return if (snapshot != null) snapshot.length else -1
}

fun describe(v: Any?): String = when (v) {
    null -> "null"
    is String -> "string of length ${v.length}"
    is Int -> "int doubled to ${v * 2}"
    else -> "other"
}

fun main() {
    println(lengthOf(Config("kotlin")))
    println(lengthOf(Config(null)))
    println(lengthOf(MutableConfig("kotlin")))

    println(describe(null))
    println(describe("hi"))
    println(describe(21))
    println(describe(1.5))

    // !! converts a compile-time obligation into a runtime failure.
    val absent: String? = null
    val threw = try {
        absent!!.length
        false
    } catch (e: NullPointerException) {
        true
    }
    println("!! on null threw NullPointerException: $threw")

    // requireNotNull fails in the same place but says why.
    val message = try {
        requireNotNull(absent) { "config name is required" }
        "no throw"
    } catch (e: IllegalArgumentException) {
        e.message
    }
    println("requireNotNull: $message")
}
