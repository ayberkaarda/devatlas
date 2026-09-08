// A struct stored inside something else is stored by value, so every way of
// reading it back hands you a copy. C# 14, .NET 10.

var list = new List<Counter> { new Counter { Hits = 0 } };

// list[0] returns a copy; the increment lands on the copy and is discarded.
var copy = list[0];
copy.Hits++;
Console.WriteLine($"after mutating a copy taken from the list: {list[0].Hits}");

// Writing the whole element back is the only way to change what is stored.
var edited = list[0];
edited.Hits++;
list[0] = edited;
Console.WriteLine($"after writing the element back:            {list[0].Hits}");

// An array is different in kind: indexing an array of structs produces a
// variable, not a value, so the element can be mutated in place.
var arr = new Counter[] { new Counter { Hits = 0 } };
arr[0].Hits++;
Console.WriteLine($"array element mutated in place:            {arr[0].Hits}");

// A class element is a reference either way, so both containers agree.
var refList = new List<RefCounter> { new RefCounter() };
refList[0].Hits++;
Console.WriteLine($"class element through a List indexer:      {refList[0].Hits}");

// 'readonly struct' asks the compiler to reject the mutation instead of
// silently applying it to a copy.
var frozen = new ReadonlyCounter(7);
Console.WriteLine($"readonly struct value:                     {frozen.Hits}");
Console.WriteLine($"readonly struct 'increment' returns a new value: {frozen.Increment().Hits}");
Console.WriteLine($"and leaves the original alone:                  {frozen.Hits}");

struct Counter
{
    public int Hits;
}

class RefCounter
{
    public int Hits;
}

readonly struct ReadonlyCounter(int hits)
{
    public int Hits { get; } = hits;

    public ReadonlyCounter Increment() => new ReadonlyCounter(Hits + 1);
}
