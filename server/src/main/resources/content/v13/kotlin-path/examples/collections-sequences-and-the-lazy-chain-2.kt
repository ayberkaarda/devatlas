// A sequence stage does nothing when it is written. The work happens when a
// terminal operation asks for elements, and only for the elements it asks for.

fun main() {
    var evaluated = 0
    val pipeline = (1..5).asSequence().map { evaluated++; it * it }
    println("after building the chain, map calls: $evaluated")

    println("first: ${pipeline.first()}")
    println("after first(), map calls: $evaluated")

    println("toList: ${pipeline.toList()}")
    println("after toList(), map calls: $evaluated")

    // Laziness is what makes an endless sequence usable at all.
    val powers = generateSequence(1) { it * 2 }
    println("first eight powers of two: ${powers.take(8).toList()}")
    println("first power above 1000: ${powers.first { it > 1000 }}")

    val fibonacci = generateSequence(0 to 1) { (a, b) -> b to (a + b) }.map { it.first }
    println("fibonacci below 50: ${fibonacci.takeWhile { it < 50 }.toList()}")
}
