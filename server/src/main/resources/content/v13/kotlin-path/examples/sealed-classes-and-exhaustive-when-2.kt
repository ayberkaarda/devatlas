// The same logic written twice: once so that a new subtype fails the build, and
// once with an else so that a new subtype falls silently into the wrong branch.

sealed interface Event {
    data class Click(val x: Int, val y: Int) : Event
    data class Key(val code: Int) : Event
    data object Close : Event
}

fun describe(e: Event): String = when (e) {
    is Event.Click -> "click at ${e.x},${e.y}"
    is Event.Key -> "key ${e.code}"
    Event.Close -> "close"
}

fun describeLoosely(e: Event): String = when (e) {
    is Event.Click -> "click at ${e.x},${e.y}"
    else -> "something else"
}

fun main() {
    val events = listOf(Event.Click(3, 4), Event.Key(27), Event.Close)

    events.forEach { println(describe(it)) }
    events.forEach { println(describeLoosely(it)) }

    // A when without a subject is a chain of conditions, and needs an else when
    // it is used as an expression.
    val code = 27
    println(
        when {
            code < 32 -> "control"
            code < 127 -> "printable"
            else -> "beyond ascii"
        }
    )

    // A when used as a statement over a sealed type must also be complete.
    for (e in events) {
        when (e) {
            is Event.Click -> print("C")
            is Event.Key -> print("K")
            Event.Close -> print("X")
        }
    }
    println()
}
