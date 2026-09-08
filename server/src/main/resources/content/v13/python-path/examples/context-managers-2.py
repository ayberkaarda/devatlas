"""contextlib.contextmanager: setup, yield, and cleanup that must be guarded."""

import contextlib

released: list[str] = []


@contextlib.contextmanager
def guarded(name: str):
    print("acquire", name)
    try:
        yield name
    finally:
        released.append(name)
        print("release", name)


@contextlib.contextmanager
def unguarded(name: str):
    print("acquire", name)
    yield name
    released.append(name)
    print("release", name)


def main() -> None:
    with guarded("first") as handle:
        print("body sees", handle)

    print("---")
    try:
        with guarded("second"):
            raise RuntimeError("failure inside the body")
    except RuntimeError as exc:
        print("propagated:", exc)

    print("---")
    try:
        with unguarded("third"):
            raise RuntimeError("failure inside the body")
    except RuntimeError as exc:
        print("propagated:", exc)

    print("---")
    print("released:", released)
    print("every guarded resource was released:", released.count("second") == 1)
    print("the unguarded one was not:", "third" not in released)


if __name__ == "__main__":
    main()
