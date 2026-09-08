"""A mutable class attribute is shared by every instance of the class."""


class SharedLog:
    entries: list[str] = []          # one list, owned by the class

    def __init__(self, name: str) -> None:
        self.name = name

    def note(self, message: str) -> None:
        self.entries.append(message)


class OwnLog:
    def __init__(self, name: str) -> None:
        self.name = name
        self.entries: list[str] = []   # a new list per instance

    def note(self, message: str) -> None:
        self.entries.append(message)


def main() -> None:
    a, b = SharedLog("a"), SharedLog("b")
    a.note("from a")
    b.note("from b")
    print("a.entries:", a.entries)
    print("b.entries:", b.entries)
    print("a.entries is b.entries:", a.entries is b.entries)
    print("the class holds them too:", SharedLog.entries)

    c, d = OwnLog("c"), OwnLog("d")
    c.note("from c")
    d.note("from d")
    print("c.entries:", c.entries)
    print("d.entries:", d.entries)
    print("c.entries is d.entries:", c.entries is d.entries)

    a.entries = ["rebound on the instance"]
    print("after assigning to a.entries, the class list is:", SharedLog.entries)
    print("and a now has its own:", a.entries is SharedLog.entries)


if __name__ == "__main__":
    main()
