"""raise ... from: keeping the cause instead of throwing it away."""


class ConfigError(Exception):
    pass


def read_port(raw: str) -> int:
    try:
        return int(raw)
    except ValueError as exc:
        raise ConfigError(f"port must be a whole number, got {raw!r}") from exc


def read_port_losing_cause(raw: str) -> int:
    try:
        return int(raw)
    except ValueError:
        raise ConfigError(f"port must be a whole number, got {raw!r}") from None


def read_port_implicit(raw: str) -> int:
    try:
        return int(raw)
    except ValueError:
        raise ConfigError(f"port must be a whole number, got {raw!r}")


def describe(func, raw: str) -> None:
    try:
        func(raw)
    except ConfigError as exc:
        cause = type(exc.__cause__).__name__ if exc.__cause__ else "none"
        context = type(exc.__context__).__name__ if exc.__context__ else "none"
        print(f"{func.__name__}:")
        print("  message:            ", exc)
        print("  __cause__:          ", cause)
        print("  __context__:        ", context)
        print("  __suppress_context__:", exc.__suppress_context__)


def main() -> None:
    print("good input:", read_port("8080"))
    describe(read_port, "eight thousand")
    describe(read_port_losing_cause, "eight thousand")
    describe(read_port_implicit, "eight thousand")

    try:
        read_port("nope")
    except ConfigError as exc:
        print("the original text is still reachable:", exc.__cause__)
        print("and its type is:", type(exc.__cause__).__name__)


if __name__ == "__main__":
    main()
