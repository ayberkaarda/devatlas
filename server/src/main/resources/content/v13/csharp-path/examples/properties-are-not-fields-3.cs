// The getter runs on every read, and what it returns is a value.
// C# 14, .NET 10.

var doc = new Document();

// The getter builds a fresh list each time, so this Add lands on a list nobody
// keeps. No diagnostic, no exception, no effect.
doc.Tags.Add("draft");
Console.WriteLine($"tags after Add through the property: {doc.Tags.Count}");

// The same property read twice hands back two different lists.
Console.WriteLine($"two reads, same instance:            {ReferenceEquals(doc.Tags, doc.Tags)}");

// A property that exposes the stored collection behaves as the caller expects.
doc.StoredTags.Add("draft");
Console.WriteLine($"tags after Add through storage:      {doc.StoredTags.Count}");
Console.WriteLine($"two reads, same instance:            {ReferenceEquals(doc.StoredTags, doc.StoredTags)}");

// A struct-typed property returns a copy. Mutating it in place does not
// compile (CS1612); the read-modify-write below is the working form.
var box = new Box();
var size = box.Size;
size.Width = 42;
box.Size = size;
Console.WriteLine($"struct property after write-back:    {box.Size.Width}");

// Every read is a call, so a getter with a cost pays it every time. Hoisting
// the value into a local is the fix, and it is visible in the count.
var counted = new Counted();
var total = 0;
for (var i = 0; i < counted.Limit; i++)
{
    total += counted.Limit;
}

Console.WriteLine($"loop total:                          {total}");
Console.WriteLine($"getter calls for that loop:          {counted.Reads}");

counted.ResetCount();
var limit = counted.Limit;
total = 0;
for (var i = 0; i < limit; i++)
{
    total += limit;
}

Console.WriteLine($"loop total, hoisted:                 {total}");
Console.WriteLine($"getter calls when hoisted:           {counted.Reads}");

struct Size
{
    public int Width;
}

class Box
{
    public Size Size { get; set; }
}

class Document
{
    private readonly List<string> _tags = [];

    // A new list on every read: the caller's Add is discarded.
    public List<string> Tags => [.. _tags];

    // The stored list itself.
    public List<string> StoredTags => _tags;
}

class Counted
{
    public int Limit
    {
        get
        {
            Reads++;
            return 3;
        }
    }

    public int Reads { get; private set; }

    public void ResetCount() => Reads = 0;
}
