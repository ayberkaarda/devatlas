## Why this exists

Every resource a program takes has to be given back: a file handle, a database connection, a
lock, a temporary directory, a changed working directory. Writing `close()` after the work is
enough right up until the work raises, and then the close never runs. Wrapping everything in
`try`/`finally` is correct and, repeated fifty times, is also where the one forgotten
`finally` hides. Python 3.13 defines the `with` statement so that acquisition and release are
declared once, together, by the object that owns them — and so that release happens on every
exit, including the ones nobody wrote code for.

## The idea

A context manager is an airlock. Entering runs a procedure, and leaving runs another one; the
outward procedure runs whether you walked out, were carried out, or the alarm went off. You
do not get to skip it by leaving in a hurry, and you do not have to remember it, because it
is part of the door rather than part of your plan.

### Where the analogy breaks

An airlock is symmetric and a context manager is not. `__enter__` returns a value that the
`as` clause binds — often the resource, sometimes something else entirely, sometimes nothing
— while `__exit__` receives three arguments describing the exception that is on its way out,
or three `None`s if there is not one. That asymmetry is the interesting part: `__exit__` can
inspect the failure and decide.

And the door can be rigged. If `__exit__` returns a true value the exception is suppressed,
execution continues after the block, and the caller never learns anything went wrong. An
airlock cannot quietly cancel the emergency. This is a real footgun: returning something
truthy by accident — a non-empty string, a count — turns a manager into a swallower, and
listing 1 shows a block whose body raised `KeyError` and whose caller carried on.

## How it works

`with expr as name:` calls `type(expr).__enter__` and binds its result, runs the body, and
then calls `__exit__` with the exception type, value and traceback — or three `None`s.

```python
class Trace:
    def __enter__(self):
        return self
    def __exit__(self, exc_type, exc_value, tb):
        return False        # do not suppress
```

Nested managers exit in reverse order, and a single `with` taking several managers behaves
the same way, which matters when one resource depends on another.

For a function-shaped resource, `contextlib.contextmanager` turns a generator into a manager:
everything before `yield` is setup, the yielded value is what `as` binds, and everything
after is cleanup. The cleanup only runs on failure if it is inside `finally` — an exception
in the body is thrown in at the `yield`, and an unguarded generator simply stops there.

```python
@contextlib.contextmanager
def guarded(name):
    acquire(name)
    try:
        yield name
    finally:
        release(name)       # runs on both paths
```

When the number of resources is only known at run time, `contextlib.ExitStack` collects them
and unwinds whatever was entered, in reverse, even if a later acquisition fails.

```python
with contextlib.ExitStack() as stack:
    handles = [stack.enter_context(open_one(n)) for n in names]
```

`contextlib.suppress(SomeError)` is the deliberate, narrow version of a swallowing
`__exit__`: it ignores exactly the listed exception types and lets everything else through.

## Common mistakes

**Cleanup after `yield` without `try`/`finally`.** The body raises, the exception is thrown in
at the `yield`, and the lines after it never execute. Listing 2 runs both versions and shows
one resource released and the other not.

**Returning a truthy value from `__exit__` by accident.** The exception disappears. `__exit__`
should end with `return False` or with no return statement at all.

**Assuming `__enter__` returns the manager.** It returns whatever it returns.
`open()` yields a file, but `contextlib.suppress` yields `None`, and code that binds it with
`as` and then uses it gets an `AttributeError`.

**Acquiring in a loop without a stack.** A failure partway through leaves the earlier
resources open. `ExitStack` exists for exactly that shape.

## Check yourself

<details><summary>What does <code>__exit__</code> receive when the block ends normally?</summary>

Three `None` values, for the exception type, the exception and the traceback. A manager that
wants to behave differently on failure tests whether the first argument is `None`.

</details>

<details><summary>Why must cleanup in a <code>@contextmanager</code> generator sit in <code>finally</code>?</summary>

An exception from the body is raised at the `yield`. Without `finally` it propagates out of
the generator immediately and the cleanup lines are never reached.

</details>

<details><summary>When would you want <code>__exit__</code> to return <code>True</code>?</summary>

Rarely, and only for a narrowly named manager whose whole purpose is to ignore one kind of
failure — which `contextlib.suppress` already provides. A general-purpose manager that
suppresses hides bugs from its callers.

</details>

## Listings

1. `context-managers-1.py` — enter and exit ordering, the exception passed to `__exit__`, and
   a manager that swallows.
2. `context-managers-2.py` — `@contextmanager` with and without the guard, counted.
3. `context-managers-3.py` — `ExitStack` for a run-time number of resources, and `suppress`.
