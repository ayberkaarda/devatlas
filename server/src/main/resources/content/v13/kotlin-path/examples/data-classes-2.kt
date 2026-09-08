// The generated members read the primary constructor and nothing else.

data class Order(val id: Int) {
    var status: String = "NEW"          // declared in the body: outside the contract
}

data class Payload(val bytes: ByteArray) // an array property: equality is identity

fun main() {
    val shipped = Order(1).also { it.status = "SHIPPED" }
    val fresh = Order(1)
    println("equal despite different status: ${shipped == fresh}")
    println("shipped.status=${shipped.status} fresh.status=${fresh.status}")

    val copy = shipped.copy()
    println("copy status: ${copy.status}")
    println("copy carried the status over: ${copy.status == shipped.status}")

    println("set of the two orders has size: ${setOf(shipped, fresh).size}")

    val one = Payload(byteArrayOf(1, 2))
    val two = Payload(byteArrayOf(1, 2))
    println("arrays with equal contents make equal data classes: ${one == two}")
    println("their contents are equal: ${one.bytes.contentEquals(two.bytes)}")
}
