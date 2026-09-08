## Why this exists

Python has no declaration and no visible pointer, so an assignment looks like it copies. Most
of the time nothing depends on the difference. Then a function appends to a list a caller
passed in, a configuration dictionary is handed to three subsystems and edited by one of
them, or a "copy" of a record turns out to be the record. The bug is never in the line that
fails. It is in an earlier line that everybody read as "make a copy" and that was never a
copy at all. Python 3.13's execution model defines assignment as binding a name to an object,
and once that sentence is taken literally the whole family of bugs stops being surprising.

## The idea

Think of names as luggage tags and objects as suitcases. `a = [1, 2, 3]` prints a tag reading
`a` and ties it to a suitcase. `b = a` prints a second tag, `b`, and ties it to the *same*
suitcase — it does not buy a second suitcase. Packing something through the `b` tag changes
what someone reading the `a` tag will find. Re-tying `b` to a different suitcase leaves the
`a` tag exactly where it was.

### Where the analogy breaks

A tag is a physical thing you could find and count; a Python name is an entry in a namespace,
and the object does not know its own names. Nothing about the list records that two names
point at it, which is why there is no way to ask "who else can see this?".

The analogy also suggests suitcases are all alike. They are not: some objects cannot be
packed at all. An `int`, a `str` and a `tuple` have no operation that changes them in place,
so for those the distinction the analogy is teaching is invisible — every apparent change is
a new object and a re-tied tag. The distinction only becomes observable for lists,
dictionaries, sets and ordinary class instances.

## How it works

The reference manual defines an assignment statement as binding a target to the object the
right-hand side evaluates to. No copy is made and no type check happens.

```python
a = [1, 2, 3]
b = a
b.append(4)          # one object, reached through two names
print(a)             # [1, 2, 3, 4]
```

`is` compares object identity and `==` compares value, which is why two equal lists can
answer `False` to `is`. Identity is what matters when you want to know whether a mutation is
going to be seen elsewhere.

Rebinding and mutating look similar and are not. For a list, `b = b + [5]` builds a new list
and re-points `b`; `b += [5]` calls `list.__iadd__`, which extends the existing list and
gives it back, so both names see the change.

```python
b = [1, 2]; c = b
b += [3]             # same object, both names see [1, 2, 3]
b = b + [4]          # new object, c still sees [1, 2, 3]
```

The same rule governs arguments. A call binds the argument object to a parameter name inside
the function; the function can mutate that object, and the caller sees it. Assigning to the
parameter only rebinds the local name.

```python
def rebind(values):
    values = values + [99]      # local name only
def mutate(values):
    values.append(99)           # visible to the caller
```

## Common mistakes

**Expecting `b = a` to copy.** It never does. `list(a)`, `a.copy()` or `copy.deepcopy(a)` do.

**Assigning to a parameter and expecting the caller to notice.** The caller keeps its own
binding. Return the new value, or mutate in place deliberately with `values[:] = ...`.

**`t[0] += [3]` on a tuple.** The interpreter raises `TypeError: 'tuple' object does not
support item assignment` — and the list inside the tuple has already been extended, because
`__iadd__` ran before the failing store. Listing 3 shows both halves.

**Reading before binding in a function.** Assigning to a name anywhere in a function body
makes it local for the whole body, so an earlier read raises `UnboundLocalError` rather than
finding the module-level value.

## Check yourself

<details><summary>Why can two lists be <code>==</code> but not <code>is</code>?</summary>

`==` asks whether the values compare equal, which for lists means element by element. `is`
asks whether there is one object or two. Two separately built lists with the same contents
are two objects.

</details>

<details><summary>A function does <code>values = values + [1]</code>. What does the caller see?</summary>

Nothing. The concatenation built a new list and the assignment re-pointed the local name at
it. The caller's name is still bound to the original object.

</details>

<details><summary>Why does the failing tuple statement still change the list?</summary>

Augmented assignment on a subscript is read, modify, store. `list.__iadd__` mutates in place
and returns the same list; only the store back into the tuple slot fails.

</details>

## Listings

1. `names-objects-and-rebinding-1.py` — aliasing, mutation and rebinding, with identity read
   as a property rather than as an address.
2. `names-objects-and-rebinding-2.py` — what a call passes, and what an assignment inside the
   function can and cannot change.
3. `names-objects-and-rebinding-3.py` — the augmented assignment that raises and mutates.
