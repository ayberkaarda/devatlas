names = ["ada", "grace", "linus"]
kept = []

for name in names:
    if len(name) > 4:
        kept.append(name)

names[:] = kept
print(names)
