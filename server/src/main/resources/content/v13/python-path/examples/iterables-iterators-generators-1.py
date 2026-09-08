"""An iterable can be asked for an iterator; an iterator is consumed once."""


def squares(limit: int):
    for n in range(limit):
        yield n * n


def main() -> None:
    values = [1, 2, 3]
    first = iter(values)
    second = iter(values)
    print("iter(list) returns something new each time:", first is not second)
    print("iter(iterator) returns the iterator itself:", iter(first) is first)

    print(next(first), next(first), next(first))
    try:
        next(first)
    except StopIteration as exc:
        print("asking an exhausted iterator raises:", type(exc).__name__)

    print("the list can still be walked again:", [n for n in values])

    generated = squares(4)
    print("first pass over the generator:", list(generated))
    print("second pass over the generator:", list(generated))

    print("a generator has no length:", hasattr(squares(4), "__len__"))
    once = squares(4)
    print("a generator is its own iterator:", iter(once) is once)


if __name__ == "__main__":
    main()
