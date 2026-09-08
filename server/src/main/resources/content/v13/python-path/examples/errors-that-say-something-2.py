"""The bare except catches things you never meant to catch."""


def total_with_bare_except(rows: list[dict]) -> int:
    total = 0
    for row in rows:
        try:
            total += row["amont"]        # a typo: the key is "amount"
        except:                          # noqa: E722 - the mistake under study
            pass
    return total


def total_with_specific_except(rows: list[dict]) -> int:
    total = 0
    for row in rows:
        try:
            total += row["amont"]
        except KeyError as exc:
            print("  missing key:", exc)
    return total


def shutdown_swallowed() -> str:
    try:
        raise SystemExit(3)
    except:                              # noqa: E722 - the mistake under study
        return "the interpreter was told to exit and nobody let it"


def shutdown_respected() -> str:
    try:
        raise SystemExit(3)
    except Exception:
        return "not reached"


ROWS = [{"amount": 10}, {"amount": 5}, {"amount": 7}]


def main() -> None:
    print("bare except total:", total_with_bare_except(ROWS))
    print("expected total:   ", sum(r["amount"] for r in ROWS))
    print("the bug was silent:", total_with_bare_except(ROWS) == 0)

    print("---")
    print("specific except total:", total_with_specific_except(ROWS))

    print("---")
    print(shutdown_swallowed())
    print("SystemExit is not an Exception:", issubclass(SystemExit, Exception))
    print("it is a BaseException:", issubclass(SystemExit, BaseException))

    try:
        shutdown_respected()
    except SystemExit as exc:
        print("except Exception let it through, code:", exc.code)


if __name__ == "__main__":
    main()
