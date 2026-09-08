## Why this exists

A function signature is a promise about what callers can pass in and what they get back, and Python gives several ways to shape that promise: required positional arguments, optional arguments with defaults, and open-ended `*args`/`**kwargs` for callers who need flexibility. Getting comfortable with these forms matters because one specific default-argument pattern looks completely reasonable and quietly breaks: a mutable object used as a default value is created exactly once, when the function is defined, and every call that relies on the default shares that same object.

## The idea

Think of a default argument as a shared supply closet listed on a work order rather than a fresh box handed to each worker. If the work order says "use the supply closet if you weren't given your own box," and the closet is a single physical room, then every worker who skips bringing their own box ends up reaching into the exact same room. One worker leaving tools behind in the closet means the next worker who relies on the default finds those tools still sitting there, even though the two workers never coordinated. A default that is a plain number or a string has no such closet, because each worker effectively gets a fresh label rather than a shared space.

### Where the analogy breaks

A supply closet is visibly one physical place, but a default argument in code looks like it should be a fresh value handed out per call, which is exactly why the sharing is surprising the first time it happens. The analogy also does not capture when the closet gets built: it is built once, at the moment the function is defined, not once per call, so the "supply closet" is stocked before any worker has even shown up to use it.

## How it works

A default value is evaluated once, at function definition time, and that same object is reused on every call where the argument is omitted.

```python
def add_item(item, basket=None):
    if basket is None:
        basket = []
    basket.append(item)
    return basket
```

Checking for `None` and building a fresh list inside the function body sidesteps the shared-object problem entirely, because a new list is created on every call instead of once at definition time. `*args` collects extra positional arguments into a tuple, and `**kwargs` collects extra keyword arguments into a dictionary:

```python
def summarize(title, *values, **labels):
    print(values, labels)

summarize("scores", 10, 20, curve="none")
```

## Common mistakes

Writing `def add_item(item, basket=[]):` directly, without the `None` guard, produces a basket that keeps every item from every call that used the default, which looks like a data leak between unrelated calls rather than an obvious syntax error, since nothing about the code raises an exception. A second common mistake is assuming `*args` and `**kwargs` must be named exactly that; the names are a convention, not a requirement, and forgetting the `**` or `*` prefix instead turns them into ordinary required parameters, raising `TypeError: missing required argument` when a caller does not supply them explicitly.

## Check yourself

**1. Why does calling `add_item("apple")` twice, both times omitting `basket`, not return two separate one-item lists, if the guard against `None` is removed?**

<details><summary>Answer</summary>

Without the guard, the empty list in the default is created once, when the function is defined, and both calls share and mutate that same list object.

</details>

**2. What type of object does `**kwargs` collect extra keyword arguments into?**

<details><summary>Answer</summary>

A dictionary, with each keyword argument's name as a key and the passed value as that key's value.

</details>

## Full listings

The first listing demonstrates the mutable-default trap being avoided with a `None` guard that builds a fresh list per call. The second listing shows a function that accepts a required title alongside arbitrary extra positional and keyword arguments through `*args` and `**kwargs`.
