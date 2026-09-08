a = [1, 2, 3]
b = a
b.append(4)
print(a)
print(b)
print(a is b)
c = a[:]
c.append(5)
print(a)
print(a is c)
