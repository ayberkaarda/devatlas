def parse_age(raw):
    try:
        return int(raw)
    except ValueError as exc:
        print(f"invalid age: {exc}")
        return None

print(parse_age("30"))
print(parse_age("thirty"))
