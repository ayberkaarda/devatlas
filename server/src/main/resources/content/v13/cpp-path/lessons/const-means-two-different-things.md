## Why this exists

The keyword `const` appears in two grammatically unrelated places and means something
different in each. On a declaration it restricts what may be done *through that name*:
`const int* p` and `int* const p` are both "const pointers" in conversation and neither
restricts the same thing as the other. On a member function it declares that calling the
function does not change the object's observable state, and — the part people miss — it
takes part in overload resolution, so `const` on the object decides which body runs. Getting
these confused produces two failures with the same feel: a compiler that refuses code you
believe is read-only, and a "const" object that changes anyway.

## The idea

`const` is a read-only key, not a lock. It restricts the door you were handed, and the
building may have other doors.

### Where the analogy breaks

A key controls a door, and doors are one per room. `const` binds to a *type*, and a pointer
type has two of them stacked: the pointer and the pointee. Which one the keyword restricts
depends on which side of the `*` it sits, so a single word restricts one of two different
things depending on position — a key has no equivalent.

A read-only key also has no say in *which room* you walk into. `const` does: a class may
declare `at(std::size_t) const` and `at(std::size_t)` as two functions, and the constness of
the object selects between them. Listing 2 counts the calls to prove the selection is real.

And a locked room stays locked. `const` on a member function is one level deep: it makes
`this` a pointer to const, which freezes the members, and a member that is itself a pointer
stays frozen while the object it points at remains fully writable. Listing 3 has a `const`
object incrementing a counter through a member pointer, with no cast and no undefined
behaviour — the object did not change, and the world did.

## How it works

Read a declaration right to left. `const` applies to what is on its left; with nothing on its
left it applies to what is on its right, which is why two spellings mean the same thing:

```cpp
const int* pointeeIsConst;       // pointer to const int   — may re-point, may not write
int const* alsoPointeeIsConst;   // the same type, spelled the other way
int* const pointerIsConst;       // const pointer to int   — may write, may not re-point
const int* const bothAreConst;   // neither
```

References need only one of these, because no reference can ever be re-seated: `int& const`
is not a type in C++23. A `const int&` is the reference equivalent of the first line.

On a member function, `const` sits after the parameter list and changes the type of `this`.
That makes it part of the function's type, so a class can carry both overloads, and the
non-const one is the one that hands out a writable reference:

```cpp
const std::string& at(std::size_t index) const;  // chosen for a const object
std::string& at(std::size_t index);              // chosen for a non-const object
```

`mutable` is the deliberate exception: a member marked `mutable` may be written by a `const`
member function. It exists because "does not change the observable state" and "does not
write any bytes" are different promises, and caches, counters and mutexes need the first
without the second.

## Common mistakes

**Assuming `const int*` protects the pointer.** It protects the pointee:

```text
c1.cpp:4:21: error: read-only variable is not assignable
    4 |     *pointeeIsConst = 2;
      |     ~~~~~~~~~~~~~~~ ^
```

**Assuming `int* const` protects the pointee.** It protects the pointer:

```text
c2.cpp:5:20: error: cannot assign to variable 'pointerIsConst' with const-qualified type
'int *const'
c2.cpp:4:16: note: variable 'pointerIsConst' declared const here
```

**Forgetting `const` on a member function that a `const` reference needs.** This is the
diagnostic that teaches const-correctness, because it propagates: one missing `const` deep in
a class forces callers to give up theirs:

```text
c3.cpp:5:33: error: 'this' argument to member function 'bump' has type 'const Counter',
but function is not marked const
```

**Believing top-level `const` on a parameter changes the signature.** It does not — the two
declarations below name one function, and defining both is a redefinition:

```text
c5.cpp:2:6: error: redefinition of 'publish'
c5.cpp:1:6: note: previous definition is here
```

Machine paths and the compiler's trailing candidate lists are stripped from the quotations
above; the wording and the carets are as the compiler printed them.

## Check yourself

<details><summary>What is the difference between <code>const int* p</code> and <code>int* const p</code>?</summary>

The first may be re-pointed but may not be written through; the second may be written through
but may not be re-pointed. `const` restricts whatever is on its left, and with nothing on its
left, whatever is on its right.

</details>

<details><summary>A <code>const</code> member function incremented a counter. Was that a cast?</summary>

Not necessarily. Either the member is `mutable`, or the counter lives behind a pointer member
— `const` freezes the pointer, not the object it points at.

</details>

<details><summary>Why does adding <code>const</code> to one member function often force changes elsewhere?</summary>

Because a `const` object can only call `const` member functions. A caller holding a `const&`
cannot reach a non-const member, so const-correctness propagates outward from wherever it is
first demanded.

</details>

## Listings

1. `const-means-two-different-things-1.cpp` — where `const` binds in a pointer declaration.
2. `const-means-two-different-things-2.cpp` — const member functions, overload selection, `mutable`.
3. `const-means-two-different-things-3.cpp` — `const` describes the path and is one level deep.
