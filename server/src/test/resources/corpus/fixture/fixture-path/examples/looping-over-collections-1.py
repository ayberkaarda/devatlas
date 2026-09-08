def squares(limit):
    n = 0
    while n < limit:
        yield n * n
        n += 1

for value in squares(5):
    print(value)
