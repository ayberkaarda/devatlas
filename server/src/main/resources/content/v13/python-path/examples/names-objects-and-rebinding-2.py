"""What a function receives, and what an assignment inside it can change.

A call binds the argument object to a new local name. Mutating the object is
visible to the caller; rebinding the local name is not.
"""


def mutate(values: list[int]) -> None:
    values.append(99)


def rebind(values: list[int]) -> None:
    values = values + [99]
    print("  inside rebind, local list:", values)


def replace_contents(values: list[int]) -> None:
    values[:] = [7, 8]


def main() -> None:
    numbers = [1, 2]
    mutate(numbers)
    print("after mutate:", numbers)

    numbers = [1, 2]
    rebind(numbers)
    print("after rebind:", numbers)

    numbers = [1, 2]
    replace_contents(numbers)
    print("after slice assignment:", numbers)

    text = "abc"

    def shout(s: str) -> None:
        s = s.upper()
        print("  inside shout:", s)

    shout(text)
    print("after shout:", text)


if __name__ == "__main__":
    main()
