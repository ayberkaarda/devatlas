// Two more places the boundary shows: exceptions Kotlin does not check, and
// collections whose read-only type is not a promise about the object.

import java.io.IOException

class Store {
    @Throws(IOException::class)
    fun load(): String = "loaded"

    fun loadWithoutAnnotation(): String = "loaded"
}

fun main() {
    // Kotlin has no checked exceptions; @Throws is what puts one in the signature
    // a Java caller compiles against.
    val declared = Store::class.java.getMethod("load").exceptionTypes.map { it.simpleName }
    val undeclared = Store::class.java.getMethod("loadWithoutAnnotation")
        .exceptionTypes.map { it.simpleName }
    println("load declares: $declared")
    println("loadWithoutAnnotation declares: $undeclared")
    println("both still run: ${Store().load()} / ${Store().loadWithoutAnnotation()}")

    // List is read-only, which is a statement about the reference and not about
    // the object. Casting past it reaches whatever is actually underneath.
    val fromKotlin: List<Int> = listOf(1, 2, 3)
    val refused = try {
        (fromKotlin as MutableList<Int>).add(4)
        false
    } catch (e: UnsupportedOperationException) {
        true
    }
    println("listOf refused the write: $refused")

    val fromJava: List<Int> = java.util.ArrayList(listOf(1, 2, 3))
    val accepted = try {
        (fromJava as MutableList<Int>).add(4)
        true
    } catch (e: UnsupportedOperationException) {
        false
    }
    println("an ArrayList behind the same type accepted it: $accepted")
    println("the read-only reference now sees ${fromJava.size} elements")
}
