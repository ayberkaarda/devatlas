"""A closure stores the variable, not the value the variable had."""


def build_late() -> list:
    handlers = []
    for name in ("alpha", "beta", "gamma"):
        handlers.append(lambda: name)
    return handlers


def build_with_default() -> list:
    handlers = []
    for name in ("alpha", "beta", "gamma"):
        handlers.append(lambda bound=name: bound)
    return handlers


def build_with_factory() -> list:
    def make(bound: str):
        return lambda: bound

    return [make(name) for name in ("alpha", "beta", "gamma")]


def main() -> None:
    print("closing over the loop variable:", [h() for h in build_late()])
    print("captured by default argument:", [h() for h in build_with_default()])
    print("captured by a factory call:  ", [h() for h in build_with_factory()])

    counter = {"calls": 0}

    def memoised(n: int, cache: dict[int, int] = {}) -> int:
        counter["calls"] += 1
        if n not in cache:
            cache[n] = n * n
        return cache[n]

    print(memoised(4), memoised(4), memoised(5))
    print("body entered", counter["calls"], "times, cache holds", len(memoised.__defaults__[0]), "entries")


if __name__ == "__main__":
    main()
