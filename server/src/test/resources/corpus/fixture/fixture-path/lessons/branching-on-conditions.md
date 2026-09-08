## Why this exists

Every program eventually needs to do one thing or another depending on the data it holds, and Python's `if`/`elif`/`else` reads almost like plain English, which hides a few decisions that matter. Chief among them is what counts as true when the condition is not an obvious `True` or `False`: an empty list, the number zero, and an empty string are all treated as false without you writing any comparison at all. That convenience becomes a bug the moment "empty" and "missing" need to be told apart, which is exactly the case a `None` check exists to handle.

## The idea

Think of a bouncer at a door who only asks one question: "is there anything here worth letting through?" An empty guest list, a zero-headcount reservation, and a blank name all get the same answer: no, nothing here, do not open the door. The bouncer does not care why the list is empty, whether no one was ever invited or everyone already left; from the door's point of view those situations look identical. `if x:` is that bouncer, and it treats every kind of "nothing" the same way regardless of the value's actual type.

### Where the analogy breaks

A bouncer only has one door to guard, but Python's truthiness check quietly covers many different types of "nothing": `0`, `0.0`, `""`, `[]`, `{}`, and `None` are all false, even though they mean very different things to the surrounding program. The bouncer analogy also cannot express the case that actually needs separating: a reservation that exists but genuinely has zero guests, versus a reservation that was never made at all. `if x:` cannot tell those two apart, which is precisely why `if x is not None:` exists as a sharper, narrower question.

## How it works

`if`, `elif`, and `else` run top to bottom, executing the first branch whose condition is true and skipping the rest.

```python
cart = []
if cart:
    print("checkout is enabled")
elif cart is not None:
    print("cart is empty but ready")
```

Here `cart` is an empty list, so `if cart:` is false and control falls to the `elif`, which asks a different question entirely: not "is this non-empty" but "does this exist at all." An empty cart and a missing cart both fail the first check, but only a missing one fails the second. A value can be falsy and still be present, which is exactly the distinction the narrower check makes:

```python
value = 0
missing = None
print(value is not None, missing is not None)
```

## Common mistakes

Writing `if score == 0:` to catch a missing score, when the field is actually `None` for "not graded," produces wrong behavior with no error message at all: `0 == None` is simply `False`, so the intended branch never runs and the mistake shows up only as a wrong answer somewhere downstream. A second common mistake is writing `if not value:` to detect a missing value when `value` might legitimately be `0` or an empty string; the check fires for those valid values too, which is easy to miss until a test supplies exactly that edge case.

## Check yourself

**1. What does `if []:` evaluate to, and why?**

<details><summary>Answer</summary>

It evaluates to false, because an empty list has no elements. Python treats an empty container as falsy regardless of its type.

</details>

**2. When should you write `if x is not None:` instead of `if x:`?**

<details><summary>Answer</summary>

When zero, an empty string, or an empty collection are valid, meaningful values that should still take the "present" branch. `is not None` only excludes the genuinely missing case.

</details>

## Full listings

The first listing shows an empty cart falling through to an `elif` that specifically checks for `None` rather than emptiness. The second listing builds a grading function whose branches distinguish an ungraded score from a graded score of zero.
