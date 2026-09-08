"""frozen, order and slots: a value object that can be a key and be sorted."""

from dataclasses import FrozenInstanceError, dataclass, replace


@dataclass(frozen=True, order=True, slots=True)
class Version:
    major: int
    minor: int
    patch: int = 0


def main() -> None:
    releases = [Version(1, 10), Version(1, 2), Version(2, 0), Version(1, 2, 3)]
    print("sorted:", sorted(releases))

    print("equal by value:", Version(1, 2) == Version(1, 2))
    print("usable as a set member:", len({Version(1, 2), Version(1, 2), Version(2, 0)}))

    notes = {Version(1, 2): "first stable", Version(2, 0): "breaking"}
    print("looked up by an equal but distinct object:", notes[Version(1, 2)])

    current = Version(1, 2)
    try:
        current.minor = 3
    except FrozenInstanceError as exc:
        print("mutation raises:", type(exc).__name__)
        print("message:", exc)

    updated = replace(current, minor=3)
    print("replace builds a new one:", updated, "| original untouched:", current)

    print("slots declared:", Version.__slots__)
    print("no per-instance dictionary:", hasattr(current, "__dict__"))


if __name__ == "__main__":
    main()
