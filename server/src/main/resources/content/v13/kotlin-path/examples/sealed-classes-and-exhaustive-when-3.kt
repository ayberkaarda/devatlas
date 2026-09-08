// Sealed types carry different data per case, which is what makes them better
// than an enum plus a nullable field for every case that needs one.

sealed interface Parsed<out T> {
    data class Value<T>(val value: T) : Parsed<T>
    data class Invalid(val input: String, val reason: String) : Parsed<Nothing>
}

fun parsePort(raw: String): Parsed<Int> {
    val n = raw.toIntOrNull() ?: return Parsed.Invalid(raw, "not a number")
    return if (n in 1..65535) Parsed.Value(n) else Parsed.Invalid(raw, "out of range")
}

fun main() {
    for (raw in listOf("8080", "0", "http", "65536")) {
        // `when (val p = ...)` binds and matches in one place; each branch is
        // smart-cast to its own subtype, so p.value and p.reason both resolve.
        when (val p = parsePort(raw)) {
            is Parsed.Value -> println("$raw -> port ${p.value}")
            is Parsed.Invalid -> println("$raw -> rejected: ${p.reason}")
        }
    }

    // Because the when is complete, it is an expression with a type.
    val summarise: (String) -> String = { raw ->
        when (val p = parsePort(raw)) {
            is Parsed.Value -> "ok(${p.value})"
            is Parsed.Invalid -> p.reason
        }
    }
    println(listOf("1", "x").map(summarise))

    // Invalid is a Parsed<Nothing>, so it fits wherever a Parsed<T> is wanted.
    val asString: Parsed<String> = Parsed.Invalid("-", "not a number")
    println("covariance: ${(asString as Parsed.Invalid).reason}")
}
