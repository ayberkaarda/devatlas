## Why this exists

PHP has three separate ways for something to go wrong and they are easy to confuse. An
**exception** unwinds the stack and can be caught. A **diagnostic** — a warning, a notice, a
deprecation — prints something and lets execution continue with whatever value the operation
produced. A **fatal error** stops the script and no `catch` block sees it.

PHP 8.0 moved a great many failures from the second category into the first: calling an undefined
function, dividing by zero, calling a method on `null` and passing an out-of-range argument all
throw in PHP 8.2 where they once produced a diagnostic and carried on. A reader who learned the old
behaviour will write defensive code for warnings that no longer happen, and no handling at all for
the throws that replaced them.

## The idea

Think of a kitchen. An exception is a dish sent back: the chef is told, and can remake it or refuse
the order. A diagnostic is a note pinned to the wall — service continues, and whether anyone reads
the note is a separate question. A fatal error is the gas being cut off.

### Where the analogy breaks

The dish that comes back has two different tickets on it and only one of them is the one you are
watching for. `Throwable` is the root; below it sit `Exception` and `Error`, and they are siblings
rather than parent and child. `catch (Exception $e)` does not see a `TypeError`, a `ValueError`, a
`DivisionByZeroError` or an `ArgumentCountError`, because every one of those is on the `Error`
branch. That is the single most common way a PHP 8.2 failure escapes a handler that looks complete.

The note on the wall is also not passive. `set_error_handler()` receives diagnostics and can throw
from inside the callback, which converts the whole second category into the first — usually as an
`ErrorException`. And the gas being cut off still leaves one thing running:
`register_shutdown_function()` fires after a fatal error, and `error_get_last()` tells it what
happened. That is the only hook there is.

## How it works

The hierarchy decides which `catch` runs:

```php
try {
    intdiv(1, 0);
} catch (Exception $e) {
    // never reached: DivisionByZeroError is an Error, not an Exception
}
```

Catching `Throwable` catches both branches. A union catch — `catch (TypeError | ValueError $e)` —
narrows to two related failures without reaching for the root.

`finally` runs on every path out of the block, and a `return` inside it replaces whatever the
`try` was returning *and* discards an exception that was on its way out:

```
from try / from finally / finally won
```

The third of those is a function whose `try` block threw and whose `finally` returned. Nothing was
reported. That is a deliberate demonstration and never a thing to write.

Chaining preserves the cause. Passing the original as the third constructor argument keeps it
reachable through `getPrevious()`:

```
ImportFailed: row 7 of amounts.csv could not be imported
  caused by InvalidArgumentException: not an integer: 12x
```

Diagnostics stay outside all of that until you move them. A missing array key produces a warning
and the expression still evaluates to `null`; `??` avoids the warning entirely. Installing a
handler that throws makes it behave like everything else:

```
handler saw Warning: Undefined array key "name"
the expression still produced NULL
promoted to ErrorException: A non-numeric value encountered
```

And then there is the category no handler intercepts:

```
about to raise something no catch block can see
shutdown: fatal=true message=the queue driver is gone
```

The `try`/`catch` around that call did not run, and neither did the line after it.

## Common mistakes

**Catching `Exception` and believing it is everything.** In PHP 8.2 most of what a wrong call
produces is an `Error`. Catch `Throwable` at the boundary, or name the specific classes.

**Returning from `finally`.** It silently discards a pending exception. A `finally` block is for
releasing something, not for producing a value.

**Rethrowing without `$previous`.** The new exception replaces the old one entirely and the
original message and stack are gone.

**Naming a custom exception property `$file`, `$line`, `$message` or `$code`.** `Exception`
declares all four, and redeclaring one is a fatal error at class-declaration time:

```
Fatal error: Cannot redeclare non-readonly property Exception::$file as readonly ImportFailed::$file
```

**Assuming a warning can be caught.** It cannot, until a handler is installed that throws.

## Check yourself

<details><summary><code>catch (Exception $e)</code> around <code>1 / 0</code>. What happens?</summary>

Nothing catches it. `DivisionByZeroError` extends `ArithmeticError` extends `Error`, and `Error`
is not an `Exception`. Catching `Throwable`, `Error`, `ArithmeticError` or `DivisionByZeroError`
all work; catching `Exception` does not.

</details>

<details><summary>How do you find out that a fatal error happened?</summary>

`register_shutdown_function()` plus `error_get_last()`. The shutdown callback runs after the fatal,
and the array it reads carries the type and message.

</details>

<details><summary>What is the cost of promoting every warning to an <code>ErrorException</code>?</summary>

Code that relied on continuing past a diagnostic now stops. That is usually what you want in an
application, and usually not what you want inside a third-party library you did not write.

</details>

## Listings

1. `exceptions-errors-and-what-is-catchable-1.php` — the hierarchy, and which class each common
   failure actually throws in PHP 8.2.
2. `exceptions-errors-and-what-is-catchable-2.php` — `finally`, the `return` that swallows, and
   chaining through `getPrevious()`.
3. `exceptions-errors-and-what-is-catchable-3.php` — diagnostics, promoting them with
   `set_error_handler()`, and the fatal error only a shutdown function observes.
