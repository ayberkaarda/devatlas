"""Classes and functions are objects, and a class is an instance of a type."""


class Base:
    kind = "base"


class Child(Base):
    kind = "child"


def area(width: int, height: int) -> int:
    return width * height


def main() -> None:
    print("type of an instance:", type(Child()).__name__)
    print("type of the class:   ", type(Child).__name__)
    print("type of that type:   ", type(type).__name__)
    print("a class is an object:", isinstance(Child, object))

    print("method resolution order:", [cls.__name__ for cls in Child.__mro__])

    made = type("Made", (Base,), {"kind": "made at runtime"})
    print("built by calling type():", made.__name__, "|", made.kind)
    print("and it inherits:", [cls.__name__ for cls in made.__mro__])

    print("a function has a name attribute:", area.__name__)
    area.unit = "square metres"
    print("and accepts new attributes:", area.unit)

    operations = {"area": area}
    print("stored in a dict and called:", operations["area"](3, 4))


if __name__ == "__main__":
    main()
