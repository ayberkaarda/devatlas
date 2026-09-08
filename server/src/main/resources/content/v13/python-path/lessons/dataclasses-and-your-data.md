## Why this exists

A dictionary is the fastest way to hold a few related values, and for a while it is the right
one. Then the same three keys are read in eleven places, one of those places writes `"prot"`
instead of `"port"`, and the dictionary accepts it — because accepting new keys is what a
dictionary is for. Nothing raises. A field that was supposed to be required turns out to be
absent in one code path. Comparing two records means writing a helper. Python 3.13's
`dataclasses` module gives the same convenience with a declared shape: the fields are named
once, the decorator writes the constructor, `__repr__` and `__eq__` from that declaration, and an
unexpected name is refused at the call.

## The idea

A dictionary is a blank notebook and a dataclass is a printed form. In the notebook you can
write anything anywhere, which is exactly what you want while you are still working out what
to record. The form has named boxes; handing it in with a box nobody printed is not something
the clerk will accept, and two forms filled in identically are treated as the same record.

### Where the analogy breaks

A form is inert and a dataclass is an ordinary class. It can have methods, inherit, run
validation in `__post_init__`, and be frozen so that assignment raises rather than being
ignored. It also decides its own equality, ordering and hashability by keyword, which no
paper form does: `frozen=True` makes instances usable as dictionary keys and set members,
`order=True` supplies the comparison operators, in field order.

The place the picture misleads is depth. `frozen=True` stops assignment to the *fields*; it
says nothing about what a field points at. A frozen dataclass holding a list has a list that
anyone can still append to, and `asdict` copies the structure while `replace` does not — the
new instance shares the same inner objects. Immutability in Python stops at the first
reference, and a form makes it look total.

## How it works

`@dataclass` reads the annotated names in the class body and writes the methods from them.

```python
@dataclass
class Endpoint:
    host: str
    port: int
    scheme: str = "https"
```

That produces `__init__`, `__repr__` and `__eq__`. A misspelled keyword raises `TypeError`
at construction, and a missing required field raises `TypeError` too — the two failures a
dictionary cannot give you.

Mutable defaults are refused outright. Declaring `items: list[str] = []` raises `ValueError`
when the class is created, with a message telling you to use `default_factory`; the factory
is called once per instance.

```python
@dataclass
class Basket:
    owner: str
    items: list[str] = field(default_factory=list)
```

`__post_init__` runs after `__init__` has assigned the fields, which is where
normalisation and cross-field validation belong. `field(repr=False)` keeps a value out of the
`__repr__` without removing it.

Three keywords change what kind of thing the class is. `frozen=True` makes assignment raise
`FrozenInstanceError` and gives instances a `__hash__`; `order=True` adds `<`, `<=`, `>`
and `>=` comparing fields in declaration order; `slots=True` removes the instance dictionary.

```python
@dataclass(frozen=True, order=True, slots=True)
class Version:
    major: int
    minor: int
    patch: int = 0
```

`dataclasses.replace` builds a new instance with some fields changed, `fields()` reports the
declaration, and `asdict()` converts back to nested dictionaries at the boundary where a
dictionary is genuinely what is wanted.

## Common mistakes

**Declaring a list or dict default directly.** `ValueError: mutable default <class 'list'>
for field items is not allowed: use default_factory`. The check exists because the default
would otherwise be shared by every instance.

**Expecting `frozen=True` to freeze the contents.** It prevents attribute assignment only.
Store a tuple, or copy on the way in, if the inner value must not change.

**Sorting without `order=True`.** `sorted` raises `TypeError` saying the operands do not
support `<`. Equality comes by default; ordering does not.

**Using a dataclass with a mutable field as a dictionary key.** `frozen=True` supplies a
`__hash__` derived from the fields, and if a field's own hash changes the entry is filed under
a value the dictionary will no longer compute.

## Check yourself

<details><summary>Why is a mutable default an error rather than a warning?</summary>

The default is stored once on the `__init__` the decorator writes, so every instance would share one
object. The module refuses it at class creation and points at `default_factory`, which is
called per instance.

</details>

<details><summary>What exactly does <code>frozen=True</code> prevent?</summary>

Assignment to the instance's own fields, which raises `FrozenInstanceError`. It does not
prevent mutation of objects a field refers to, and it does not deep-copy anything.

</details>

<details><summary>When is a dictionary still the better choice?</summary>

When the keys are data rather than structure — arbitrary user-supplied names, a parsed
document, a counter. A dataclass is for a shape you decided on and want enforced.

</details>

## Listings

1. `dataclasses-and-your-data-1.py` — the typo a dictionary keeps and a dataclass refuses.
2. `dataclasses-and-your-data-2.py` — `frozen`, `order` and `slots`, as a key and in a sort.
3. `dataclasses-and-your-data-3.py` — `default_factory`, `__post_init__`, and the refused
   mutable default.
