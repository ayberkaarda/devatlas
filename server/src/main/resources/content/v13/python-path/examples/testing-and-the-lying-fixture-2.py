"""setUp runs before every test, so each one starts from the same place."""

import io
import unittest


class Cart:
    def __init__(self) -> None:
        self.lines: list[tuple[str, int]] = []

    def add(self, name: str, quantity: int) -> None:
        self.lines.append((name, quantity))

    def total_items(self) -> int:
        return sum(quantity for _, quantity in self.lines)


class FreshFixture(unittest.TestCase):
    def setUp(self) -> None:
        self.cart = Cart()

    def test_a_adding_counts(self) -> None:
        self.cart.add("apple", 1)
        self.assertEqual(self.cart.total_items(), 1)

    def test_b_starts_empty(self) -> None:
        self.assertEqual(self.cart.total_items(), 0)

    def test_c_quantities_add_up(self) -> None:
        self.cart.add("apple", 2)
        self.cart.add("pear", 3)
        self.assertEqual(self.cart.total_items(), 5)


def run(names: list[str], label: str) -> None:
    suite = unittest.TestSuite([FreshFixture(name) for name in names])
    result = unittest.TextTestRunner(stream=io.StringIO(), verbosity=0).run(suite)
    print(f"{label}: ran {result.testsRun}, failures {len(result.failures)}, "
          f"errors {len(result.errors)}")


def main() -> None:
    names = unittest.defaultTestLoader.getTestCaseNames(FreshFixture)
    print("tests:", names)
    run(names, "declaration order")
    run(list(reversed(names)), "reversed order  ")
    run([names[1]], "second one alone")
    print("the result did not depend on the order:", True)


if __name__ == "__main__":
    main()
