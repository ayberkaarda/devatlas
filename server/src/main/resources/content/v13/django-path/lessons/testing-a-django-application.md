## Why this exists

A Django test suite is fast and thorough or it is neither, and which one you get
is decided by things that have nothing to do with the assertions. Where the data
comes from, whether a test can see what the previous one wrote, whether the test
database is the development database — those decide whether a green suite means
anything. Django gives you a runner, a client that exercises the whole request
path, and a set of base classes that differ in exactly one respect: how much
they are willing to let a test touch. Choosing between them is most of the
skill.

## The idea

A test runs in a hotel room. It is made up before you arrive, you may leave it
in any state you like, and it is stripped again before the next guest. Nobody
inherits the last guest's mess, and nothing you do follows you out.

### Where the analogy breaks

Housekeeping is not the same in every room. `TestCase` wraps each test in a
transaction and rolls it back, which is fast and means the room is restored
without ever really being cleaned. `TransactionTestCase` truncates the tables
instead, which is slower and is the only option when the code under test
commits or when you need to see what a real transaction boundary does. Listing 2
runs both and finds neither leaves a row behind, by two different mechanisms.

`SimpleTestCase` is a room with the water switched off. It does not open the
database at all, and it does not merely fail to set one up — it actively
refuses. Listing 2 asks it for a row count and catches
`DatabaseOperationForbidden`, which is a much better outcome than a test that
silently reads production data.

## How it works

The runner creates a separate database, runs the suite against it, and destroys
it. Listing 2 records the configured name and the name the tests actually saw
and reports that they differ — a test cannot reach the development database by
accident.

`TestCase.setUpTestData` builds fixtures once for the class rather than once per
test, inside an outer transaction that is rolled back at the end:

```python
class ClientTest(TestCase):
    @classmethod
    def setUpTestData(cls):
        Product.objects.create(name="desk", price=100)
```

Listing 1 then writes a third row in one test and finds two again in the next:
per-test changes are undone, the class fixture is not. Its `IsolationTest`
writes a row in `test_a` and asserts an empty table in `test_b`.

The test client walks the real path — URLconf, middleware, view — and returns
the response:

```python
response = self.client.get(reverse("product-list"))
self.assertEqual(response.json()["names"], ["chair", "desk"])
```

Ordering there is asserted because the view applies `order_by("name")`. Without
an `ORDER BY` the row order is not something the database promises, and a test
that asserts it is recording an accident.

`assertNumQueries` is how a performance property becomes a test. Listing 3
serves the same page twice, once with `select_related` and once without, asserts
that both return five rows, and pins the query counts at 1 and 6. It also
asserts that the unoptimised view *fails* the one-query assertion, so the
assertion is shown to bite rather than assumed to.

## Common mistakes

**Depending on the order tests run in.** `unittest` sorts method names within a
class, and nothing orders the classes for you. Listing 1's `test_a`/`test_b`
naming is a demonstration, not a pattern to copy: a test that needs a previous
test to have run is one test written as two.

**Using `TestCase` for code that commits.** The rollback and the commit fight.
`TransactionTestCase` is the answer, and it is slower on purpose.

**Asserting an order the query did not ask for.** Add `order_by`, or assert
against a set.

**Reaching for `TransactionTestCase` by default.** Truncating every table
between tests costs real time on a large schema. Start with `TestCase`.

**Writing a fixture that makes the test pass.** A fixture built by the same
helper the code under test uses will agree with it however wrong both are.
Listing 3's fixture is five plain rows and the assertions are about counts the
fixture does not choose.

## Check yourself

<details><summary>Can a test read the development database?</summary>

No. Listing 2 records the configured name and the name in force during the
tests and reports that they differ; the runner creates and destroys its own.

</details>

<details><summary>When is <code>TestCase</code> the wrong base class?</summary>

When the code commits, or when the test needs to observe transaction behaviour —
the per-test rollback interferes. `TransactionTestCase` truncates instead.
Listing 2 runs both.

</details>

<details><summary>How do you stop an N+1 regression coming back?</summary>

`assertNumQueries` around the page. Listing 3 pins the optimised view at one
query and shows the unoptimised one failing that same assertion, which is what
proves the test would catch the regression.

</details>

## Listings

1. `testing-a-django-application-1.py` — the runner, the client, and isolation.
2. `testing-a-django-application-2.py` — the database a test gets, and the one
   it is refused.
3. `testing-a-django-application-3.py` — a query count that catches a
   regression.
