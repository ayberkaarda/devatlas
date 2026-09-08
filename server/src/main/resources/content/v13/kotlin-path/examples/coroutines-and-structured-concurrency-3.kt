// Cancellation is cooperative. The library can mark a job cancelled; only the
// code inside it can stop. The gate below makes that observable without relying
// on which thread wins a race: the first coroutine cannot leave its loop until
// after cancel() has already been called.

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

fun main() = runBlocking {
    // A loop with no suspension point and no isActive check does not notice cancel().
    val gate = AtomicBoolean(false)
    val startedDeaf = CompletableDeferred<Unit>()
    var ranPastCancellation = false
    val deaf = launch(Dispatchers.Default) {
        startedDeaf.complete(Unit)
        while (!gate.get()) {
            // spin: no suspension point, so nothing here can be interrupted
        }
        ranPastCancellation = true
    }
    startedDeaf.await()
    deaf.cancel()          // the job is cancelled from here on
    gate.set(true)         // only now may the loop finish
    deaf.join()
    println("job is cancelled: ${deaf.isCancelled}")
    println("its body ran on past the cancellation: $ranPastCancellation")

    // The same shape with one check stops as soon as it is told to.
    val startedListening = CompletableDeferred<Unit>()
    var exitedOnCancel = false
    val listening = launch(Dispatchers.Default) {
        startedListening.complete(Unit)
        while (isActive) {
            // spin until the flag flips
        }
        exitedOnCancel = true
    }
    startedListening.await()
    listening.cancelAndJoin()
    println("the loop that checked isActive exited: $exitedOnCancel")

    // Every suspension point is a cancellation point.
    var sawCancellation = false
    val suspending = launch {
        try {
            delay(10_000)
        } catch (e: CancellationException) {
            sawCancellation = true
            throw e                       // rethrow: the machinery relies on it
        } finally {
            // Cleanup that has to suspend needs a context that cannot be cancelled.
            withContext(NonCancellable) {
                delay(1)
                println("cleanup finished")
            }
        }
    }
    delay(20)
    suspending.cancelAndJoin()
    println("delay threw CancellationException: $sawCancellation")
}
