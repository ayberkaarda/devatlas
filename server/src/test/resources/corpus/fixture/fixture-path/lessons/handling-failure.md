## Why this exists

Things fail: a file is missing, a network call times out, a user types text where a number was expected. A program that never anticipates failure crashes at the first surprise, while a program that catches everything indiscriminately can hide the very information a developer needs to fix the actual problem. Python's exception handling gives a middle path: catch only the specific failure you know how to recover from, let anything else propagate so it is visible, and use `finally` for cleanup that must happen whether or not something went wrong.

## The idea

Think of `try`/`except` as a fire drill plan for one specific alarm, not a blanket policy for any unexpected noise. A building has a plan for a fire alarm specifically: evacuate through a known route. It does not have one universal plan that treats a fire alarm, a bake sale announcement, and a lockdown drill identically, because the correct response to each is different, and reacting to all of them the same way could make a real emergency worse. Catching `ZeroDivisionError` is like having a fire drill plan for fire alarms only; a bare `except:` is the announcement system responding to every possible alarm with "evacuate," even the ones where that response is wrong or unhelpful.

### Where the analogy breaks

A fire drill plan is meant to be exhaustive about one scenario, but exception handling is meant to be narrow about scenario selection while still allowing multiple recovery steps for that one scenario, through `else` for the case nothing went wrong and `finally` for cleanup that runs regardless. The drill analogy also does not have a natural equivalent for `else`, since a real fire drill has no special action reserved for "the alarm did not go off," whereas Python code often does have work that should only happen when the risky operation actually succeeded.

## How it works

`except` after `try` names the specific error class it recovers from; other error types are not caught and continue propagating upward.

```python
def safe_divide(a, b):
    try:
        result = a / b
    except ZeroDivisionError:
        return None
    else:
        return result
    finally:
        print("attempt finished")
```

`else` runs only if the `try` block raised nothing, and `finally` runs no matter what happened, which makes it the right place for closing a file or releasing a lock. Narrowing the caught error to the one you expect looks like this:

```python
try:
    age = int("thirty")
except ValueError as exc:
    print(exc)
```

## Common mistakes

A bare `except:` catches everything, including `KeyboardInterrupt` and typos that raise `NameError`, so a program can swallow a bug entirely and print a generic "something went wrong" instead of the traceback that would have pointed straight at the mistake. Catching `Exception` broadly and then not logging or re-raising it produces the same problem in a quieter way: the program keeps running, but a later failure downstream, caused by the swallowed error, shows up with no obvious connection back to its real cause.

## Check yourself

**1. What is the difference between code placed in `else` versus code placed after the whole `try` statement?**

<details><summary>Answer</summary>

Code in `else` only runs if the `try` block completed without raising. Code placed after the entire statement runs regardless of whether an exception was raised and caught, as long as the statement did not re-raise past it.

</details>

**2. Why is `except ZeroDivisionError:` preferred over a bare `except:`?**

<details><summary>Answer</summary>

The narrow form only catches the specific failure the code was written to recover from. A bare `except:` also catches unrelated bugs, hiding a traceback that would otherwise reveal the real problem.

</details>

## Full listings

The first listing shows a `try`/`except`/`else`/`finally` chain around a division that might fail, with `finally` printing regardless of the outcome. The second listing narrows the catch to `ValueError` while parsing text into an integer, keeping the original exception's message visible.
