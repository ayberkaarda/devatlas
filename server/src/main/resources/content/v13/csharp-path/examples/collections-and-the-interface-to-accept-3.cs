// Choosing the collection, and the variance that comes with the interface.
// C# 14, .NET 10.

var words = new[] { "bee", "ant", "bee", "cow", "ant" };

// The choice is made on the property you need, not on the shape of the code.
// Nothing below prints an enumeration order for a set or a dictionary, because
// neither type specifies one.
var list = new List<string>(words);
var set = new HashSet<string>(words);
var counts = new Dictionary<string, int>();
foreach (var word in words)
{
    counts[word] = counts.GetValueOrDefault(word) + 1;
}

Console.WriteLine($"list keeps every occurrence:   {list.Count}");
Console.WriteLine($"list keeps insertion order:    {string.Join(",", list)}");
Console.WriteLine($"set keeps one of each:         {set.Count}");
Console.WriteLine($"set membership:                {set.Contains("cow")} {set.Contains("dog")}");
Console.WriteLine($"dictionary keys:               {counts.Count}");
Console.WriteLine($"count for 'bee':               {counts["bee"]}");
Console.WriteLine($"sorted pairs:                  {string.Join(",", counts.OrderBy(p => p.Key).Select(p => $"{p.Key}={p.Value}"))}");

// IEnumerable<out T> is covariant, so a sequence of strings is usable wherever
// a sequence of objects is expected. That is safe because the interface has no
// method that accepts a T.
IEnumerable<string> strings = list;
IEnumerable<object> objects = strings;
Console.WriteLine($"IEnumerable<string> as IEnumerable<object>: {objects.Count()}");

// IList<T> is invariant for the opposite reason: it has an Add that accepts a
// T, so the same conversion is refused at compile time (CS0266).

// Arrays were made covariant before generics existed, and the hole is checked
// at run time instead of compile time.
object[] asObjects = words;
Console.WriteLine($"string[] assigned to object[]: {asObjects.Length}");
try
{
    asObjects[0] = 42;
}
catch (ArrayTypeMismatchException ex)
{
    Console.WriteLine($"writing an int into it threw:  {ex.GetType().Name}");
}

// Accepting the narrowest interface that carries what you need keeps callers
// free. Each of these compiles for every one of the collections above.
Console.WriteLine($"Describe(list):  {Describe(list)}");
Console.WriteLine($"Describe(set):   {Describe(set)}");
Console.WriteLine($"Describe(array): {Describe(words)}");
Console.WriteLine($"Describe(query): {Describe(words.Where(w => w.Length == 3))}");

static string Describe(IEnumerable<string> values)
{
    var total = 0;
    var longest = 0;
    foreach (var value in values)
    {
        total++;
        longest = Math.Max(longest, value.Length);
    }

    return $"{total} values, longest {longest}";
}
