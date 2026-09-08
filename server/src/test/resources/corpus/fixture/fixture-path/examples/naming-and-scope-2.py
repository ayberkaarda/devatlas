def make_counter():
    count = 0

    def increment():
        nonlocal count
        count += 1
        return count

    return increment

first = make_counter()
print(first())
print(first())
print(first())
