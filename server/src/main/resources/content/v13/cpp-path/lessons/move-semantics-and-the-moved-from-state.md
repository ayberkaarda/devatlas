## Why this exists

A `std::vector` of a million strings returned from a function used to be a reason to write a
worse function. Copying it means a million allocations and a million string copies, all to
build something the source is about to throw away. Move semantics let a type say what should
happen when the source *is* about to be thrown away: take its buffer, leave it in a state
that is safe to destroy, and do no allocation at all. That is the whole feature. Everything
awkward about it comes from the second half — the source object still exists, and something
has to be true about it afterwards.

## The idea

Moving is transferring the deed to a house, not rebuilding the house next door.

### Where the analogy breaks

After a deed transfer the seller no longer exists in the story. In C++23 the source object is
still there, still has to be destroyed, and may still be assigned to and used again. It has
not been emptied, ended or invalidated; it has been left in a state that somebody has to have
decided on.

Who decided depends on the type. `std::unique_ptr` is specified: after a move it is empty,
and you may rely on that. The standard library's containers and strings are specified only as
"valid but unspecified" — you may destroy them, and you may assign a new value and carry on,
but no listing may print what is in one, because the answer is not the language's to give. A
class you write chooses for itself, and listing 3's `Account` zeroes its balance because that
is the state its author chose and documented.

And a deed transfer is a deliberate act with a notary. `std::move` is neither deliberate nor
an act: it is a cast. It changes which overload is selected and does nothing else. Cast a
`const` object and the move constructor is not viable, so the copy constructor is selected
instead — silently, with no diagnostic, which listing 2 records as `copies=1 moves=0`.

## How it works

A move constructor takes an rvalue reference, `T(T&&)`, and is selected when the argument is
an rvalue: a temporary, or an lvalue that `std::move` has cast into one. Writing it means
deciding what the source keeps; `std::exchange` is the usual tool, because it reads the old
value and installs the new one in a single expression.

Mark it `noexcept`. This is the part that is easy to read as style advice and is not:

```cpp
Cheap(Cheap&& other) noexcept;   // vector relocates by moving
Risky(Risky&& other);            // vector relocates by copying
```

`std::vector` must leave the vector unchanged if reallocation throws. If the element's move
constructor may throw, the only way to keep that promise is to copy. Listing 1 forces one
reallocation of a three-element vector and counts what happened: three moves and no copies
for the `noexcept` type, three copies and no moves for the other. Same code, same container,
one keyword.

For parameters, take by value and move into place — the *sink* idiom. A caller with a
temporary pays one move; a caller with an lvalue pays one copy and one move, which is what an
overload pair would have cost anyway, without the second overload.

## Common mistakes

**`return std::move(local);`.** It prevents the elision it was meant to help, and the
compiler says so:

```text
mm1.cpp:5:12: error: moving a local object in a return statement prevents copy elision
[-Werror,-Wpessimizing-move]
mm1.cpp:5:12: note: remove std::move call here
```

**Moving a variable to itself.** Usually the result of a `swap` written by hand:

```text
mm2.cpp:5:10: error: explicitly moving variable of type 'std::string' to itself
[-Werror,-Wself-move]
```

**Moving a `const` object.** No diagnostic at all. The cast succeeds, the move constructor is
not viable for a `const` source, and the copy constructor runs. The counters in listing 2 are
the only evidence:

```text
moving a const lvalue: copies=1 moves=0
```

**Using a moved-from object as though it still held something.** For `unique_ptr` the state is
specified, so this is a plain null dereference and both sanitizers report it (paths stripped,
frames trimmed):

```text
memory:3449:16: runtime error: reference binding to null pointer of type 'int'
SUMMARY: UndefinedBehaviorSanitizer: undefined-behavior
ERROR: AddressSanitizer: access-violation on unknown address
The signal is caused by a READ memory access.
```

## Check yourself

<details><summary>What does <code>std::move</code> move?</summary>

Nothing. It is a cast to an rvalue reference. The move happens later, if overload resolution
picks a constructor or assignment operator that takes one.

</details>

<details><summary>Why did marking a move constructor <code>noexcept</code> change how many copies a vector made?</summary>

Because reallocation must leave the vector unchanged if it throws. A move that may throw
cannot be undone, so the implementation copies instead. Marking it `noexcept` is what permits
the move.

</details>

<details><summary>May a lesson print the contents of a moved-from <code>std::string</code>?</summary>

No. The standard says only that it is valid, not what it holds, so any printed value would
describe one implementation. What can be shown is that assigning to it makes it an ordinary
string again.

</details>

## Listings

1. `move-semantics-and-the-moved-from-state-1.cpp` — `noexcept` decides whether a container moves or copies.
2. `move-semantics-and-the-moved-from-state-2.cpp` — `std::move` is a cast, and the const that defeats it.
3. `move-semantics-and-the-moved-from-state-3.cpp` — what is left behind, specified and unspecified.
