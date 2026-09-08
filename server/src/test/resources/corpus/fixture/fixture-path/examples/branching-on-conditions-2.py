def describe(score):
    if score is None:
        return "not graded yet"
    elif score >= 90:
        return "excellent"
    elif score >= 60:
        return "passing"
    else:
        return "needs work"

print(describe(None))
print(describe(0))
print(describe(75))
