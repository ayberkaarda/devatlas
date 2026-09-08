"""ExitStack for a number of resources known only at run time."""

import contextlib


class Resource:
    def __init__(self, name: str) -> None:
        self.name = name

    def __enter__(self) -> "Resource":
        print("open ", self.name)
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> bool:
        print("close", self.name)
        return False


def main() -> None:
    wanted = ["config", "cache", "socket"]

    with contextlib.ExitStack() as stack:
        opened = [stack.enter_context(Resource(name)) for name in wanted]
        print("opened:", [r.name for r in opened])

    print("---")

    try:
        with contextlib.ExitStack() as stack:
            for name in wanted:
                stack.enter_context(Resource(name))
            raise OSError("the third one failed to be useful")
    except OSError as exc:
        print("still closed everything, then propagated:", exc)

    print("---")

    with contextlib.suppress(ZeroDivisionError):
        print("about to divide by zero")
        _ = 1 / 0
        print("this line never runs")
    print("suppress swallowed only the listed exception")

    try:
        with contextlib.suppress(ZeroDivisionError):
            raise ValueError("not in the list")
    except ValueError as exc:
        print("passed through:", exc)


if __name__ == "__main__":
    main()
