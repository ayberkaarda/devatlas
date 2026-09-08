// copy names the part that changes; destructuring reads the parts by position.

data class Money(val amount: Int, val currency: String)

fun main() {
    val price = Money(1250, "EUR")
    val (amount, currency) = price
    println("$amount $currency")

    val discounted = price.copy(amount = 999)
    println("original:   $price")
    println("discounted: $discounted")
    println("currency carried over: ${discounted.currency == price.currency}")

    // component1() and component2() are ordinary functions.
    println("component1: ${price.component1()}, component2: ${price.component2()}")

    // Destructuring over an ordered map, and over a lambda parameter.
    val rates = linkedMapOf("EUR" to 100, "GBP" to 87)
    for ((code, rate) in rates) println("$code -> $rate")
    println("amounts: ${listOf(price, discounted).map { (a, _) -> a }}")
}
