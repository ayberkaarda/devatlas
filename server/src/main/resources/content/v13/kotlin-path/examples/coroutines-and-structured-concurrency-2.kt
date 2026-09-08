// Cancellation travels down the tree, and a scope waits for the cleanup.

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

fun main() = runBlocking {
    // withTimeoutOrNull returns null rather than throwing when the block overruns.
    val tooSlow = withTimeoutOrNull(50) {
        delay(10_000)
        "finished"
    }
    println("the block that overran gave: $tooSlow")

    val inTime = withTimeoutOrNull(10_000) {
        delay(1)
        "finished"
    }
    println("the block that fitted gave: $inTime")

    // Cancelling a parent cancels its children, and finally blocks still run.
    val cleanups = mutableListOf<String>()
    val parent = launch {
        launch {
            try {
                delay(10_000)
            } finally {
                cleanups += "child"
            }
        }
        try {
            delay(10_000)
        } finally {
            cleanups += "parent"
        }
    }
    delay(20)
    parent.cancelAndJoin()
    println("both cleanup blocks ran: ${cleanups.size == 2}")
    println("parent is cancelled: ${parent.isCancelled}, completed: ${parent.isCompleted}")

    // A cancelled scope stays cancelled; it is not a pause.
    val scope = CoroutineScope(Dispatchers.Default)
    println("scope active before cancel: ${scope.coroutineContext[Job]?.isActive}")
    scope.cancel()
    println("scope active after cancel:  ${scope.coroutineContext[Job]?.isActive}")
}
