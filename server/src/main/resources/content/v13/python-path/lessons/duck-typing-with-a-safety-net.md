## Why this exists

Python decides at run time whether an operation is possible, by trying it. That is why a
function written for a file works on anything with a `read` method, and it is why a function
written for a string fails halfway through a batch job when one row holds an integer. Type
hints were added so the second case can be found before the program runs. What they are not
is a run-time check: Python 3.13 stores annotations and hands them to whoever asks, and the
interpreter itself never consults them. Knowing which half of that sentence applies to your
situation is the whole lesson, because a team that believes hints are enforced will stop
validating input.

## The idea

A type hint is a label on a jar in a shared kitchen. The label says "sugar". It helps
everyone who reads it, an inspector can walk the shelves and find a jar labelled sugar that
was filled from the salt bag, and none of that stops you putting salt in it. The jar does not
resist.

### Where the analogy breaks

A label is a static thing and annotations are code that produces a value. They can be read,
computed over, and turned into a check you write yourself — `typing.get_type_hints` resolves
them to real objects, and a decorator can compare an argument against them and refuse.
Listing 2 does exactly that in twenty lines. So it is closer to a label a robot in the
kitchen *could* be told to enforce, if you hire the robot.

The picture also undersells the inspector. A type checker is not glancing at labels; it
follows values through the program and reports where a jar labelled sugar could receive salt,
including paths that this particular run never took. That is coverage no run-time check
gives. And it undersells duck typing in the other direction: `Protocol` lets a hint describe
a shape rather than a class, so a type can satisfy an interface it has never heard of — which
is the thing dynamic typing was good at, written down.

## How it works

Annotations are stored, not applied. The function runs whatever it was given.

```python
def normalise(code: str) -> str:
    return code

normalise(42)        # returns 42; nothing complains
```

The failure arrives later, at the first operation the value cannot do — which is usually a
long way from the annotation that was wrong.

```python
normalise(42).lower()   # AttributeError: 'int' object has no attribute 'lower'
```

`typing.get_type_hints` reads the annotations back and resolves them to objects, so a check
can be built from the function's own declaration rather than repeated by hand.

```python
hints = typing.get_type_hints(func)      # {'code': str, 'return': str}
isinstance(value, hints["code"])         # a check you chose to run
```

`Protocol` describes required members instead of a base class. With `@runtime_checkable`,
`isinstance` will test the shape: a class that never mentions the protocol satisfies it by
having the right methods or attributes.

```python
@runtime_checkable
class Closeable(Protocol):
    def close(self) -> None: ...

isinstance(Socket(), Closeable)     # True; Closeable is not in Socket.__mro__
```

The division of labour is then clear. A static checker reads the hints and finds the mistakes
that are visible in the source. Explicit validation at the boundary — where data arrives from
a file, a socket or a user — finds the ones that only exist at run time. Hints do not replace
that validation, and listing 1 shows why.

## Common mistakes

**Treating a hint as validation.** No exception is raised at the call. The value flows on and
fails somewhere else, or worse, does not fail: `add("x", "y")` with both parameters annotated
`int` returns `"xy"`.

**Calling `isinstance` against a protocol that is not `@runtime_checkable`.** It raises
`TypeError`; the decorator is what permits the check.

**Believing a `@runtime_checkable` check is thorough.** It tests that the members exist, not
their signatures or their types. A `close` taking three required arguments still satisfies
`Closeable`.

**Skipping validation at the boundary because the function is annotated.** Data from outside
the program has whatever type it has. The annotation is a claim about what the code expects,
not a filter on what it receives.

## Check yourself

<details><summary>What does the interpreter do with <code>def f(x: int) -> str</code>?</summary>

It stores the annotations on the function object so that tools can read them, and then
executes the body as written. It performs no check on `x` and none on the returned value.

</details>

<details><summary>How can a class satisfy a <code>Protocol</code> it does not inherit from?</summary>

A protocol is matched structurally: the check is whether the required members are present.
`@runtime_checkable` makes `isinstance` perform that check; the protocol never appears in the
class's method resolution order.

</details>

<details><summary>Where should a program still validate explicitly?</summary>

At every boundary where values come from outside — parsed input, network responses,
configuration files. Static checking cannot see what those will contain.

</details>

## Listings

1. `duck-typing-with-a-safety-net-1.py` — a hint that is ignored, and the failure landing far
   from the call.
2. `duck-typing-with-a-safety-net-2.py` — a decorator that enforces the function's own hints.
3. `duck-typing-with-a-safety-net-3.py` — structural matching with `Protocol` and
   `runtime_checkable`.
