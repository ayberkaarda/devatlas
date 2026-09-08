## Why this exists

Data rarely arrives in the shape a program needs it in: a form field is text even when it represents a number, a database row returns a decimal where a display wants a string, and a calculation needs a float where user input gave an integer. Python refuses to guess how to combine mismatched types automatically, which is a deliberate design choice rather than an oversight. Understanding conversion functions, and understanding why the language stops you at the border between types, turns a confusing `TypeError` into a predictable, one-line fix.

## The idea

Treat each type as its own currency: dollars, euros, and yen cannot be added together directly no matter how tempting the arithmetic looks, because the numbers alone do not carry enough meaning. A currency exchange counter converts one into the other explicitly, at a known rate, before the addition makes sense. `int()`, `str()`, and `float()` are that exchange counter for Python values: they take a value in one type and hand back an equivalent value in another, on request, rather than the language silently picking an exchange rate on your behalf.

### Where the analogy breaks

Currency exchange always loses a little value to fees and rounding, but converting between compatible Python types is often exact and lossless, such as turning the integer `42` into the string `"42"` and back again. The analogy also implies every conversion is possible if you just pick the right counter, while Python conversions can fail outright: turning the text `"forty-two"` into an integer has no sensible exchange rate at all, and the attempt raises an error rather than returning a guessed value.

## How it works

Concatenating a string and a number with `+` raises an error, because `+` between a `str` and an `int` is not defined; the fix is an explicit conversion on one side.

```python
count = 3
line = str(count) + " items"
print(line)
```

The reverse direction works the same way: `int()` turns digit text into a whole number, and `float()` turns text or an integer into a decimal number.

```python
raw = "42"
total = int(raw) + 8
print(total)
```

TypeScript makes the same operation explicit with `String()` and `Number()`, which is useful for comparison: neither language performs this conversion for you inside an arithmetic or concatenation expression without you asking.

## Common mistakes

Writing `"Total: " + 5` raises `TypeError: can only concatenate str (not "int") to str`, naming the exact mismatch so the fix is obvious once you read the message instead of guessing at it. A subtler mistake is calling `int()` on text that is not a clean whole number, such as `int("3.5")`, which raises `ValueError: invalid literal for int() with base 10: '3.5'`; the fix is usually `int(float("3.5"))` if truncation is intended, since `int()` never parses a decimal point.

## Check yourself

**1. What happens when you run `str(5) + str(3)`?**

<details><summary>Answer</summary>

It produces the string `"53"`, because both operands are converted to strings first and `+` between two strings concatenates their text rather than adding numbers.

</details>

**2. Why does `int("42")` succeed but `int("42.0")` fail?**

<details><summary>Answer</summary>

`int()` parses text that represents a whole number using only digits and an optional sign. A decimal point is not part of that grammar, so `int()` raises `ValueError` instead of silently truncating.

</details>

## Full listings

The Python listing builds a display string from a number and parses a numeric string back into an integer for arithmetic. The TypeScript listing performs the identical two conversions using `String()` and `Number()`, showing that explicit conversion is not a Python-specific requirement.
