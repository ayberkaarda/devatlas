"""Attribute lookup order: type first for data descriptors, then the instance."""


class Config:
    source = "class attribute"

    @property
    def resolved(self) -> str:
        return "computed by the property"

    def __getattr__(self, name: str) -> str:
        return f"no attribute named {name!r}; falling back"


def main() -> None:
    config = Config()
    print("instance dictionary starts empty:", config.__dict__)
    print("read from the class:", config.source)

    config.source = "instance attribute"
    print("instance dictionary now:", config.__dict__)
    print("the instance wins:", config.source)
    print("the class is unchanged:", Config.source)

    config.__dict__["resolved"] = "smuggled into the instance dictionary"
    print("the entry really is there:", config.__dict__["resolved"])
    print("but attribute access gives:", config.resolved)

    try:
        config.resolved = "assigned normally"
    except AttributeError as exc:
        print("assigning to it raises:", type(exc).__name__)

    print("unknown attribute:", config.missing_entirely)

    del config.source
    print("after deleting the instance entry:", config.source)


if __name__ == "__main__":
    main()
