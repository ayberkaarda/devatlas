## Why this exists

A program that grows past a single file needs a way to split related code apart while still letting one part use another, and Python's module and package system is exactly that mechanism: any `.py` file is a module, any directory of them (with the right marker) is a package, and `import` is how one file borrows names from another. Alongside that, the same file is often meant to work two ways: as a script you run directly, and as a module another file imports for its functions. The `__name__ == "__main__"` check exists to tell those two situations apart.

## The idea

Think of a module as a toolbox with a label on the outside naming everything inside it. `import math` is carrying the whole labeled toolbox into your workshop and reaching into it as `math.pi` or `math.sqrt(16)` each time, always through the box's label. `from math import sqrt` is instead taking one specific tool out of the box and setting it directly on your bench, so you can reach for `sqrt(16)` without naming the box each time. Both approaches get you the same tool; they differ only in whether you keep carrying the box's label around with you.

### Where the analogy breaks

A toolbox sitting on a shelf does not care whether you are actively building something or just organizing your workshop, but a Python module absolutely does run its top-level code the moment it is imported, not just when someone calls a function from it. That is precisely the gap `__name__ == "__main__"` closes: code inside that check only runs when the file is executed directly, never when the same file is imported elsewhere purely to borrow its functions, which a plain toolbox has no equivalent distinction for at all.

## How it works

Importing a module runs its top-level statements exactly once, the first time it is imported, and caches the result for any later import in the same run.

```python
import math
from math import sqrt

print(math.pi)
print(sqrt(16))
```

Guarding script-only behavior behind `if __name__ == "__main__":` keeps that behavior from firing when the file is imported rather than run directly, since `__name__` equals `"__main__"` only in the file that was actually launched:

```python
if __name__ == "__main__":
    print("running as a script")
else:
    print("imported as a module")
```

## Common mistakes

Two modules that import each other at the top level can raise `ImportError: cannot import name ... (most likely due to a circular import)`, because each module tries to finish loading before the other one has fully defined the name being asked for. A second common mistake is putting a script's real work directly at module level with no `__name__` guard at all; importing that file anywhere else, even just to reuse one function from it, triggers the whole script to run immediately, often printing output or opening files nobody asked for.

## Check yourself

**1. What is the practical difference between `import math` and `from math import sqrt`?**

<details><summary>Answer</summary>

`import math` requires prefixing every name with `math.`, while `from math import sqrt` brings `sqrt` directly into the current file's namespace, usable without a prefix.

</details>

**2. Why does `__name__ == "__main__"` matter for a file meant to be both a script and an importable module?**

<details><summary>Answer</summary>

It lets script-only code run when the file is executed directly, while staying silent when the same file is imported elsewhere purely to reuse its functions.

</details>

## Full listings

The first listing imports the standard `math` module two different ways and uses both styles in the same file. The second listing is a shell script that runs a Python module as a program with `python -m`, the same style of invocation that makes the `__name__` guard meaningful.
