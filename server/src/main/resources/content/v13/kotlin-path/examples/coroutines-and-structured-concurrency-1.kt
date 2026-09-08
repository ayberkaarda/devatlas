// What structured concurrency guarantees, printed as guarantees. Which of two
// independent coroutines reaches its println first is not one of them, so
// nothing here records that.

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

suspend fun slowSquare(n: Int): Int {
    delay(20L * n)          // suspends this coroutine; it does not block a thread
    return n * n
}

fun main() = runBlocking {
    // The four calls overlap. awaitAll returns in argument order regardless of
    // which of them finished first.
    val deferred = listOf(4, 1, 3, 2).map { async { slowSquare(it) } }
    println("awaitAll follows the argument order: ${deferred.awaitAll()}")

    // coroutineScope does not return until every child launched inside it has
    // completed, so the collection below is complete on the next line.
    val produced = mutableListOf<Int>()
    coroutineScope {
        for (n in 1..4) {
            launch {
                delay(10L * n)
                produced += n
            }
        }
    }
    println("all four children finished: ${produced.size == 4}")
    println("sum of what they produced: ${produced.sum()}")

    // A suspending call is an ordinary call as far as sequencing goes.
    val a = slowSquare(2)
    val b = slowSquare(3)
    println("sequential result: ${a + b}")
}
