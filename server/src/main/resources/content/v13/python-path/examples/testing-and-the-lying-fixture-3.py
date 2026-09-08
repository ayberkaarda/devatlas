"""Assertions that cannot fail, and the ones that can."""

import io
import unittest
from unittest.mock import Mock


class Signup:
    """Registers an address. It is supposed to mail a welcome and to
    refuse a blank address; it does neither."""

    def __init__(self, mailer) -> None:
        self.mailer = mailer
        self.registered: list[str] = []

    def register(self, address: str) -> list[str]:
        self.registered.append(address)
        return self.registered


class WeakAssertions(unittest.TestCase):
    def test_truthy_container(self) -> None:
        signup = Signup(Mock())
        result = signup.register("ada@example.org")
        self.assertTrue(result)                 # true for any non-empty list

    def test_no_exception(self) -> None:
        signup = Signup(Mock())
        signup.register("ada@example.org")      # asserts nothing at all


class StrongAssertions(unittest.TestCase):
    def test_exact_value(self) -> None:
        signup = Signup(Mock())
        self.assertEqual(signup.register("ada@example.org"), ["ada@example.org"])

    def test_mail_was_sent(self) -> None:
        mailer = Mock()
        signup = Signup(mailer)
        signup.register("ada@example.org")
        mailer.send.assert_called_once_with("ada@example.org", "welcome")

    def test_rejects_blank(self) -> None:
        signup = Signup(Mock())
        with self.assertRaises(ValueError):
            signup.register("")


def run(case: type[unittest.TestCase]) -> None:
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(case)
    result = unittest.TextTestRunner(stream=io.StringIO(), verbosity=0).run(suite)
    print(f"{case.__name__}: ran {result.testsRun}, "
          f"failures {len(result.failures)}, errors {len(result.errors)}")
    for test, _ in result.failures + result.errors:
        print("  reported:", test._testMethodName)


def main() -> None:
    run(WeakAssertions)
    run(StrongAssertions)
    print("Signup.register never sends mail and never validates;")
    print("only the second class noticed.")


if __name__ == "__main__":
    main()
