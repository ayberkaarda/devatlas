## Why this exists

A function that collects things into a list is written once by everyone, and a great many
people write it with `def collect(item, into=[])`. It works in the first test, works in the
second, and then a report contains yesterday's rows. Nothing in the source looks wrong. The
cause is that the default value is a single object, created when the `def` statement runs,
and shared by every call that does not pass its own. Python 3.13's reference manual states
this directly under function definitions, and the Programming FAQ has a section devoted to
the surprise. It is worth an early lesson because the same mechanism produces three more
bugs: shared class attributes, closures over a loop variable, and caches that outlive their
usefulness.

## The idea

A default argument is a spare pen chained to the counter at a post office. The counter is set
up once, when the office opens. Everyone who does not bring their own pen uses that one — and
if the pen is the sort that keeps a record of what it wrote, everyone's writing accumulates
on it. Bring your own pen and the chained one is untouched.

### Where the analogy breaks

A pen wears out and gets replaced; the default object is replaced only when the module is
re-executed, which in a long-running process means never. More importantly, the analogy makes
the sharing sound like a property of the counter. It is a property of the *object*: an
immutable default such as `0`, `None` or `"utf-8"` is shared in exactly the same way and
causes no trouble at all, because there is no operation that could change it. The rule is not
"defaults are dangerous"; it is "a default that can be mutated is shared mutable state".

The picture also stops short of the interesting case. The same sharing is sometimes what you
want — a cache that survives between calls is a default dictionary used deliberately, and the
FAQ names that use. The problem is doing it by accident.

## How it works

Default expressions are evaluated once, at definition time, and stored on the function object.
They are visible: `func.__defaults__` is the tuple of positional defaults.

```python
def collect(item, into=[]):
    into.append(item)
    return into

collect("a")            # ['a']
collect("b")            # ['a', 'b']  -- the same list
collect.__defaults__    # (['a', 'b'],)
```

The fix is a sentinel. `None` is immutable, so sharing it costs nothing, and the fresh object
is built inside the body on every call that needs one.

```python
def collect(item, into=None):
    if into is None:
        into = []
    into.append(item)
    return into
```

The same shape appears on classes. An assignment in the class body creates one object owned
by the class; every instance that reads the attribute reads that object, and every instance
that mutates it mutates everybody's. Assigning the list in `__init__` gives each instance its
own.

```python
class Shared:
    entries = []            # one list for the whole class
class Own:
    def __init__(self):
        self.entries = []   # one list per instance
```

Closures are the third face of it. A lambda defined in a loop closes over the *variable*, not
over the value the variable had at the time, so all of them read the final value once the
loop is over. Binding through a default argument captures the value at definition.

```python
[lambda: n for n in "abc"]            # every one returns 'c'
[lambda bound=n: bound for n in "abc"]  # 'a', 'b', 'c'
```

## Common mistakes

**Believing the default is re-evaluated per call.** It is not, and `__defaults__` proves it:
the object callers keep receiving is the one stored on the function.

**Using `if not into:` instead of `if into is None:`.** An empty list the caller deliberately
passed is falsy, so it gets silently replaced and the caller's list never receives anything.

**Mutating a class-level list from an instance method.** `self.entries.append(x)` reads the
class attribute and mutates it; only `self.entries = [...]` creates an instance attribute.

**Registering handlers in a loop with a lambda.** Every handler ends up doing the last
iteration's work, and nothing raises — the calls succeed with the wrong value.

## Check yourself

<details><summary>Why is <code>into=None</code> safe when <code>into=[]</code> is not?</summary>

Both defaults are single shared objects. `None` has no operation that changes it, so sharing
is unobservable, and the body builds a new list per call.

</details>

<details><summary>When is a mutable default the right choice?</summary>

When the persistence is the point — a memo dictionary held on the function for caching. The
FAQ names this. It should be obvious from the name and a comment, not a surprise.

</details>

<details><summary>Two instances share a list. What does <code>a.entries = []</code> do?</summary>

It creates an instance attribute on `a` that shadows the class attribute. The class list and
every other instance are untouched, which is why the fix has to happen in `__init__`.

</details>

## Listings

1. `mutable-default-arguments-1.py` — the accumulating default, `__defaults__`, and the
   sentinel fix.
2. `mutable-default-arguments-2.py` — a class attribute shared by every instance, and what
   assigning to it does.
3. `mutable-default-arguments-3.py` — closures over a loop variable, two fixes, and a
   deliberate cache.
