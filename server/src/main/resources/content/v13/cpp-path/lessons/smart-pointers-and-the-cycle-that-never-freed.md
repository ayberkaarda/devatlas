## Why this exists

RAII solves cleanup for objects with a scope. Plenty of objects do not have one: a parsed
document handed back from a factory, a node in a graph, a listener that outlives the function
that registered it. For those you need an object whose lifetime you control explicitly, and
the historical way to get one was `new` paired with a `delete` you had to place yourself on
every exit path:

```cpp
Document* doc = new Document(source);
if (!doc->valid()) { return {}; }   // the delete that was not written
process(*doc);
delete doc;
```

The smart pointers in `<memory>` make that pairing a type rather than a discipline.
`std::unique_ptr` is the default and costs nothing over a raw pointer; `std::shared_ptr` is
for the genuinely shared case and brings a reference count with it; `std::weak_ptr` exists
because a reference count can be kept alive by the very structure it is counting.

## The idea

Ownership is a lease register. A `unique_ptr` is a lease with exactly one name on it, and
handing it over crosses your name out. A `shared_ptr` is a lease with a tally, and the flat is
cleared when the tally reaches zero. A `weak_ptr` is the address written on a scrap of paper,
which is not the lease.

### Where the analogy breaks

A register is kept by a landlord who can see the whole building. A `shared_ptr`'s count lives
in a control block created when the pointer was made, and it knows nothing about any other
control block. Build two `shared_ptr`s from the same raw pointer and you get two registers,
each convinced it is the only one, and the object is freed twice.

A tally that never reaches zero is at least visible on the door. Here nothing announces it.
Two objects that each hold a `shared_ptr` to the other hold each other's count at one for the
rest of the program, and the evidence in listing 3 is a destructor that did not run — not a
leak report, because a leak report would be a fact about one tool's configuration rather than
about the program.

And a lease is transferred by agreement between two parties. A `unique_ptr` transfer is a
move, and the state it leaves behind is *specified* to be null — which is unusual, and worth
remembering when the next lesson gets to types whose moved-from state is not specified at all.

## How it works

Create with `std::make_unique` or `std::make_shared` rather than `new`, so no raw owning
pointer exists even briefly. `unique_ptr` is move-only: copying is a compile error, and
`std::move` transfers. `release()` hands the raw pointer back and makes the smart pointer
null; `reset()` destroys what is held; `get()` returns a non-owning pointer that must never be
passed to `delete`.

`unique_ptr`'s second template parameter is the deleter, so it manages any resource with a
release function, not only memory:

```cpp
struct CloseHandle { void operator()(Handle* h) const noexcept { closeHandle(h); } };
using OwnedHandle = std::unique_ptr<Handle, CloseHandle>;
```

`shared_ptr` counts owners. Listing 2 walks the count up and down and shows two things worth
holding on to: a `weak_ptr` does not raise the count, and `lock()` returns a `shared_ptr` that
is null once the last owner has gone. `expired()` answers the same question without
constructing anything, and is the right check when you are not about to use the object.

The rule for cycles is structural, not clever: when two objects refer to each other, decide
which direction is ownership and make the other direction a `weak_ptr`. Parent owns child,
child observes parent.

## Common mistakes

**Copying a `unique_ptr`.** The library deleted the copy constructor, so the diagnostic is
exact (machine paths stripped):

```text
s1.cpp:6:10: error: call to deleted constructor of 'std::unique_ptr<std::string>'
memory:3475:5: note: 'unique_ptr' has been explicitly marked deleted here
s1.cpp:3:40: note: passing argument to parameter 'p' here
```

**Building two `shared_ptr`s from one raw pointer.** Two control blocks, two counts, two
frees. Nothing at compile time; the sanitized build is unambiguous (addresses and paths
stripped, frames trimmed):

```text
ERROR: AddressSanitizer: attempting double-free in thread T0
    #0 in operator delete
    #4 in std::shared_ptr<int>::~shared_ptr memory:1690
    #5 in main s2.cpp:8
```

**Using `lock()` without checking it.** The `weak_ptr` was the point: the object may be gone,
and the returned `shared_ptr` may be null:

```text
s3.cpp:11:34: runtime error: member call on null pointer of type 'std::basic_string<char>'
SUMMARY: UndefinedBehaviorSanitizer: undefined-behavior
```

## Check yourself

<details><summary>Why prefer <code>std::make_unique&lt;T&gt;(args)</code> over <code>std::unique_ptr&lt;T&gt;(new T(args))</code>?</summary>

Because no raw owning pointer ever exists as a separate value, so there is no window in which
an exception can lose it, and the type is written once instead of twice.

</details>

<details><summary>Does a <code>weak_ptr</code> keep an object alive?</summary>

No. It does not contribute to the owner count, which is exactly why it breaks a cycle. It
keeps the control block alive so that `expired()` and `lock()` can answer, and nothing more.

</details>

<details><summary>Two nodes point at each other with <code>shared_ptr</code> and go out of scope. What runs?</summary>

Neither destructor. Each object's count is held at one by the other, so the count never
reaches zero. The repair is to decide which direction owns and make the other a `weak_ptr`.

</details>

## Listings

1. `smart-pointers-and-the-cycle-that-never-freed-1.cpp` — one owner, transferable, null after a move.
2. `smart-pointers-and-the-cycle-that-never-freed-2.cpp` — counting owners, and what `weak_ptr` adds.
3. `smart-pointers-and-the-cycle-that-never-freed-3.cpp` — the cycle, and the weakened edge that breaks it.
