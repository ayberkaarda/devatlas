// Nullability is part of the type. String and String? are different types, and
// every read of a nullable value has to state how it handles the null.

class Address(val city: String)
class Account(val name: String, val address: Address?)

fun main() {
    val present: String? = "Kotlin"
    val absent: String? = null

    // ?. skips the call and yields null; the result type is Int?, not Int.
    println("length of present: ${present?.length}")
    println("length of absent:  ${absent?.length}")

    // ?: supplies the value for the null branch.
    println("absent or default: ${absent?.length ?: -1}")

    // ?.let runs the block only when the receiver is not null.
    present?.let { println("upper: ${it.uppercase()}") }
    absent?.let { println("this line never runs") }

    // A chain of safe calls stops at the first null.
    val accounts = mapOf("ada" to Account("ada", null))
    println("city: ${accounts["ada"]?.address?.city ?: "unknown"}")
    println("missing account: ${accounts["bob"]?.address?.city ?: "unknown"}")

    // Nullability travels through inference into the element type.
    val lengths: List<Int?> = listOf(present, absent).map { it?.length }
    println("lengths: $lengths")
    println("known lengths: ${lengths.filterNotNull()}")
}
