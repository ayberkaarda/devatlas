"""__enter__ runs on the way in; __exit__ runs on every way out."""


class Trace:
    def __init__(self, name: str, swallow: bool = False) -> None:
        self.name = name
        self.swallow = swallow

    def __enter__(self) -> "Trace":
        print("enter", self.name)
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> bool:
        label = exc_type.__name__ if exc_type is not None else "none"
        print("exit ", self.name, "| exception:", label)
        return self.swallow


def main() -> None:
    with Trace("outer"):
        with Trace("inner"):
            print("body of inner")

    print("---")

    try:
        with Trace("outer"):
            with Trace("inner"):
                raise ValueError("something went wrong")
    except ValueError as exc:
        print("caught outside both:", exc)

    print("---")

    with Trace("swallower", swallow=True):
        raise KeyError("never leaves the block")
    print("execution continued: __exit__ returned True")

    print("---")
    with Trace("a"), Trace("b"):
        print("one with statement, two managers")


if __name__ == "__main__":
    main()
