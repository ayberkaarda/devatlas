// The same chain twice. The results match; the number of times each lambda ran
// does not, and that count is the whole difference between the two types.

fun main() {
    val input = (1..10).toList()

    var eagerMaps = 0
    var eagerFilters = 0
    val eager = input
        .map { eagerMaps++; it * 2 }
        .filter { eagerFilters++; it > 10 }
        .take(2)
    println("eager result: $eager")
    println("eager map calls: $eagerMaps, filter calls: $eagerFilters")

    var lazyMaps = 0
    var lazyFilters = 0
    val lazy = input.asSequence()
        .map { lazyMaps++; it * 2 }
        .filter { lazyFilters++; it > 10 }
        .take(2)
        .toList()
    println("lazy result: $lazy")
    println("lazy map calls: $lazyMaps, filter calls: $lazyFilters")

    println("same answer: ${eager == lazy}")
    println("lazy did fewer map calls: ${lazyMaps < eagerMaps}")
}
