// What the compiler writes for a data class, and what changes because of it.

data class Point(val x: Int, val y: Int)
class Plain(val x: Int, val y: Int)

fun main() {
    val a = Point(1, 2)
    val b = Point(1, 2)
    println("data equals: ${a == b}")
    println("data identity: ${a === b}")
    println("data hashCodes agree: ${a.hashCode() == b.hashCode()}")
    println("toString: $a")

    val p = Plain(1, 2)
    val q = Plain(1, 2)
    println("plain equals: ${p == q}")
    println("plain toString starts with the class name: ${p.toString().startsWith("Plain@")}")

    // The consequence: a data class works as a set element and a map key.
    val seen = setOf(Point(1, 2), Point(1, 2), Point(3, 4))
    println("distinct points: ${seen.size}")
    println("set contains an equal but separate Point: ${Point(1, 2) in seen}")

    val counts = mutableMapOf<Point, Int>()
    counts[Point(0, 0)] = 1
    counts[Point(0, 0)] = (counts[Point(0, 0)] ?: 0) + 1
    println("map key count: ${counts.size}, value: ${counts[Point(0, 0)]}")

    val plainSeen = setOf(Plain(1, 2), Plain(1, 2))
    println("distinct plains: ${plainSeen.size}")
}
