"""Laziness, counted: a generator produces exactly what is asked for."""

import itertools


def counted(limit: int, produced: list[int]):
    for n in range(limit):
        produced.append(n)
        yield n


def fibonacci():
    a, b = 0, 1
    while True:
        yield a
        a, b = b, a + b


def main() -> None:
    produced: list[int] = []
    taken = list(itertools.islice(counted(1_000_000, produced), 3))
    print("taken:", taken)
    print("items the source actually produced:", len(produced))

    produced.clear()
    everything = [n for n in counted(10, produced)]
    print("a list comprehension takes all of them:", len(everything))
    print("items the source actually produced:", len(produced))

    print("eight values from an endless sequence:", list(itertools.islice(fibonacci(), 8)))

    def narrated():
        print("  body starts running only now")
        yield "one"
        print("  body resumes where it stopped")
        yield "two"

    generator = narrated()
    print("calling the function ran no body code:", type(generator).__name__)
    print("got:", next(generator))
    print("got:", next(generator))


if __name__ == "__main__":
    main()
