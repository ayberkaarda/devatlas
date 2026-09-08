## Why this exists

Most of what circulates about PHP describes a language with no type declarations at all, where a
function took whatever it was handed and worked it out later. That language is gone. PHP 7.0 added
scalar type declarations and the `declare(strict_types=1)` directive, 7.4 added typed properties,
8.0 added union types, 8.1 added `never` and readonly properties, and 8.2 added readonly classes,
disjunctive normal form types and the standalone `true`, `false` and `null` types. In PHP 8.2 a
declaration is not a comment: the engine checks it on every call, every return and every property
assignment, and it throws when the check fails.

What is left to decide is how a mismatch is handled, and PHP 8.2 offers two answers in the same
runtime. That choice is the subject of this lesson, because half of what the rest of this track
teaches behaves differently depending on which one is in force.

## The idea

A type declaration is a passport check at a border. Strict mode is the officer who accepts exactly
one document and turns everyone else away. Coercive mode is the officer who will take a driving
licence, issue a temporary substitute at the desk, and refuse only when the traveller is carrying
nothing that could be made into a passport at all.

### Where the analogy breaks

Three ways. First, the border does not decide the rules — the traveller's own paperwork does.
`declare(strict_types=1)` applies to the file in which the **call is written**, not the file that
declares the function. A strict file calling into a library written without the directive gets
strict checking; a coercive file calling into a strict library gets coercion. Second, strict mode
is not free of conversion: `int` is still accepted where `float` is declared, because every `int`
has an exact `float` value. That single widening is deliberate and nothing else survives. Third,
the traveller is not modified. Coercion produces a new value for the parameter; the caller's
variable keeps whatever it had.

The analogy also stops at the door, and the check does not. A typed property is checked on every
assignment for the lifetime of the object, which is why an uninitialised typed property is an
error rather than `null`.

## How it works

Under `declare(strict_types=1)`, a `string` where an `int` is declared is a `TypeError`:

```php
declare(strict_types=1);
function halve(int $count): float { return $count / 2; }
halve('8');   // TypeError
halve(8);     // float(4)
area(3);      // int -> float is the one widening strict mode still performs
```

Without the directive, the same call converts, and the conversions are narrower than they used to
be. `'8 apples'` is not accepted by an `int` parameter in PHP 8.2 even though `'8 apples' + 1` is
still `9` with a warning — parameter coercion and arithmetic are separate rules. A float string
with a fractional part converts but says so:

```
Deprecated: Implicit conversion from float-string "8.5" to int loses precision
'8.5'    -> 8
'0x1A'   -> TypeError
'eight'  -> TypeError
```

Properties carry the same declarations, and PHP 8.2 lets a whole class be marked `readonly` so
every declared property is write-once.

```php
readonly class Money
{
    public function __construct(public int $amount, public string $currency) {}
}
```

## Common mistakes

**Expecting `strict_types` to change `==`.** It does not. The directive governs parameter, return
and property coercion. Comparison operators keep their own rules in every file, which is the
subject of the third lesson in this module.

**Assuming a nullable parameter is optional.** `?string $x` accepts `null`; it does not supply one.

```
ArgumentCountError: Too few arguments to function f(), 0 passed and exactly 1 expected
```

**Reading a typed property before assigning it.** There is no implicit `null`:

```
Error: Typed property Draft::$title must not be accessed before initialization
```

**Putting the directive in the file that declares the function and expecting callers to obey it.**
Strictness is chosen per calling file, so a library cannot impose it on the code that uses it.

## Check yourself

<details><summary>A strict file calls a function declared in a file without the directive. Which mode applies?</summary>

Strict. The mode is a property of the file containing the call. The reverse also holds: a coercive
file calling a function declared in a strict file gets coercion.

</details>

<details><summary>Why is <code>area(3)</code> accepted when <code>area</code> declares <code>float</code> and strict mode is on?</summary>

`int` to `float` is the single widening strict mode permits, because every `int` has an exact
`float` value. `float` to `int` is not permitted, and neither is `string` to anything.

</details>

<details><summary>Coercive mode converts <code>'8'</code> to <code>8</code>. What does it do with <code>'8 apples'</code>?</summary>

It throws a `TypeError` in PHP 8.2. Arithmetic is more forgiving than parameter coercion:
`'8 apples' + 1` produces `9` with a warning.

</details>

## Listings

1. `types-declarations-and-strict-types-1.php` — strict mode: the widening that is allowed, the
   conversions that are not, and the messages PHP produces.
2. `types-declarations-and-strict-types-2.php` — the same function without the directive, over a
   table of inputs, including the two deprecation messages.
3. `types-declarations-and-strict-types-3.php` — typed properties, uninitialised state, and a
   readonly class.
