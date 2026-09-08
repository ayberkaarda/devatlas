// val fixes the binding. It says nothing about the object the binding points at.

fun main() {
    val names = mutableListOf("ada", "grace")
    names.add("alan")                 // legal: the list changed, the binding did not
    println("val list after add: $names")

    var count = 0
    count += 1                        // var: the binding itself is replaced
    println("var count: $count")

    val sb = StringBuilder("a")
    sb.append("b").append("c")
    println("val StringBuilder: $sb")

    // A read-only type is a view of the same object, not a copy of it.
    val backing = mutableListOf(1, 2, 3)
    val view: List<Int> = backing
    backing.add(4)
    println("read-only view sees the write: $view")
    println("view is the same object as backing: ${view === backing}")

    // toList() copies, and the copy does not follow.
    val snapshot = backing.toList()
    backing.add(5)
    println("snapshot: $snapshot")
    println("backing:  $backing")
    println("snapshot is the same object as backing: ${snapshot === backing}")
}
