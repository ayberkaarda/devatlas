"""A default value is one object, created once, when the def is executed."""


def collect(item: str, into: list[str] = []) -> list[str]:
    into.append(item)
    return into


def collect_safely(item: str, into: list[str] | None = None) -> list[str]:
    if into is None:
        into = []
    into.append(item)
    return into


def main() -> None:
    print("call 1:", collect("a"))
    print("call 2:", collect("b"))
    print("call 3:", collect("c"))

    stored_default = collect.__defaults__[0]
    print("the stored default is the list callers keep getting:",
          stored_default is collect("d"))

    print("call 1:", collect_safely("a"))
    print("call 2:", collect_safely("b"))
    print("caller's own list is still honoured:", collect_safely("c", ["seed"]))
    print("the safe default is:", collect_safely.__defaults__[0])


if __name__ == "__main__":
    main()
