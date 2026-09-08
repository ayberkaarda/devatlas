## Why this exists

Code that works once, by hand, in the moment it was written, has no guarantee of still working after the next change. A test written down instead of run mentally becomes a repeatable question the program can answer for you: does this still do what it is supposed to do. The arrange-act-assert shape gives that question a consistent structure, and `unittest`'s `setUp` avoids repeating the same setup code in every test. The harder discipline is testing what a function is supposed to accomplish rather than how it happens to be written internally, since a test tied to implementation details breaks every time the implementation is refactored, even when the behavior stays correct.

## The idea

Think of a test like a recipe tester in a kitchen who is handed a finished dish and a fork, not the recipe card. They taste the dish and check it against what was ordered: is it salty enough, is it the right temperature, does it match the description on the menu. They do not care whether the cook used a whisk or a spoon to mix the batter, only whether the result on the plate is correct. Arrange sets the table and gathers the ingredients, act is the cook preparing the dish, and assert is the taste test at the end that says yes or no.

### Where the analogy breaks

A recipe tester tastes one dish at a time from one kitchen, but `setUp` in `unittest` runs before every single test method in a class, rebuilding the same starting ingredients fresh each time rather than reusing a dish that already exists from a previous test. The tasting analogy also suggests a single verdict per dish, while a real test class often runs the same arrange-and-act steps and then makes several distinct assertions across many test methods, each checking a different aspect of the same prepared result.

## How it works

`setUp` runs before each test method in the class, giving every test the same fresh starting data without repeating the setup code by hand.

```python
class TotalPriceTests(unittest.TestCase):
    def setUp(self):
        self.items = [{"price": 5}, {"price": 7}]

    def test_sums_all_prices(self):
        self.assertEqual(total_price(self.items), 12)
```

The test calls `total_price` and checks its return value; it says nothing about whether that function uses a loop, `sum()`, or something else internally, which is exactly what testing behavior instead of implementation means:

```python
def total_price(items):
    return sum(item["price"] for item in items)

print(total_price([{"price": 5}, {"price": 7}]))
```

## Common mistakes

Asserting on an internal variable or a private helper function instead of the public result means a harmless refactor, one that keeps the same external behavior, can turn a passing test into a failing one for no real reason, and the failure message then points at code structure rather than an actual bug. Forgetting that `setUp` runs fresh before every test method is a second common trap: relying on one test method's changes carrying over into another produces results that depend on test execution order, and reordering the tests silently changes which ones pass.

## Check yourself

**1. What does `setUp` guarantee about the state each test method starts with?**

<details><summary>Answer</summary>

`setUp` runs before every test method in the class, so each test method starts from the same freshly built state, with no leftover changes from a previous test.

</details>

**2. Why is asserting on a function's return value usually better than asserting on an internal variable it used along the way?**

<details><summary>Answer</summary>

The return value is the function's actual contract with its callers. An internal variable can change or disappear during a refactor that keeps the external behavior identical, which would break the test for no real reason.

</details>

## Full listings

The first listing uses `unittest`'s `setUp` to build a fresh list of priced items before a test asserts on the total returned by a plain function. The second listing creates the table such a fixture's data would ultimately be stored in, alongside sample rows and a query ordering them by total.
