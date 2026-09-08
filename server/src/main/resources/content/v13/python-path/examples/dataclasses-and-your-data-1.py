"""A dict accepts any key. A dataclass accepts the fields it declares."""

from dataclasses import dataclass


@dataclass
class Endpoint:
    host: str
    port: int
    scheme: str = "https"


def main() -> None:
    as_dict = {"host": "db.internal", "port": 5432}
    as_dict["prot"] = 5433                      # a typo, stored without complaint
    print("dict keys after the typo:", sorted(as_dict))
    print("reading the field that was meant:", as_dict.get("port"))

    try:
        print(as_dict["portt"])
    except KeyError as exc:
        print("a misread key raises:", type(exc).__name__, exc)

    endpoint = Endpoint("db.internal", 5432)
    print("repr for free:", endpoint)
    print("equality by value:", endpoint == Endpoint("db.internal", 5432))
    print("default applied:", endpoint.scheme)

    try:
        Endpoint("db.internal", 5432, prot="http")
    except TypeError as exc:
        print("the same typo raises:", type(exc).__name__)
        print("message:", exc)

    try:
        Endpoint("db.internal")
    except TypeError as exc:
        print("a missing field raises:", type(exc).__name__)
        print("message:", exc)


if __name__ == "__main__":
    main()
