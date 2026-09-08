// The choice between let and also is a choice about the return value, and
// getting it wrong produces a value nobody wanted rather than an error.

fun lookup(id: Int): String? = if (id == 1) "ada" else null

fun main() {
    // ?.let: the block runs only when the value is there
    println(lookup(1)?.let { it.uppercase() } ?: "not found")
    println(lookup(2)?.let { it.uppercase() } ?: "not found")

    // let returns the block's result, so a block ending in println returns Unit
    val fromLet: Any? = lookup(1)?.let { println("let block saw $it") }
    println("let with a println returned: $fromLet")

    // also returns the receiver, which is what a logging step wants
    val fromAlso = lookup(1)?.also { println("also block saw $it") }
    println("also with a println returned: $fromAlso")

    // takeIf and takeUnless turn a predicate into a nullable value
    println("takeIf: ${4.takeIf { it % 2 == 0 }}")
    println("takeUnless: ${4.takeUnless { it % 2 == 0 }}")
    println("chained: ${"  ".takeIf { it.isNotBlank() } ?: "blank"}")
}
