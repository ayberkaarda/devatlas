def summarize(title, *values, **labels):
    print(title)
    print(values)
    print(labels)

summarize("scores", 10, 20, 30, curve="none", passed=True)
