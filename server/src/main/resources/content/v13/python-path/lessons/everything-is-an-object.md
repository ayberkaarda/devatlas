## Why this exists

Most explanations of Python stop at "everything is an object" and move on, which leaves the
sentence sounding like a slogan. It is a mechanism, and knowing it turns three separate
mysteries into one rule. Why can a function be stored in a dictionary and called out of it?
Why does a `@property` still run when the instance dictionary already has an entry of that
name? Why does assigning to a mistyped attribute succeed in silence and cost an afternoon?
All three are answered by Python 3.13's data model: classes are objects created by a
metaclass, attribute access is a defined lookup, and instances carry a dictionary unless the
class says otherwise.

## The idea

A class is a filing cabinet and an instance is a folder kept in it. Ask the folder for
something and it looks in its own contents first; if it is not there, the cabinet is
consulted, then the cabinet the cabinet was copied from, and so on up the chain. The cabinet
itself sits in a bigger room and is filed under something too — `type` — which is why the
cabinet can be moved, renamed, passed to a function and built at run time exactly like a
folder can.

### Where the analogy breaks

The order is not simply "folder first". A cabinet may hold an item that insists on answering
even when the folder has one of the same name: that is a data descriptor, and a `@property`
is the one everybody meets. Attribute lookup consults the type *first* for descriptors that
define `__set__` or `__delete__`, then the instance dictionary, then the rest of the type
chain. A folder cannot override the cabinet in that case, and listing 2 shows an entry
sitting in `__dict__` that attribute access simply refuses to return.

The picture also implies a folder always has room for more paper. `__slots__` removes that
room deliberately: there is no instance dictionary, the permitted attribute names are fixed
at class definition, and an unexpected name raises instead of being stored.

## How it works

Every value has a type, and a class is a value whose type is `type`. `type` is its own type,
which is where the chain ends.

```python
class Child(Base): ...
type(Child())      # <class 'Child'>
type(Child)        # <class 'type'>
type(type)         # <class 'type'>
```

Because a class is an ordinary object it can be built by calling `type` with a name, a tuple
of bases and a dictionary — which is exactly what the `class` statement does.

```python
Made = type("Made", (Base,), {"kind": "made at run time"})
Made.__mro__       # (Made, Base, object)
```

`__mro__` is the linearised search order, and it is what an attribute lookup walks after the
instance has been consulted. Functions are objects too: they have `__name__`, accept new
attributes, and can be stored in a dictionary and called from it, which is how dispatch
tables and decorators work without any special language support.

Assignment normally writes into the instance dictionary, which is where the typo goes.

```python
config.prot = 5433        # stored happily; config.port is unchanged
sorted(config.__dict__)   # ['host', 'port', 'prot']
```

`__slots__` closes that door. The class declares its attribute names, instances get no
`__dict__`, and the same typo raises `AttributeError`.

```python
class Tight:
    __slots__ = ("host", "port")
```

## Common mistakes

**Assuming the instance always wins.** It does not against a data descriptor. Writing
`obj.__dict__["value"] = x` where `value` is a `@property` stores the entry and changes
nothing about what `obj.value` returns.

**Assigning to a read-only property.** `AttributeError` is raised, with a message naming the
property and saying it has no setter. Adding a `@value.setter` is the fix, not reaching into
`__dict__`.

**Expecting `__slots__` to be inherited for free.** A subclass without its own `__slots__`
gets a `__dict__` back, and the protection is gone for the new class.

**Relying on `__getattr__` for everything.** It runs only after normal lookup has failed, so
it cannot intercept an attribute that exists — and if it returns a value for every name, a
misspelling never raises again.

## Check yourself

<details><summary>Why does a <code>@property</code> beat an entry in the instance dictionary?</summary>

A property defines `__set__`, which makes it a data descriptor. Attribute lookup gives data
descriptors found on the type priority over the instance dictionary.

</details>

<details><summary>What does the <code>class</code> statement actually do?</summary>

It executes the class body to build a namespace dictionary, then calls the metaclass —
`type` unless another is given — with the name, the bases and that dictionary, and binds the
result to the class name.

</details>

<details><summary>What does <code>__slots__</code> buy, and what does it cost?</summary>

It removes the per-instance dictionary, so unknown attribute names raise instead of being
stored, and instances take less memory. The cost is that attributes cannot be added at run
time, and a subclass has to declare `__slots__` too or the dictionary returns.

</details>

## Listings

1. `everything-is-an-object-1.py` — classes and functions as objects, the metaclass, and a
   class built by calling `type`.
2. `everything-is-an-object-2.py` — lookup order, with a property overruling the instance
   dictionary and `__getattr__` as the last resort.
3. `everything-is-an-object-3.py` — the silent typo, and `__slots__` refusing it.
