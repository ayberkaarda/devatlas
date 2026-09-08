"""Where the memory goes, measured rather than asserted.

Absolute byte counts are implementation details, so this listing prints
properties that hold for any CPython build rather than the numbers themselves.
"""

import sys
import tracemalloc

SIZE = 200_000


def main() -> None:
    short = (n * n for n in range(10))
    lengthy = (n * n for n in range(10_000_000))
    print("generator object size does not depend on how much it will yield:",
          sys.getsizeof(short) == sys.getsizeof(lengthy))

    tracemalloc.start()
    materialised = [n * n for n in range(SIZE)]
    _, list_peak = tracemalloc.get_traced_memory()
    list_total = sum(materialised)
    del materialised

    tracemalloc.reset_peak()
    generator_total = sum(n * n for n in range(SIZE))
    _, generator_peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    print("both computed the same sum:", list_total == generator_total)
    print("the list needed more than one megabyte:", list_peak > 1_000_000)
    print("the generator needed under one percent of that:",
          generator_peak < list_peak / 100)


if __name__ == "__main__":
    main()
