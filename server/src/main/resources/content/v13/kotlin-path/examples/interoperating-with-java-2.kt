// Going the other way: what Java sees of a Kotlin declaration, and what the
// annotations change about it.

import java.lang.reflect.Modifier
import java.util.concurrent.Callable

class Formatter {
    @JvmOverloads
    fun render(value: Int, prefix: String = "", suffix: String = ""): String =
        "$prefix$value$suffix"

    companion object {
        @JvmStatic
        fun of(): Formatter = Formatter()
    }
}

fun main() {
    // A Java interface with a single abstract method accepts a lambda directly.
    val task = Callable { 6 * 7 }
    println("Callable via SAM conversion: ${task.call()}")

    val byLength = Comparator<String> { a, b -> a.length - b.length }
    println("sorted by length: ${listOf("ccc", "a", "bb").sortedWith(byLength)}")

    Runnable { println("Runnable ran") }.run()

    // Default arguments are a Kotlin idea. @JvmOverloads asks for one JVM method
    // per arity so a Java caller can leave arguments off.
    val arities = Formatter::class.java.declaredMethods
        .filter { it.name == "render" }
        .map { it.parameterCount }
        .sorted()
    println("render arities on the JVM: $arities")

    // @JvmStatic puts a real static method on the class as well as on Companion.
    val staticOf = Formatter::class.java.declaredMethods
        .any { it.name == "of" && Modifier.isStatic(it.modifiers) }
    println("a static method named of exists: $staticOf")
    println(Formatter.of().render(42, prefix = "#"))
}
