"""Two names for one object, and the difference between rebinding and mutating.

Identity is printed as a property (`a is b`) rather than as an id() value:
id() results are addresses and are not reproducible between runs.
"""


def main() -> None:
    a = [1, 2, 3]
    b = a
    print("a is b after 'b = a':", a is b)

    b.append(4)
    print("mutated through b, read through a:", a)

    b = b + [5]
    print("after 'b = b + [5]', a is b:", a is b)
    print("a:", a)
    print("b:", b)

    c = [1, 2, 3]
    d = [1, 2, 3]
    print("equal lists:", c == d)
    print("same object:", c is d)

    x = 5
    y = x
    x = 6
    print("integers are immutable, so y kept its value:", y)


if __name__ == "__main__":
    main()
