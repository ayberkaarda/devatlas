def add_item(item, basket=None):
    if basket is None:
        basket = []
    basket.append(item)
    return basket

first = add_item("apple")
second = add_item("pear")
print(first)
print(second)
