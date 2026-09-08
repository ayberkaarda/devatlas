// An extension is resolved from the declared type of the expression, at compile
// time. A member is resolved from the runtime type. Putting the two side by side
// is the fastest way to see the difference.

open class Shape
class Circle : Shape()

fun Shape.label() = "Shape"
fun Circle.label() = "Circle"

open class Base {
    open fun virtual() = "Base.virtual"
}

class Derived : Base() {
    override fun virtual() = "Derived.virtual"
}

fun main() {
    val asShape: Shape = Circle()      // runtime type Circle, declared type Shape
    val asCircle: Circle = Circle()

    println("declared Shape:  ${asShape.label()}")
    println("declared Circle: ${asCircle.label()}")

    val b: Base = Derived()
    println("member through a Base reference: ${b.virtual()}")

    // Inside the lambda the element's declared type is Shape, so both resolve to
    // the Shape extension even though one of them is a Circle at runtime.
    val shapes: List<Shape> = listOf(asShape, asCircle)
    println("labels through List<Shape>: ${shapes.map { it.label() }}")
    println("runtime classes: ${shapes.map { it.javaClass.simpleName }}")
}
