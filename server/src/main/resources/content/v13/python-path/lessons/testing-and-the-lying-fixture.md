## Why this exists

A green test suite is only worth what its assertions are worth. Two failures are common and
neither is visible in the output: a test that passes because another test left something
behind, and a test whose assertion could not have failed. Both look identical to a passing
test — same dot, same summary line — and both are usually discovered months later, when the
code they were supposed to protect breaks in production with the suite still green.
Python 3.13's `unittest` gives each test method a fresh `TestCase` instance and runs `setUp`
before every one; using that properly is most of the cure, and the rest is writing assertions
that name an expected value.

## The idea

A test is an experiment, and a fixture is the bench it runs on. An experiment that begins on
a bench somebody else was just using is measuring their leftovers as much as its own
reagents. The discipline is not cleverness; it is wiping the bench between runs, so that the
only thing that could have produced the result is the thing under test.

### Where the analogy breaks

A bench is visibly dirty and shared state is not. The leftover is usually a Python object that
looks exactly as it did at the start — a list with one extra element, a class attribute
mutated by an earlier method, a module-level cache. Nothing about the second test's source
mentions it.

The analogy also implies a clean bench guarantees a good experiment. It does not. `setUp` can
give every test a pristine object and the assertions can still be worthless: `assertTrue` on
a non-empty list passes for any non-empty list, and a test that only checks "no exception was
raised" passes for a function that does nothing at all. Isolation makes a failing test
trustworthy; assertions are what make a passing one mean something.

## How it works

`unittest` builds a new instance of the `TestCase` class for every test method and calls
`setUp` before it and `tearDown` after, so state assigned to `self` in `setUp` cannot leak
between tests.

```python
class FreshFixture(unittest.TestCase):
    def setUp(self):
        self.cart = Cart()      # rebuilt for every test method
```

State assigned in the class body is different. It is created once, when the class statement
runs, and every test method sees the same object.

```python
class SharedFixture(unittest.TestCase):
    cart = Cart()               # created once, shared by every method
```

Methods are discovered in alphabetical order by name, so `test_a_...` runs before
`test_b_...`, and a test that depends on its neighbour will pass in a full run and fail on
its own. Listing 1 demonstrates exactly that: the same method reports one failure when it is
the only test in the suite, and passes when the whole class runs.

Assertions are where the second failure lives. `assertEqual` names the value expected;
`assertTrue` only requires truthiness, and `assertRaises` is the way to state that a failure
is required rather than tolerated.

```python
self.assertEqual(signup.register("ada@example.org"), ["ada@example.org"])
with self.assertRaises(ValueError):
    signup.register("")
```

For collaborators a program should not really call during a test, `unittest.mock.Mock`
records what happened and `assert_called_once_with` states what should have. A `Mock` accepts
any call, so without that assertion a test proves only that the code did not crash.

## Common mistakes

**A fixture in the class body.** One object for the whole class. The first mutation makes
every later test in the class order-dependent.

**`assertTrue` where a value was meant.** It passes for any non-empty container, any non-zero
number and any object without `__bool__`. Listing 3 has two such tests passing against a
function that neither sends its mail nor validates its input.

**A test with no assertion.** It passes unless an exception escapes, which makes it a
smoke test, not a test of behaviour. It should say so in its name.

**Trusting a `Mock` without asserting on it.** A `Mock` answers every attribute and every
call. The test passes whether or not the code called it.

## Check yourself

<details><summary>Why does a fixture in the class body break isolation when <code>setUp</code> does not?</summary>

The class body executes once, so its objects are shared by every test method. `setUp` runs
before each method on a fresh `TestCase` instance, so anything it assigns to `self` is built
again for every test.

</details>

<details><summary>A test passes in the suite and fails alone. What does that tell you?</summary>

That it depends on state an earlier test produced. The test is not testing what it claims;
either the fixture must be made per-test, or the dependency must be made explicit inside the
test itself.

</details>

<details><summary>What is wrong with <code>assertTrue(result)</code>?</summary>

It asserts only that `result` is truthy, so it cannot distinguish the right answer from any
other non-empty one. `assertEqual` against the expected value can.

</details>

## Listings

1. `testing-and-the-lying-fixture-1.py` — a class-body fixture, and the same test failing
   alone and passing in company.
2. `testing-and-the-lying-fixture-2.py` — `setUp`, and a suite that gives the same result in
   any order.
3. `testing-and-the-lying-fixture-3.py` — assertions that cannot fail, beside ones that do.
