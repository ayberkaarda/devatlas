## Why this exists

Most languages leave a declared variable in one of two unhappy states. Either it holds
whatever bytes were in that memory before, which is undefined behaviour waiting for a reader,
or it holds a null the type system did not warn you about. Go 1.27 removes the question: the
specification says every variable declared without an initial value is set to the zero value
for its type, and the zero value is defined for every type there is. A declaration is
therefore already a complete value. The second half of the same bargain is that Go never
converts one type into another behind your back — not `int32` into `int64`, not `float64`
into `int`, not a named type into the type it was defined from. Both halves exist for the same
reason: the value a line of code produces should be readable from that line.

## The idea

Declaring a variable in Go is like opening a bank account rather than inheriting one. The
moment it exists it has a defined balance of zero. Nobody has to tell you what the previous
holder left in it, and no statement is needed to make it safe to use.

### Where the analogy breaks

In three places, and each one is something a newcomer gets wrong.

A balance is one number. A zero value is per-type, and for the reference kinds — pointer,
slice, map, channel, function, interface — it is `nil`. `nil` is not uniformly usable: a nil
slice appends, reports `len` 0 and prints as `[]`, and a nil map reads fine and returns the
element type's zero value, but a write to a nil map panics at run time. The zero value is
always defined; it is not always writable.

An account has one currency. An untyped constant has none. `4096` and `1.0/3.0` carry
arbitrary precision until the place they are used gives them a type, which is why a constant
expression can be more accurate than any variable it is later stored in.

And a bank converts at the counter. Go does not. Assigning an `int32` to an `int64` is a
compile error until you write the conversion, and the same holds for two named types with the
same underlying type. `Celsius` and `Fahrenheit` can both be defined from `float64` and still
refuse to be added, which is the entire reason to define them separately.

## How it works

A `var` declaration with no initialiser produces the zero value. A short declaration with `:=`
infers the type from the value on the right. Structs zero recursively, so the zero `Config` is
a `Config` whose every field is that field's zero value:

```go
var count int           // 0
var name string         // ""
var labels []string     // nil, but len(labels) == 0 and append works
var lookup map[string]int
_ = lookup["absent"]    // reads the zero value; a write here would panic
```

Conversion is a call-shaped expression, `T(v)`, and it is required wherever the types differ.
Float to integer truncates towards zero rather than rounding; unsigned arithmetic is computed
modulo two to the power of the type's bit width, which the specification states outright, so a
program may rely on the wrap:

```go
var level uint8 = 250
level += 10             // 4, and the specification says so
fmt.Println(int(-9.7))  // -9, truncated towards zero
```

Struct values are values. Assigning one copies every field, passing one to a function copies
it again, and `==` compares field by field when every field is comparable — which is also what
lets a struct be a map key.

## Common mistakes

**Expecting a widening conversion to be automatic.** It is not, and the message names both
types:

```text
.\widening.go:7:19: cannot use small (variable of type int32) as int64 value in variable declaration
```

**Giving an integer a fractional constant.** The compiler says what it would have had to do:

```text
.\truncated-constant.go:6:14: cannot use 3.5 (untyped float constant) as int value in variable declaration (truncated)
```

**Leaving a variable unused.** In Go this is an error, not a warning:

```text
.\unused.go:7:2: declared and not used: count
```

**Mixing two named types with the same underlying type.**

```text
.\mismatched-named-types.go:12:14: invalid operation: c + f (mismatched types Celsius and Fahrenheit)
```

**Writing to a nil map.** The one zero value that is readable but not writable:

```text
panic: assignment to entry in nil map
```

## Check yourself

<details><summary>Why does <code>len</code> work on a nil slice but a write fail on a nil map?</summary>

A nil slice is a header with a nil pointer, length 0 and capacity 0, so `len` and `cap` read
the header and `append` allocates a backing array. A nil map has no hash table at all, so
there is nowhere to put an entry; reads answer from the zero value instead of touching one.

</details>

<details><summary>Two types are both defined from <code>float64</code>. Can one be assigned to the other?</summary>

No. They are distinct types, so a conversion has to be written. The conversion is free at run
time — it reinterprets nothing — but writing it is what stops a temperature in one scale being
used as a temperature in the other.

</details>

<details><summary>Is <code>int(9.7)</code> 10?</summary>

No, it is 9. Conversion from a floating-point type to an integer type truncates towards zero,
so `int(-9.7)` is `-9` rather than `-10`.

</details>

## Listings

1. `types-zero-values-and-no-surprises-1.go` — the zero value of every kind of type.
2. `types-zero-values-and-no-surprises-2.go` — conversions, untyped constants, wrap-around.
3. `types-zero-values-and-no-surprises-3.go` — named types, struct copies and comparability.
