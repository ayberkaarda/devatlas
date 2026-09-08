// Immutability is one level deep unless you pay for another level.

class Team(val name: String, val members: List<String>)

fun main() {
    val roster = mutableListOf("ada")
    val team = Team("core", roster)          // declared List, backed by a MutableList
    roster.add("grace")                      // the caller still holds the mutable end
    println("team sees the caller's write: ${team.members}")

    val safe = Team("core", roster.toList()) // defensive copy at the boundary
    roster.add("alan")
    println("defensive copy: ${safe.members}")
    println("caller's list:  $roster")

    // A read-only outer holding mutable inner values is still mutable inside.
    val byTeam: Map<String, MutableList<String>> = linkedMapOf("core" to mutableListOf("ada"))
    byTeam["core"]?.add("grace")
    println("read-only map, mutable values: ${byTeam["core"]}")

    // Depth costs a decision at every level.
    val deep: Map<String, List<String>> = byTeam.mapValues { (_, v) -> v.toList() }
    byTeam["core"]?.add("alan")
    println("deep copy unaffected: ${deep["core"]}")
    println("original moved on:   ${byTeam["core"]}")
}
