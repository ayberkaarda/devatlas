"""A Protocol describes a shape; runtime_checkable lets isinstance test it."""

from typing import Protocol, runtime_checkable


@runtime_checkable
class Closeable(Protocol):
    def close(self) -> None: ...


@runtime_checkable
class Named(Protocol):
    name: str


class Socket:
    """Never mentions Closeable, and satisfies it anyway."""

    def close(self) -> None:
        print("  socket closed")


class Rock:
    pass


class Person:
    def __init__(self, name: str) -> None:
        self.name = name


def shut_down(resource: Closeable) -> None:
    resource.close()


def main() -> None:
    print("Socket satisfies Closeable:", isinstance(Socket(), Closeable))
    print("Rock satisfies Closeable:  ", isinstance(Rock(), Closeable))
    print("Socket inherits from Closeable:", Closeable in Socket.__mro__)

    shut_down(Socket())

    try:
        shut_down(Rock())
    except AttributeError as exc:
        print("without the check:", type(exc).__name__, "|", exc)

    print("Person satisfies Named:", isinstance(Person("ada"), Named))
    print("Rock satisfies Named:  ", isinstance(Rock(), Named))
    print("Person's bases:", [cls.__name__ for cls in Person.__mro__])


if __name__ == "__main__":
    main()
