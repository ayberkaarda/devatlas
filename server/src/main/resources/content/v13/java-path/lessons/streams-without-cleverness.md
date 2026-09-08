## Why this exists

A stream pipeline and a `for` loop compile to code that does the same work, and the reason to
prefer one over the other is which of them a reader understands faster. That decision goes
wrong in a predictable direction: the pipeline gets written because it is a pipeline, side
effects get smuggled into a lambda, and the result is a loop with worse diagnostics. Knowing
what the streams package actually promises is what makes the choice a choice.

## The idea

A pipeline is a factory line. There is a source at one end, stations along it, and a truck at
the far end. Nothing on the line moves until the truck backs up to the dock and asks for
output; when it does, each item travels the whole line before the next item starts.

### Where the analogy breaks

A factory reopens tomorrow. A stream does not: it is consumed once, and a second terminal
operation on the same object is answered with `IllegalStateException: stream has already been
operated upon or closed`. If you need the data twice, keep the collection, not the stream.

"Each item travels the whole line" is also only true of the stateless stations. `sorted` and
`distinct` have to see input they have not been given yet, so they hold items back — a
pipeline containing `sorted` does not short-circuit the way listing 1's `filter` and
`findFirst` do. And the truck, not the line, decides when to stop: short-circuiting is a
property of the terminal operation.

## How it works

Intermediate operations build the pipeline and run nothing. The terminal operation pulls, and
listing 1 shows the pull arriving element by element — `findFirst` over a filter tests two
employees out of four and stops.

```java
STAFF.stream().map(e -> { System.out.println("never printed"); return e; });
// no terminal operation, so no output
```

What you get back at the end is worth reading carefully, because two collectors with almost
the same name differ. `Stream.toList` returns a `List` whose javadoc says it is unmodifiable
and that calls to any mutator method will always throw `UnsupportedOperationException`.
`Collectors.toList` documents the opposite posture: there are no guarantees on the type,
mutability, serializability or thread-safety of the list returned.

```java
STAFF.stream().map(Employee::name).toList().add("Alan");   // UnsupportedOperationException
```

`Collectors.toMap` refuses to guess when two elements map to one key. Without a merge
function it throws, and the message names the collision:
`Duplicate key engineering (attempted merging values Ada and Grace)`. Supplying the merge
function is the fix, and it makes the intent explicit at the call site.

```java
.collect(Collectors.toMap(Employee::department, Employee::name, (a, b) -> a + " and " + b))
```

The clarity argument runs the other way too. A loop with an index, an early `break` on a
condition that is not a predicate, or an accumulation into something you already own reads
better as a loop, and listing 3 puts both versions side by side.

## Common mistakes

**Reusing a stream.** Assigning a stream to a variable and terminating it twice throws
`IllegalStateException`. Streams are values you consume, not collections you keep.

**Assuming `toList()` is modifiable.** It is not, and the failure is an
`UnsupportedOperationException` in whatever code later tries to add to it. Use
`Collectors.toList` if you need a mutable result, and know that its type is unspecified.

**Calling `toMap` on data with duplicate keys.** Works in a test with three rows, throws in
production. Pass a merge function whenever the key is not known to be unique.

**Collecting through `forEach` into an outer list.** It is a loop wearing a pipeline's
clothes, and it gives up the one thing the pipeline offered — a result you can name.

**Mixing null policies.** `Stream.toList` accepts `null` elements;
`Collectors.toUnmodifiableList` throws `NullPointerException` on them. Listing 2 runs both.

## Check yourself

<details><summary>Why does a pipeline with no terminal operation print nothing?</summary>

Intermediate operations only describe the pipeline. Without a terminal operation nothing pulls
elements through, so no lambda runs.

</details>

<details><summary>When does the element-at-a-time picture stop holding?</summary>

At a stateful operation. `sorted` must consume the entire input before it can emit its first
element, so nothing downstream of it sees anything until the source is exhausted.

</details>

<details><summary>A collector is needed for a mutable list. Which one, and what is not guaranteed?</summary>

`Collectors.toList`. Its javadoc guarantees nothing about the type, mutability,
serializability or thread-safety of what comes back, so if you need a specific class, use
`Collectors.toCollection` with a supplier.

</details>

## Listings

1. `streams-without-cleverness-1.java` — the same job as a loop and as a pipeline, and laziness.
2. `streams-without-cleverness-2.java` — collecting, and what the result will and will not allow.
3. `streams-without-cleverness-3.java` — single use, and where a loop is the clearer code.
