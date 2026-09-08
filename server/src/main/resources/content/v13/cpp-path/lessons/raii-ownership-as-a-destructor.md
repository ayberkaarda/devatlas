## Why this exists

Anything you acquire has to be given back: a file handle, a lock, a database connection, a
counter you incremented. A language with `finally` puts the giving-back at the call site, once
per place the resource is used, and the bug is the one place somebody forgot. C++23 has no
`finally`. It has something narrower and stronger: when an object's lifetime ends, its
destructor runs, and it runs on every path out of the scope — a `return`, a `break`, an
exception thrown four calls deeper. So the giving-back is written once, in the type, and every
user of that type gets it whether they thought about it or not. That pattern is called RAII,
and it is the organising idea of the rest of this track.

## The idea

RAII is a `finally` block that belongs to the object rather than to the call site.

### Where the analogy breaks

A `finally` block belongs to one function. A destructor runs wherever the object dies, which
may be nowhere near where it was created: inside a `std::vector` that was resized, inside
another object being destroyed, or in the middle of stack unwinding. There is no scope you
can point at as "the" place the cleanup lives.

A `finally` block also runs in whatever order the source happens to nest. Destructors have a
guaranteed order: objects in a scope are destroyed in reverse order of construction, which is
what makes a lock acquired second and released first correct by construction rather than by
review. Listing 1 prints that order.

And a `finally` block may throw as freely as any other code. A destructor may not, usefully:
in C++23 a destructor is `noexcept` unless declared otherwise, and one that throws while an
exception is already propagating calls `std::terminate`. Cleanup that can fail needs an
explicit method the caller may call and check, with the destructor as the last-resort path.

## How it works

Acquire in the constructor, release in the destructor, and the compiler writes the rest:

```cpp
ScopedConnection(std::string name) { /* acquire */ }
~ScopedConnection()                { /* release */ }
```

Once a class does that, it must say what copying and moving mean, because the default answers
are wrong for an owned resource: a copied handle would release the same resource twice. This
is the *rule of five* — if you declare a destructor, a copy constructor, a copy assignment, a
move constructor or a move assignment, you have taken responsibility for all five. Listing 2
deletes the copies and implements the moves, so ownership can be transferred but not
duplicated, and `std::exchange` leaves the source owning nothing.

The far more common case is the *rule of zero*: a class whose members are already
self-managing declares none of the five, and the compiler-generated versions are correct.
`Session` in listing 3 is a `std::string` and a `std::vector`, and the copy it gets for free
is a deep, independent copy. Declaring a destructor you do not need is not neutral — it
suppresses the implicit move operations, and the class silently starts copying.

For a resource with no class of its own — a counter, a flag, a registration — a small scope
guard holds a callable and invokes it in its destructor. Listing 3 uses one to decrement a
counter on every exit path from a function that has two.

## Common mistakes

**Copying a handle that owns something.** With the copy deleted, this is a compile error, and
the note points at the deletion:

```text
r1.cpp:12:38: error: call to deleted constructor of 'Connection'
r1.cpp:6:5: note: 'Connection' has been explicitly marked deleted here
r1.cpp:11:22: note: passing argument to parameter 'c' here
```

**Declaring a destructor and expecting moves anyway.** Declaring any of the five suppresses
the implicit move operations, so `std::move` finds only the copy constructor and uses it. No
diagnostic — the program is correct, just slower than you think. A counting type shows what
happened:

```text
copies=1 moves=0
```

**Releasing in a named method instead of the destructor.** This is the bug RAII exists to
remove, and it reappears the moment a function grows a second exit:

```text
after the successful path: open=0
after the early return:    open=1
```

Machine paths are stripped from the quoted compiler output; the wording is the compiler's.

## Check yourself

<details><summary>Why are destructors run in reverse order of construction?</summary>

Because a later object may depend on an earlier one. Reverse order means nothing is destroyed
while something that was built on top of it is still alive — which is what makes nested locks
and layered resources safe without any bookkeeping.

</details>

<details><summary>You added a destructor to a class that only holds a <code>std::vector</code>. What did it cost?</summary>

The implicit move constructor and move assignment operator are no longer generated, so
operations that used to move now copy. The class still works and every `std::move` on it
quietly became a deep copy.

</details>

<details><summary>Why can cleanup that might fail not live only in a destructor?</summary>

Because a destructor that throws during stack unwinding calls `std::terminate`. A failable
release needs a normal member function the caller can call and check, leaving the destructor
as the path taken when nobody did.

</details>

## Listings

1. `raii-ownership-as-a-destructor-1.cpp` — release on every exit path, in reverse order.
2. `raii-ownership-as-a-destructor-2.cpp` — the rule of five on a type that owns a resource.
3. `raii-ownership-as-a-destructor-3.cpp` — the rule of zero, and a scope guard.
