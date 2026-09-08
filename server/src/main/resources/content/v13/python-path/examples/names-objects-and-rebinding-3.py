"""The augmented assignment that raises and mutates in the same statement.

`t[0] += [3]` is read-modify-write: list.__iadd__ extends the list in place and
then the interpreter tries to store the result back into the tuple slot.
The store fails; the extension already happened.
"""


def main() -> None:
    holder = ([1, 2],)
    print("before:", holder)

    try:
        holder[0] += [3]
    except TypeError as exc:
        print("raised:", type(exc).__name__)
        print("message:", exc)

    print("after the failed statement:", holder)

    plain = [1, 2]
    same = plain
    plain += [3]
    print("list += keeps the same object:", plain is same)

    text = "ab"
    same_text = text
    text += "c"
    print("str += produces a new object:", text is same_text)
    print("the other name still sees:", same_text)


if __name__ == "__main__":
    main()
