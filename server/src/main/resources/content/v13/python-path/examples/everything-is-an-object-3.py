"""__slots__ removes the per-instance dictionary, and with it the typo."""


class Loose:
    def __init__(self, host: str, port: int) -> None:
        self.host = host
        self.port = port


class Tight:
    __slots__ = ("host", "port")

    def __init__(self, host: str, port: int) -> None:
        self.host = host
        self.port = port


def main() -> None:
    loose = Loose("db.internal", 5432)
    loose.prot = 5433                      # a typo, accepted in silence
    print("the typo was stored:", sorted(loose.__dict__))
    print("the real port is still:", loose.port)

    tight = Tight("db.internal", 5432)
    try:
        tight.prot = 5433
    except AttributeError as exc:
        print("the same typo raises:", type(exc).__name__)

    print("declared slots:", Tight.__slots__)
    print("a slotted instance has no __dict__:", hasattr(tight, "__dict__"))
    print("its attributes still read normally:", tight.host, tight.port)

    tight.port = 6543
    print("and still write normally:", tight.port)


if __name__ == "__main__":
    main()
