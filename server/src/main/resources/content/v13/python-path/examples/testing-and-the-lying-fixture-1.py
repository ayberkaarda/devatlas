"""A fixture built once for the class makes a later test pass for free.

The runner's own report carries timings, so this listing prints a summary of
unittest.TestResult instead: counts and method names, which are reproducible.
"""

import io
import unittest


class Cart:
    def __init__(self) -> None:
        self.lines: list[tuple[str, int]] = []

    def add(self, name: str, quantity: int) -> None:
        self.lines.append((name, quantity))

    def total_items(self) -> int:
        return sum(quantity for _, quantity in self.lines)


class SharedFixture(unittest.TestCase):
    cart = Cart()                      # created once, when the class is defined

    def test_a_adding_counts(self) -> None:
        self.cart.add("apple", 1)
        self.assertEqual(self.cart.total_items(), 1)

    def test_b_cart_has_an_apple(self) -> None:
        self.assertEqual(self.cart.total_items(), 1)


def run(suite: unittest.TestSuite, label: str) -> None:
    result = unittest.TextTestRunner(stream=io.StringIO(), verbosity=0).run(suite)
    print(f"{label}: ran {result.testsRun}, failures {len(result.failures)}")
    for test, _ in result.failures:
        print("  failed:", test._testMethodName)


def main() -> None:
    loader = unittest.defaultTestLoader
    print("discovery order:", loader.getTestCaseNames(SharedFixture))

    alone = unittest.TestSuite([SharedFixture("test_b_cart_has_an_apple")])
    run(alone, "second test on its own")

    run(loader.loadTestsFromTestCase(SharedFixture), "whole class in order ")

    print("the same test failed alone and passed after its neighbour ran")


if __name__ == "__main__":
    main()
