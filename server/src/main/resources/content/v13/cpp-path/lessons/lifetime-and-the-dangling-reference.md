## Why this exists

C++23 gives you references and views with no runtime cost, and charges for them in a single
currency: you must know how long the referent lives. Nothing checks it. A reference to an
object that has been destroyed is not an error the language reports; it is a read of storage
that no longer belongs to anyone, and the program keeps running. This is the failure mode
that makes C++ different from every other language in this corpus, and it is why compiling
cleanly proves so little here. The compiler catches the obvious cases and says so loudly. The
case in this lesson compiles under `-Wall -Wextra -Wpedantic -Werror` with nothing to say,
runs, and prints a wrong answer that looks like an answer.

## The idea

A reference is a street address written on a card. Demolishing the house does not change
what the card says.

### Where the analogy breaks

You can walk to the address and see rubble. In C++23 there is no such check. Reading through
a dangling reference has undefined behaviour, and the most common thing a program does with
it is succeed — the storage is still mapped, some bytes are still there, and you get a
number.

Worse, the plot gets rebuilt. Automatic storage is reused by the next call, so the same
address can name a different, entirely valid object. The read then returns a real value that
belongs to something else, which is how a lifetime bug turns into a data bug three functions
away.

And an address is stable while the house stands, whereas a `std::string_view` can dangle
while its owner is alive and well: appending to a `std::string` may move its characters, and
the view still points at where they used to be. The referent of a view is the storage, not
the object you were thinking about.

## How it works

An automatic object lives until the end of its enclosing block, and objects are destroyed in
reverse order of construction. A temporary lives until the end of the full-expression — which
is why listing 1 prints the length *before* the destruction message, since the call is part of
the same expression. Binding a temporary to a `const` reference extends its life to the life
of the reference:

```cpp
const Tracer& kept = make("extended");   // destroyed at the end of this block
std::println("{}", kept.name);           // legal
```

That extension is the whole of the exception, and it does not travel. It does not apply when
a reference is returned from a function, and it does not apply to a reference member
initialised from a temporary in a constructor's member-initialiser list. Both of those look
like the line above and neither behaves like it.

Dynamic storage duration is the third case, and the only one whose end you write yourself.
An object created with `new` lives until a matching `delete`, which means the lifetime is a
fact about your control flow rather than about any scope:

```cpp
int* p = new int(5);
delete p;
std::printf("%d\n", *p);   // reads storage that was handed back
```

Every later lesson in this track exists to stop you writing that line, and the tools for it
— destructors, `std::unique_ptr`, `std::vector` — are the next three lessons. It appears here
once so that the three storage durations can be seen together.

The repair is almost always to return an owned value. Listing 2 returns `std::string` rather
than `const std::string&` and is correct for any argument, including two temporaries.
Returning a reference stays correct when the referent demonstrably outlives the call: an
element of a container the caller owns, or an object with static storage duration.

## Common mistakes

**Returning a reference to a local.** The compiler sees this one:

```text
l1.cpp:4:12: error: reference to stack memory associated with local variable 'local'
returned [-Werror,-Wreturn-stack-address]
```

**Making a view of a temporary.** It sees this one too, through the standard library's
lifetime annotations:

```text
l2.cpp:4:29: error: object backing the pointer will be destroyed at the end of the
full-expression [-Werror,-Wdangling-gsl]
```

**Reading through a pointer after `delete`.** No diagnostic at compile time; the sanitized
build names it exactly (addresses and paths stripped, frame lists cut):

```text
ERROR: AddressSanitizer: heap-use-after-free
READ of size 4 thread T0
    #0 in main d1.cpp:5
freed by thread T0 here:
    #1 in main d1.cpp:4
```

**Returning one of two reference parameters.** It does not see this one. The function is
correct for some callers and wrong for others, and the diagnostic would have to be at the
call:

```cpp
const std::string& longerOf(const std::string& a, const std::string& b) {
    return a.size() >= b.size() ? a : b;
}
const std::string& chosen = longerOf(std::string("alpha"), std::string("be"));
std::println("size={}", chosen.size());
```

Compiled with `-Wall -Wextra -Wpedantic -Werror`, this builds without a single diagnostic and
prints `size=0` — not five, which would at least have been the length of `alpha`. Rebuilt
with `-fsanitize=address,undefined` and run, it reports (addresses, thread numbers and machine
paths stripped, and the frame list cut after the first two entries):

```text
ERROR: AddressSanitizer: stack-use-after-scope
READ of size 8 thread T0
    #0 in std::basic_string<char, ...>::size xstring:2374
    #1 in main l3.cpp:10
```

That is the entire argument for the sanitizer requirement in one program: same source, same
compiler, one flag apart, and only one of the two runs tells the truth.

## Check yourself

<details><summary>Why did the temporary's destructor run <em>after</em> its length was printed?</summary>

Because a temporary lives until the end of the full-expression, and the call that printed the
length is part of that expression. The semicolon, not the end of the argument, is the boundary.

</details>

<details><summary>Does binding a temporary to a <code>const</code> reference always extend its life?</summary>

No. The extension applies where the reference is bound directly, and does not survive being
returned from a function or being stored in a reference member. Those two cases are the ones
that look identical and dangle.

</details>

<details><summary>A <code>string_view</code>'s owner is still alive. Can the view still dangle?</summary>

Yes. Growing the owning `std::string` may move its characters, and the view keeps pointing at
the old storage. The view refers to characters, not to the string object.

</details>

## Listings

1. `lifetime-and-the-dangling-reference-1.cpp` — when objects die, and the one extension.
2. `lifetime-and-the-dangling-reference-2.cpp` — returning by value, and the safe reference returns.
3. `lifetime-and-the-dangling-reference-3.cpp` — borrowing versus owning, and the valid window.
