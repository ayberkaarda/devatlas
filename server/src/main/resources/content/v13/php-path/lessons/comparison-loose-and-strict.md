## Why this exists

`==` is the operator PHP is best known for and worst remembered for. Almost every warning a reader
has absorbed about it describes PHP 5 or PHP 7, where `0 == 'foo'` was true because the string was
converted to a number and a non-numeric string became `0`. PHP 8.0 changed that rule, and in
PHP 8.2 the same expression is false.

That cuts both ways. The old advice — never use `==` — was aimed at a hazard that has been reduced,
and repeating it unexamined teaches the wrong reason. The hazards that remain are different ones,
and they hide in library functions and language constructs rather than in the operator you can see.

## The idea

Comparison is a translator between two people who speak different languages. `===` refuses to
translate: if the two are not already speaking the same language, the answer is no. `==` translates
first and compares afterwards — and the interesting question is always which of the two was
translated into the other's language.

### Where the analogy breaks

A translator picks a direction from context; `==` picks one from a table, and the table changed
under PHP 8.0. When a number meets a non-numeric string, PHP 8.2 turns the **number into a string**
rather than the string into a number, so `0 == 'foo'` is false. When a number meets a *numeric*
string, the string still becomes a number, so `100 == '1e2'` is true and `'1' == '01'` is true.

The analogy also implies two participants and one comparison. In practice the comparison you did
not write is the one that bites: `in_array()` and `array_search()` compare loosely unless told
otherwise, and `switch` compares loosely with no way to tell it otherwise. `match`, added in
PHP 8.0, compares identically, which is the main reason to prefer it.

## How it works

The rule for PHP 8.2 fits in one sentence: a number compared with a non-numeric string is compared
as a string, and a number compared with a numeric string is compared as a number. Everything else
follows.

```php
0 == 'foo';      // false  (true in PHP 7)
0 == '0';        // true
'1' == '01';     // true   — both are numeric strings
100 == '1e2';    // true
null == false;   // true
'0' == false;    // true
```

`===` answers false to every one of those lines. It requires the same type before it compares
anything, so `'1' === '01'` and `0 === '0'` and `null === false` are all false.

The hidden comparisons matter more. `in_array($needle, $haystack)` uses `==`; the third argument
makes it use `===`. `array_search()` and `array_keys()` take the same flag. And `array_search()`
returns `0` for a hit at the first position, which is falsy, so its result has to be checked with
`!== false` rather than with a truthiness test:

```
int(0)
truthy test says: not found
identity test says: found
```

`switch` has no flag at all. A `switch ('1')` takes the `case 1:` branch. `match` does not:

```
switch: took the int 1 branch
match(true): took the string branch
UnhandledMatchError: Unhandled match case '1'
```

That last line is a feature. `match` with no arm and no `default` throws rather than falling
through silently.

For ordering, `<=>` returns `-1`, `0` or `1`, and PHP 8.0 made all of PHP's sorts stable, so equal
elements keep their input order. That is a specified guarantee, which means a sorted result is
reproducible and safe to assert on.

## Common mistakes

**Testing `array_search()` for truth.** A match at index `0` reads as a miss. Use `!== false`.

**Leaving the strict flag off `in_array()`.** With PHP 8.2's rules `in_array(0, ['a','b'])` is
already false, so the old catastrophic case is gone — but `in_array('1', [1,2,3])` is still true,
and that is enough to let a string through a check meant for integers.

**Reaching for `switch` on user input.** A string from a request will match an integer `case`.

**Comparing floats with `==`.** `0.1 + 0.2 == 0.3` is false. Compare a rounded value or a
difference against a tolerance; and because `precision` is an ini setting, print the comparison
rather than the digits.

## Check yourself

<details><summary>Why is <code>0 == 'foo'</code> false in PHP 8.2 but true in PHP 7?</summary>

PHP 8.0 changed the direction of the conversion. A non-numeric string no longer becomes `0`; the
number becomes a string, and `'0' == 'foo'` is false.

</details>

<details><summary><code>'1' == '01'</code> is true. Both sides are strings — why is anything converted?</summary>

Both are numeric strings, and PHP compares two numeric strings numerically. `'1' === '01'` is
false, and so is `'1' == '01a'`.

</details>

<details><summary>When is a printed sort order acceptable as expected output?</summary>

When the ordering is specified. PHP's sorts have been stable since PHP 8.0, so the position of
equal elements is defined rather than accidental.

</details>

## Listings

1. `comparison-loose-and-strict-1.php` — a table of pairs under `==` and `===`, including the
   pairs whose answer changed in PHP 8.0.
2. `comparison-loose-and-strict-2.php` — the comparisons you did not write: `in_array()`,
   `array_search()`, `array_keys()`, `switch` and `match`.
3. `comparison-loose-and-strict-3.php` — `<=>`, stable sorting, and the float comparison that
   should never be an equality test.
