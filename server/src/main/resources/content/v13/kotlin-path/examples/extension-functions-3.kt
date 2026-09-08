// Extensions in ordinary use: naming an operation at the point of the call, and
// a member extension whose body can see two receivers at once.

data class Row(val name: String, val cents: Int)

fun List<Row>.totalCents(): Int = sumOf { it.cents }

fun Int.asMoney(): String = "${this / 100}.${(this % 100).toString().padStart(2, '0')}"

infix fun Int.upTo(other: Int): List<Int> = (this..other).toList()

class Report(private val rows: List<Row>) {
    // Extension receiver Row, dispatch receiver Report: the body reads both.
    private fun Row.line(): String = "$name ${cents.asMoney()} of ${rows.totalCents().asMoney()}"

    fun render(): List<String> = rows.map { it.line() }
}

fun main() {
    val rows = listOf(Row("coffee", 250), Row("book", 1899))
    println("total: ${rows.totalCents().asMoney()}")
    println("infix: ${3 upTo 6}")
    Report(rows).render().forEach(::println)
}
