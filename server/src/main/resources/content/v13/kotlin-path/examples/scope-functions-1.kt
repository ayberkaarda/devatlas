// Five functions, two questions: what is the object called inside the block
// (this or it), and what comes out (the object or the block's result).

data class Server(var host: String = "", var port: Int = 0)

fun main() {
    // apply: this, returns the receiver -> configure and keep
    val configured = Server().apply {
        host = "example.invalid"
        port = 8080
    }
    println("apply returns: $configured")

    // also: it, returns the receiver -> a side effect inside a chain
    val logged = configured.also { println("also saw port ${it.port}") }
    println("also returned the same object: ${logged === configured}")

    // run: this, returns the block's result -> compute from the object
    val url = configured.run { "https://$host:$port" }
    println("run returns: $url")

    // let: it, returns the block's result -> transform, usually after ?.
    val hostLength = configured.let { it.host.length }
    println("let returns: $hostLength")

    // with: not an extension; the receiver is an argument
    val summary = with(configured) { "$host/$port" }
    println("with returns: $summary")
}
