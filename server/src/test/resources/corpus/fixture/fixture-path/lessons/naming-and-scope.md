## Why this exists

A name works differently depending on where a line of code sits: inside a function, inside a nested function, or at the top of a file. Without a clear model of scope, programmers either sprinkle `global` everywhere out of fear that a function cannot see an outer name, or get surprised when a variable they expected to persist between calls resets every time. Closures make this sharper still, because a nested function can keep using a variable from an enclosing function long after that outer function has returned. Learning the lookup order removes the guesswork about which variable a given line actually refers to.

## The idea

Picture scope as a set of nested rooms in a house: the room you are standing in, the room it sits inside, the front hall of the house, and the street outside. When you say a name, Python starts in your current room, and if the name is not there, it checks the room around it, then the house's front hall, then the street, stopping at the first place the name is found. That is exactly what "LEGB" describes: Local, Enclosing, Global, Built-in. A name assigned inside a function creates a new fixture in that function's own room rather than reaching out to redecorate a room further away, unless the code says otherwise.

### Where the analogy breaks

Rooms in a house exist whether or not anyone is inside them, but a local scope only exists while its function is running, except for the one case a closure carves out: the enclosing room can stay furnished even after its function has finished, as long as an inner function still has a door leading back into it. The house analogy also suggests that seeing outward is the default; in Python, seeing outward for lookup is automatic, but writing to something outward is not, which is why plain assignment inside a function always builds locally unless `global` or `nonlocal` says otherwise.

## How it works

`global` tells Python that an assignment inside a function should modify a name from the outermost scope of the module instead of creating a local one.

```python
total = 0

def add(n):
    global total
    total += n
```

`nonlocal` does the same thing but for a name in an enclosing function rather than the module level, which is exactly what a closure needs to keep updating state between calls:

```python
def make_counter():
    count = 0
    def step():
        nonlocal count
        count += 1
        return count
    return step
```

## Common mistakes

Forgetting `nonlocal` in a nested function that tries to modify an enclosing variable produces `UnboundLocalError: local variable 'count' referenced before assignment`, because the mere presence of `count += 1` inside the inner function marks `count` as local to it, and that local copy has no value yet. A second common mistake is assuming a loop variable used inside a function defined in the loop is captured at the time the function is defined; it is instead looked up when the function actually runs, so several functions built in a loop often end up sharing the loop's final value.

## Check yourself

**1. Why does assigning to a name inside a function make it local, even if a same-named variable exists outside?**

<details><summary>Answer</summary>

Python decides a name's scope for an entire function body at compile time. Any assignment to that name anywhere in the function marks it local for the whole function, unless `global` or `nonlocal` overrides that.

</details>

**2. What keeps a closure's captured variable alive after the enclosing function returns?**

<details><summary>Answer</summary>

The inner function holds a reference to the enclosing scope. As long as that inner function object still exists, the scope it needs stays alive too.

</details>

## Full listings

The first listing uses `global` to let a function bump a module-level counter across repeated calls. The second listing builds a closure with `nonlocal` that keeps its own private counter alive between calls, independent of any other counter created the same way.
