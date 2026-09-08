// Nesting is where this-based blocks stop being readable, and where naming the
// parameter earns its keystrokes.

class Request(val path: String) {
    val headers = linkedMapOf<String, String>()
}

fun main() {
    // The inner apply's `this` shadows the outer one for the length of its block.
    val outer = StringBuilder("outer")
    outer.apply {
        append("-1")
        StringBuilder("inner").apply { append("-2") }   // appends to the inner builder
        append("-3")
    }
    println("outer builder: $outer")

    // Naming the parameter removes the question of which receiver is in scope.
    val request = Request("/health").apply { headers["accept"] = "application/json" }
    request.let { req ->
        println("path: ${req.path}")
        req.headers.forEach { (name, value) -> println("$name: $value") }
    }

    // A chain where every step is chosen for what it returns.
    val result = listOf(3, 1, 2)
        .also { println("before: $it") }
        .sorted()
        .also { println("after:  $it") }
        .run { "min=${first()} max=${last()}" }
    println(result)
}
