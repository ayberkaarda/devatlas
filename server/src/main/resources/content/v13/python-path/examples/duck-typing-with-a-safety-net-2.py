"""Annotations are readable data, so a check can be written against them."""

import functools
import typing


def checked(func):
    """Verify positional arguments and the return value against the hints."""
    hints = typing.get_type_hints(func)
    names = func.__code__.co_varnames[: func.__code__.co_argcount]

    @functools.wraps(func)
    def wrapper(*args):
        for name, value in zip(names, args):
            expected = hints.get(name)
            if expected is not None and not isinstance(value, expected):
                raise TypeError(
                    f"{func.__name__}: {name} expected {expected.__name__}, "
                    f"got {type(value).__name__}"
                )
        result = func(*args)
        expected = hints.get("return")
        if expected is not None and not isinstance(result, expected):
            raise TypeError(
                f"{func.__name__}: declared -> {expected.__name__}, "
                f"returned {type(result).__name__}"
            )
        return result

    wrapper.hints = {name: cls.__name__ for name, cls in hints.items()}
    return wrapper


@checked
def repeat(text: str, times: int) -> str:
    return text * times


@checked
def miscounts(text: str) -> str:
    return len(text)


def main() -> None:
    print("hints read off the function:", repeat.hints)
    print("valid call:", repeat("ab", 3))

    try:
        repeat("ab", "3")
    except TypeError as exc:
        print("argument rejected:", exc)

    try:
        miscounts("abc")
    except TypeError as exc:
        print("return rejected:  ", exc)

    print("without the decorator nothing would have been checked:",
          typing.get_type_hints(miscounts)["return"].__name__)


if __name__ == "__main__":
    main()
