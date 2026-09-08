// Where the question mark sits decides what may be null: the element, the
// collection, or both.

fun total(scores: List<Int>?): Int = scores?.sum() ?: 0

fun main() {
    val elementsNullable: List<Int?> = listOf(1, null, 3)
    val listNullable: List<Int>? = null
    val both: List<Int?>? = listOf(null, 5)

    println("elements nullable, size: ${elementsNullable.size}")
    println("sum of non-null elements: ${elementsNullable.filterNotNull().sum()}")
    println("list nullable, size: ${listNullable?.size}")
    println("total of a null list: ${total(listNullable)}")
    println("total of a real list: ${total(listOf(1, 2, 3))}")
    println("both: ${both?.filterNotNull()}")

    // mapNotNull does the transform and the filter in one pass.
    val raw = listOf("1", "x", "3")
    println("parsed: ${raw.mapNotNull { it.toIntOrNull() }}")

    // The unsafe alternative, and what it costs.
    val threw = try {
        raw.map { it.toInt() }
        false
    } catch (e: NumberFormatException) {
        true
    }
    println("toInt on \"x\" threw NumberFormatException: $threw")

    // The same choice appears in the accessors.
    println("firstOrNull on empty: ${emptyList<Int>().firstOrNull()}")
    val emptyThrew = try {
        emptyList<Int>().first()
        false
    } catch (e: NoSuchElementException) {
        true
    }
    println("first on empty threw NoSuchElementException: $emptyThrew")
}
