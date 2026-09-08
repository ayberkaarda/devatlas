// A val is read-only, not constant. Some vals are computed on every read, one
// kind is fixed at compile time, and one kind is not there yet.

class Cart {
    val items = mutableListOf<Int>()
    val total: Int
        get() = items.sum()                              // recomputed on every read
    val firstAtConstruction: Int = items.firstOrNull() ?: -1   // computed once
}

class Service {
    lateinit var endpoint: String
    fun isReady(): Boolean = this::endpoint.isInitialized
    fun call(): String = "GET $endpoint"
}

object Limits {
    const val MAX_ITEMS = 3          // compile-time constant, inlined into callers
    val ceiling = MAX_ITEMS * 10     // computed when the object is initialised
}

fun main() {
    val cart = Cart()
    println("total before: ${cart.total}")
    cart.items += listOf(2, 3)
    println("total after:  ${cart.total}")
    println("captured at construction: ${cart.firstAtConstruction}")

    println("MAX_ITEMS: ${Limits.MAX_ITEMS}")
    println("ceiling: ${Limits.ceiling}")

    val service = Service()
    println("ready before assignment: ${service.isReady()}")
    val threw = try {
        service.call()
        false
    } catch (e: UninitializedPropertyAccessException) {
        true
    }
    println("reading before assignment threw: $threw")
    service.endpoint = "/health"
    println("ready after assignment: ${service.isReady()}")
    println(service.call())
}
