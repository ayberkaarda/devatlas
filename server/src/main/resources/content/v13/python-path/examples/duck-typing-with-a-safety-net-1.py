"""An annotation is a claim. The interpreter does not check it."""

import typing


def normalise(code: str) -> str:
    return code            # the caller may pass anything at all


def main() -> None:
    hints = typing.get_type_hints(normalise)
    print("declared parameter type:", hints["code"].__name__)
    print("declared return type:   ", hints["return"].__name__)

    good = normalise("AB")
    print("with a string:", good, "| runtime type:", type(good).__name__)

    bad = normalise(42)
    print("with an int:  ", bad, "| runtime type:", type(bad).__name__)
    print("the annotation still says:", hints["return"].__name__)

    try:
        print(bad.lower())
    except AttributeError as exc:
        print("the failure lands here, not at the call:", type(exc).__name__)
        print("message:", exc)

    def add(a: int, b: int) -> int:
        return a + b

    print("annotated for ints, handed strings:", add("x", "y"))
    print("annotated for ints, handed lists: ", add([1], [2]))


if __name__ == "__main__":
    main()
