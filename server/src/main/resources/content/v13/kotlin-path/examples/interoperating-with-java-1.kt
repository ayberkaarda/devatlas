// A type that arrives from Java carries no nullability information. Kotlin calls
// it a platform type, writes it String!, and moves the decision to the caller.
// Not everything from java.* is one, though, and the difference is worth seeing.

fun main() {
    // java.util.HashMap is a mapped type: Kotlin substitutes its own annotated
    // declaration, so the compiler already knows that get may return null and
    // refuses to call the result a String.
    val map = java.util.HashMap<String, String>()
    map["present"] = "yes"
    val fromMappedType: String? = map["missing"]
    println("mapped type, declared String?: $fromMappedType")
    println("mapped type, present key: ${map["present"]}")

    // java.lang.System is not mapped. getenv is String!, and the compiler accepts
    // it as String? or as String without saying anything either way.
    val asNullable: String? = System.getenv("BYTELORE_UNSET_VARIABLE")
    println("platform type, declared String?: ${asNullable ?: "<unset>"}")

    val threw = try {
        val asNonNull: String = System.getenv("BYTELORE_UNSET_VARIABLE")
        println("platform type, declared String: $asNonNull")
        false
    } catch (e: NullPointerException) {
        true
    }
    println("declaring it String threw NullPointerException: $threw")

    // java.util.Properties has the same shape, and the same fix: name the
    // nullability on the Kotlin side, at the one line where the value arrives.
    val props = java.util.Properties()
    props.setProperty("present", "yes")
    val present: String = props.getProperty("present") ?: "<unset>"
    val absent: String = props.getProperty("missing") ?: "<unset>"
    println("present property: $present")
    println("absent property: $absent")
}
