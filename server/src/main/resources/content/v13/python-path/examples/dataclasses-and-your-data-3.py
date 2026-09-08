"""default_factory, __post_init__, and the mutable default that is refused."""

from dataclasses import asdict, dataclass, field, fields


@dataclass
class Basket:
    owner: str
    items: list[str] = field(default_factory=list)
    note: str = field(default="", repr=False)

    def __post_init__(self) -> None:
        self.owner = self.owner.strip().title()


def declare_with_mutable_default():
    @dataclass
    class Broken:
        items: list[str] = []
    return Broken


def main() -> None:
    first, second = Basket("  ada  "), Basket("grace")
    first.items.append("apple")
    print("first:", first)
    print("second:", second)
    print("each got its own list:", first.items is not second.items)
    print("__post_init__ normalised the owner:", repr(first.owner))
    print("note is excluded from repr but present:", repr(first.note))

    try:
        declare_with_mutable_default()
    except ValueError as exc:
        print("declaring a list default raises:", type(exc).__name__)
        print("message:", exc)

    print("field names:", [f.name for f in fields(Basket)])
    print("asdict:", asdict(first))
    print("asdict copies the list:", asdict(first)["items"] is not first.items)


if __name__ == "__main__":
    main()
