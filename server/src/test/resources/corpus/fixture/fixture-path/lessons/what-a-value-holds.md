## Why this exists

New Python programmers usually arrive with a mental model borrowed from spreadsheets or from languages where a variable is a labeled box holding a value. That model works fine for numbers and strings until a list, dictionary, or custom object gets copied into "another" variable and both variables start changing together. The bug that follows looks random: a function mutates a list the caller did not expect to change, or two supposedly separate records drift into being the same record. Understanding that a name is a reference to an object, not a container for it, removes the mystery and predicts the behavior instead of just reacting to it.

## The idea

Think of an object as a house and a name as a sticky note with the house's address written on it. Writing `a = [1, 2, 3]` builds a house and puts one sticky note, labeled `a`, on its door. Writing `b = a` does not build a second house; it writes a second sticky note, `b`, with the same address, and sticks it on the same door. Anyone who walks up to the house using either note is standing in the same rooms. If someone repaints a wall through the `b` note, the `a` note leads to the same freshly painted wall, because there was only ever one house.

### Where the analogy breaks

Addresses on paper do not change what they point to once written down, but reassignment does exactly that: `b = [9, 9]` peels the `b` sticky note off the first house and staples it to a brand new one, leaving `a` still pointing at the original. The analogy also glosses over the fact that small integers and short strings in Python are frequently reused objects behind the scenes, which is why `is` comparisons on them can look deceptively true even though relying on that reuse is not something a program should ever depend on.

## How it works

Assignment in Python always binds a name to an existing object; it never duplicates the object's contents. `==` asks whether two objects have equal value, while `is` asks whether two names refer to the identical object in memory.

```python
a = [1, 2, 3]
b = a
b.append(4)
print(a == b, a is b)
```

Both print `True` here, because `b` never pointed anywhere else. A real copy needs an explicit request, such as slicing (`a[:]`) or the `copy` module, which builds a second, independent object.

## Common mistakes

Passing a list into a function and expecting the caller's copy to be untouched is the most common trap; the function mutates the same object, and the caller sees the change with no `TypeError` or warning at all, just data that changed when it should not have. Another frequent one is comparing large integers with `is` instead of `==`, which can silently return `False` even when the two values are mathematically equal:

```python
x = 1000
y = 1000
print(x == y, x is y)
```

## Check yourself

**1. If `x = [1]` and `y = [1]`, what do `x == y` and `x is y` print?**

<details><summary>Answer</summary>

`x == y` prints `True` because the lists contain equal elements. `x is y` prints `False` because they are two separate list objects that happen to hold the same value.

</details>

**2. Does `c = a[:]` followed by `c.append(5)` change `a`?**

<details><summary>Answer</summary>

No. Slicing builds a new list object, so `c` points somewhere new; mutating `c` leaves the object `a` points to untouched.

</details>

## Full listings

The first listing contrasts a plain assignment, which shares one list between two names, with a slice copy that produces an independent list. The second listing shows how identity checks on integers and strings can differ from equality checks even when the underlying values match.
