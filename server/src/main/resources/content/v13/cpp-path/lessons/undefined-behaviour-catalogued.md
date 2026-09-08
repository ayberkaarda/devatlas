## Why this exists

Undefined behaviour is not a category of error. It is the standard declining to say what
happens, which leaves the compiler free to assume it does not occur — and to optimise on that
assumption. That is why the usual instinct, "run it and see", fails here more thoroughly than
anywhere else in this corpus: a program with undefined behaviour can compile without a
diagnostic, run, print exactly what you expected, and be wrong, with the failure surfacing on
another machine, another compiler, or the same compiler at a different optimisation level.
This lesson names the common kinds, says which tool detects each, and — for every one — shows
the defined way to write it instead.

## The idea

Undefined behaviour is a voided warranty. You broke a condition the manufacturer stated, and
they owe you nothing.

### Where the analogy breaks

A voided warranty is discovered when you take the item back to the shop. Undefined behaviour
is acted upon at build time: the compiler may assume the condition holds, and reason from
that. A bounds check written after the read that already broke the rule can be removed as
unreachable, so the damage lands before anything has failed.

A warranty covers one item, too. Undefined behaviour is not local to the line that caused it.
The standard places no bound on the consequences, and they may appear earlier in the output
than the offending statement, in a different function, or as no symptom at all until the code
around it changes.

And a void warranty at least yields a refusal. Undefined behaviour usually yields the answer
you were expecting. Every diagnostic quoted below came from a program that ran to completion
and printed something plausible.

## How it works

**Signed overflow.** `INT_MAX + 1` is undefined; unsigned overflow is specified to wrap
modulo two to the power of the width. The defined approaches are to ask before adding, to
widen first, or to use an unsigned type where wrapping is what you mean:

```cpp
if (right > 0 && left > std::numeric_limits<std::int32_t>::max() - right) { return true; }
```

**Shift counts.** A shift by an amount not less than the width of the promoted left operand is
undefined. The compiler catches the constant case; the sanitizer catches the rest.

**Uninitialised reads.** A scalar with automatic storage duration and no initialiser holds an
indeterminate value, and reading it is undefined. Value-initialisation — `int x{}`, `Raw r{}`,
`std::vector<int> v(4)` — leaves nothing unset, and a default member initialiser applies to
every constructor a class will ever gain.

**Out-of-bounds access.** Indexing past the end of an array or a container is undefined.
`at()` checks against `size()` and throws; `std::span` carries the length with the pointer so
a callee cannot lose it; `std::ssize` gives a signed length so counting down does not depend
on an unsigned subtraction staying positive.

**Reading an object through a pointer of an unrelated type.** Undefined, and the usual reason
people try is to inspect the bytes of a value. `std::bit_cast` does that by copying the bytes
into a new object of the target type:

```cpp
const auto bits = std::bit_cast<std::uint32_t>(1.0F);   // defined
```

The detection tools are not interchangeable. The address sanitizer knows about memory that
has been freed or that lies outside an allocation. The undefined-behaviour sanitizer knows
about arithmetic, alignment, bad casts and null. Neither detects a read of an uninitialised
variable — that needs the compiler's flow analysis, which catches the straightforward cases,
or a third sanitizer this track does not use. Nor did either detect one case measured for the
previous lesson: an index inside a vector's `capacity()` but past its `size()` is undefined,
and the read lands inside a real allocation, so a heap sanitizer has nothing to object to.

## Common mistakes

**Overflowing a signed integer.** No compile-time diagnostic for a runtime value; the
sanitized build is precise:

```text
d2.cpp:2:34: runtime error: signed integer overflow: 2147483647 + 1 cannot be represented
in type 'int'
SUMMARY: UndefinedBehaviorSanitizer: undefined-behavior
```

**Shifting too far.** Caught twice over when the count is a constant:

```text
d2.cpp:7:27: warning: shift count >= width of type [-Wshift-count-overflow]
d2.cpp:7:27: runtime error: shift exponent 40 is too large for 32-bit type 'int'
```

**Reading a variable before setting it.** The compiler's own analysis, and an error under
`-Werror`:

```text
u2.cpp:4:35: error: variable 'total' is uninitialized when used here
[-Werror,-Wuninitialized]
u2.cpp:3:14: note: initialize the variable 'total' to silence this warning
```

**Indexing past the end of an array.** Both sanitizers speak, and they are describing the same
read from different angles:

```text
u4.cpp:5:25: runtime error: index 5 out of bounds for type 'int[4]'
ERROR: AddressSanitizer: stack-buffer-overflow
READ of size 4 thread T0
    #0 in main u4.cpp:5
```

**Reading through a misaligned pointer.** The value printed was `1`, which is what the author
would have expected and no evidence of anything:

```text
u3.cpp:7:25: runtime error: load of misaligned address for type 'const int', which
requires 4 byte alignment
SUMMARY: UndefinedBehaviorSanitizer: undefined-behavior
```

Addresses, thread numbers and machine paths are stripped from these quotations, and frame
lists are cut after the first entry.

## Check yourself

<details><summary>Why is "it printed the right answer" not evidence in C++?</summary>

Because undefined behaviour is permitted to produce the expected result. The standard places
no requirement on what happens, so a correct-looking run tells you about one build on one
machine and nothing about the program.

</details>

<details><summary>Which of the two sanitizers finds a read of an uninitialised local?</summary>

Neither. That is the compiler's flow analysis, which catches the clear cases at build time, or
a separate memory sanitizer. Knowing which tool covers which kind is part of using them.

</details>

<details><summary>What is the defined way to inspect the bytes of a <code>float</code>?</summary>

`std::bit_cast<std::uint32_t>(value)`, which copies the bytes into a new object of the target
type. Reading the float through a `std::uint32_t*` is undefined however plausible the result
looks.

</details>

## Listings

1. `undefined-behaviour-catalogued-1.cpp` — arithmetic that stays defined.
2. `undefined-behaviour-catalogued-2.cpp` — initialisation that leaves nothing indeterminate.
3. `undefined-behaviour-catalogued-3.cpp` — bounds that travel with the data, and a defined pun.
