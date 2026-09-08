## Why this exists

An exception is the only part of a program that gets read while somebody is under pressure.
`ValueError: invalid literal for int() with base 10: ''` at least says what happened;
`Exception("error")` says nothing, and a `try` block that ends in `except: pass` says less
than nothing, because it removes the evidence that anything happened at all. Python 3.13's
exception hierarchy is designed to be caught by branch rather than by leaf, and its chaining
rules keep the original failure attached to the one you raise in its place. Both features are
free, and both are routinely thrown away.

## The idea

An exception hierarchy is a returns desk. A customer arrives with a specific complaint —
wrong size, damaged in transit, never delivered — and the desk is arranged so that a clerk
can handle exactly the complaints they are trained for and pass the rest up. A clerk who says
"I will take anything" and then quietly bins whatever they cannot fix is worse than no desk.

### Where the analogy breaks

A returns desk deals with one customer at a time and Python's does not stop at the desk: an
unhandled exception unwinds the whole call stack, running `finally` blocks and context
manager exits on the way, and ends the program if nothing catches it. That unwinding is the
useful behaviour, not a failure mode, and it is why letting an exception through is often the
correct action.

The picture also hides `BaseException`. `SystemExit` and `KeyboardInterrupt` are not
complaints from a customer; they are the building being closed. They deliberately sit outside
`Exception` so that `except Exception` does not catch them — and a bare `except:` does,
which is how a program becomes impossible to stop with Ctrl-C. Listing 2 catches a
`SystemExit` with a bare `except` and carries on as if nothing had been asked of it.

## How it works

Give a subsystem one root exception and derive the specific ones from it. Callers then choose
their level: catch `InvalidQuantity` to handle one case, catch `OrderError` to handle the
subsystem, catch nothing and let it out.

```python
class OrderError(Exception): ...
class OrderNotFound(OrderError): ...
class InvalidQuantity(OrderError): ...
```

An exception is an object, so it can carry the facts a handler needs rather than only a
sentence. Setting attributes in `__init__` means the caller does not have to parse the
message.

```python
class InvalidQuantity(OrderError):
    def __init__(self, field, value, limit):
        super().__init__(f"{field} must be between 1 and {limit}, got {value}")
        self.field, self.value, self.limit = field, value, limit
```

`except` clauses are tried in order and the first matching one wins, so specific branches go
above general ones. Catching a base class catches every subclass — `except LookupError`
catches `KeyError` and `IndexError`.

When you translate a low-level failure into your own, `raise ... from` records the original
as `__cause__`, and it stays reachable on the exception object.

```python
try:
    return int(raw)
except ValueError as exc:
    raise ConfigError(f"port must be a whole number, got {raw!r}") from exc
```

Raising inside an `except` block without `from` still sets `__context__` automatically, so the
original is not lost — but `__cause__` is what says *this is why*, and `from None` suppresses
the display of the context when the original is genuinely noise.

## Common mistakes

**`except: pass`.** It catches `KeyboardInterrupt`, `SystemExit` and every typo in the
protected block. Listing 2 has a mistyped dictionary key inside one; the function returns `0`
instead of `22` and reports nothing.

**`except Exception` where one type was meant.** A `KeyError` from a bug in the handler's own
code is then indistinguishable from the expected failure.

**Raising a bare `Exception`.** Callers cannot catch it without catching everything, so the
only options they have are to swallow all failures or none.

**Swallowing and returning a default.** A function that answers `0` when it could not compute
an answer moves the failure to a report nobody will connect to this code.

## Check yourself

<details><summary>Why does <code>except Exception</code> not catch <code>SystemExit</code>?</summary>

`SystemExit` derives from `BaseException`, not `Exception`, precisely so that broad handlers
do not intercept an orderly shutdown. `KeyboardInterrupt` and `GeneratorExit` sit there for
the same reason.

</details>

<details><summary>What is the difference between <code>__cause__</code> and <code>__context__</code>?</summary>

`__context__` is set automatically when one exception is raised while another is being
handled. `__cause__` is set only by `raise ... from`, and states deliberately that the first
caused the second; setting it also marks the context as suppressed for display.

</details>

<details><summary>When should a function catch nothing at all?</summary>

When it cannot do anything useful about the failure. Catching to re-raise unchanged adds a
frame and no information; letting it propagate to a layer that can log, retry or answer the
user is the whole point of the hierarchy.

</details>

## Listings

1. `errors-that-say-something-1.py` — one root per subsystem, and exceptions carrying
   structured data.
2. `errors-that-say-something-2.py` — the bare `except` hiding a typo and swallowing
   `SystemExit`.
3. `errors-that-say-something-3.py` — `raise ... from`, `from None`, and the implicit context.
