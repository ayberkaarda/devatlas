// A LINQ query is a description, not a result. C# 14, .NET 10.

var calls = 0;
bool IsEven(int n)
{
    calls++;
    return n % 2 == 0;
}

var numbers = new List<int> { 1, 2, 3, 4, 5, 6 };

// Building the query runs nothing.
var evens = numbers.Where(IsEven);
Console.WriteLine($"predicate calls after building the query: {calls}");

// Enumerating runs it.
var first = evens.ToList();
Console.WriteLine($"predicate calls after one enumeration:    {calls}");
Console.WriteLine($"result:                                   {string.Join(",", first)}");

// Enumerating again runs it again. The query object holds no results.
var second = evens.ToList();
Console.WriteLine($"predicate calls after two enumerations:   {calls}");
Console.WriteLine($"same answer both times:                   {first.SequenceEqual(second)}");

// Count() enumerates too, and so does foreach. Three passes over one query
// means three passes over the source.
calls = 0;
Console.WriteLine($"count:                                    {evens.Count()}");
Console.WriteLine($"any:                                      {evens.Any()}");
foreach (var _ in evens)
{
}

Console.WriteLine($"predicate calls for Count, Any, foreach:  {calls}");

// Materialising once turns the description into data.
calls = 0;
var materialised = numbers.Where(IsEven).ToList();
Console.WriteLine($"predicate calls for ToList:               {calls}");
Console.WriteLine($"count:                                    {materialised.Count}");
Console.WriteLine($"any:                                      {materialised.Count > 0}");
foreach (var _ in materialised)
{
}

Console.WriteLine($"predicate calls after using it thrice:    {calls}");

// Any() stops at the first match; Count() cannot.
calls = 0;
_ = numbers.Where(IsEven).Any();
Console.WriteLine($"predicate calls for Any on the source:    {calls}");
calls = 0;
_ = numbers.Where(IsEven).Count();
Console.WriteLine($"predicate calls for Count on the source:  {calls}");
