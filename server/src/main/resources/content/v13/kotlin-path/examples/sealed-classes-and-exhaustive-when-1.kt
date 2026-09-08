// A sealed interface tells the compiler the whole list of direct subtypes, so a
// when over it can be complete without an else.

sealed interface Reply {
    data class Ok(val body: String) : Reply
    data class Redirect(val location: String) : Reply
    data class Failure(val status: Int, val reason: String) : Reply
    data object Pending : Reply
}

fun render(r: Reply): String = when (r) {
    is Reply.Ok -> "200 ${r.body}"
    is Reply.Redirect -> "302 -> ${r.location}"
    is Reply.Failure -> "${r.status} ${r.reason}"
    Reply.Pending -> "waiting"
}

fun main() {
    val replies = listOf(
        Reply.Ok("hello"),
        Reply.Redirect("/new"),
        Reply.Failure(404, "not found"),
        Reply.Pending,
    )
    replies.forEach { println(render(it)) }

    println("data object toString: ${Reply.Pending}")
    println("data object is one instance: ${Reply.Pending === Reply.Pending}")
    println("branches distinguish by type, not by a tag field: ${render(Reply.Ok("x"))}")
}
