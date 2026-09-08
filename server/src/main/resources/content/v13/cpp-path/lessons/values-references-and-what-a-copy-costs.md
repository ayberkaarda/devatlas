## Why this exists

In C++23 the default is a value. Declaring a parameter as `std::string name` gives the
function a second string with its own storage, and the caller sees nothing at the call site
that says so — `total(names)` looks identical whether `names` was duplicated or merely
pointed at. Reference types are how the language lets you say "do not duplicate this", and
copy elision is how the language says "I will not duplicate it even though you wrote code
that appears to". Three different things can happen when you pass an object, the syntax for
two of them differs by one character, and the third is invisible. This lesson makes all three
observable by counting them.

## The idea

A by-value parameter is a photocopy handed to the person you asked for help. A reference
parameter is a slip of paper with the office address of the original document.

### Where the analogy breaks

A slip of paper is itself a thing: you can file it, hand it back, or write a different
address on it. A reference is none of that. It has no identity of its own, and it cannot be
re-seated — `alias = other` writes `other`'s value *through* the alias into the original
object rather than pointing the alias somewhere new. C++23 also has no arrays of references
and no references to references, because a reference is not an object the language will
store for you.

A photocopy is always made when you ask for one; a copy is not. Since C++17, initialising an
object from a prvalue of the same type performs no copy and no move — not "the compiler is
allowed to skip it", but that no temporary was created for anything to be copied from. Listing
2 measures zero constructions in exactly the place a reader expects one.

And a photocopy is complete, whereas a copy constructor copies only what the class says to
copy. A class holding a raw pointer copies the pointer by default, which is how two objects
come to believe they own one allocation. That defect is the subject of a later lesson;
here it is enough to know the analogy promises a completeness the language does not.

## How it works

There are three ways to name an argument, and they differ in what gets constructed:

```cpp
std::size_t byValue(Counted item);                 // constructs a new object
std::size_t byConstReference(const Counted& item); // names the caller's object, read only
void appendMark(Counted& item);                    // names the caller's object, may write
```

Listing 1 gives `Counted` a copy constructor and a move constructor that increment counters,
so the difference stops being a matter of opinion:

```text
by value        size=32 copies=1 moves=0
by const&       size=32 copies=0 moves=0
by &            size=33 copies=0 moves=0
by value (move) size=33 copies=0 moves=1
```

The last line is the one worth staring at. The parameter is still by value, so an object is
still constructed — but the argument was an rvalue, so the move constructor ran instead of the
copy constructor. "By value" does not mean "expensive"; it means "a new object", and how that
object is built depends on what you handed it.

Copy elision removes even that. When the returned expression is a prvalue of the function's
own return type, the caller's variable *is* the object the `return` statement initialises:

```cpp
Counted makeCounted(const char* text) { return Counted{text}; }
Counted returned = makeCounted("returned as a prvalue");  // copies=0 moves=0
```

Returning a *named* local is a different case: the standard permits that copy to be elided
but does not require it, so a listing cannot honestly record a count for it. Where the
returned expression is an lvalue such as a parameter, overload resolution treats it as an
rvalue first, which is why listing 2 records a move rather than a copy.

## Common mistakes

**Writing through a `const` reference.** The diagnostic names the constness rather than the
reference, which is what confuses people the first time. Paths and the candidate list below
the first note are stripped here:

```text
m1.cpp:2:43: error: no viable overloaded '+='
    2 | void rename(const std::string& tag) { tag += "!"; }
      |                                       ~~~ ^  ~~~
xstring:1462:32: note: candidate function not viable: 'this' argument has type
'const std::string', but method is not marked const
```

**Binding a non-`const` lvalue reference to a temporary.** A `const&` would have bound and
extended the temporary's lifetime; a mutable one may not, because there is nothing the write
could usefully outlive:

```text
m2.cpp:3:27: error: non-const lvalue reference to type 'basic_string<...>' cannot bind
to a temporary of type 'basic_string<...>'
```

**Copying every element of a range-based `for`.** With `-Wall` this is a diagnostic rather
than a silent cost, and with `-Werror` it stops the build:

```text
m3.cpp:5:28: error: loop variable 'name' creates a copy from type 'const std::string'
[-Werror,-Wrange-loop-construct]
      note: use reference type 'const std::string &' to prevent copying
```

## Check yourself

<details><summary>Does <code>byValue(std::move(x))</code> avoid constructing an object?</summary>

No. The parameter is still an object and is still constructed; the move constructor is chosen
instead of the copy constructor, so what is avoided is the duplication of the resource, not
the construction.

</details>

<details><summary>Why can a listing record zero copies for a prvalue return but not for a named local?</summary>

Because since C++17 the prvalue case is guaranteed by the standard, so the count is a
property. Eliding the copy of a named local is permitted, not required, so a recorded number
would describe one compiler rather than the language.

</details>

<details><summary>After <code>int&amp; alias = value; alias = other;</code>, what does <code>alias</code> name?</summary>

Still `value`. The assignment wrote `other`'s value into `value`. A reference is bound once,
at initialisation, and never again.

</details>

## Listings

1. `values-references-and-what-a-copy-costs-1.cpp` — counting what each kind of parameter costs.
2. `values-references-and-what-a-copy-costs-2.cpp` — the copies the standard forbids.
3. `values-references-and-what-a-copy-costs-3.cpp` — a reference is another name for an object.
