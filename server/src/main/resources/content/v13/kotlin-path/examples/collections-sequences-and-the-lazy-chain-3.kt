// Two ways a sequence differs from the list you replaced with it: some can only
// be walked once, and a stage that has to see everything cancels the saving.

fun main() {
    val once = listOf(1, 2, 3).iterator().asSequence()
    println("first pass over an iterator-backed sequence: ${once.toList()}")
    val threw = try {
        once.toList()
        false
    } catch (e: IllegalStateException) {
        true
    }
    println("second pass threw IllegalStateException: $threw")

    val reusable = listOf(1, 2, 3).asSequence()
    println("collection-backed, first pass:  ${reusable.toList()}")
    println("collection-backed, second pass: ${reusable.toList()}")

    val words = listOf("delta", "alpha", "charlie", "bravo")
    println("eager: ${words.filter { it.length == 5 }.sorted()}")
    println("lazy:  ${words.asSequence().filter { it.length == 5 }.sorted().toList()}")

    // sorted() cannot emit its first element until it has consumed the last one,
    // so first() no longer short-circuits.
    var visitedBeforeSort = 0
    val firstSorted = words.asSequence().onEach { visitedBeforeSort++ }.sorted().first()
    println("first after sorting: $firstSorted")
    println("elements visited to produce it: $visitedBeforeSort of ${words.size}")

    var visitedWithoutSort = 0
    val firstMatch = words.asSequence().onEach { visitedWithoutSort++ }.first { it.startsWith("a") }
    println("first match without sorting: $firstMatch")
    println("elements visited to produce it: $visitedWithoutSort of ${words.size}")
}
