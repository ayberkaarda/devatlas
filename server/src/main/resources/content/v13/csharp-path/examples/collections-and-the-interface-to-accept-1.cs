// What a parameter type asks of the caller, and what it costs the callee.
// C# 14, .NET 10.

using System.Globalization;

// double formats itself with the ambient culture, so pin it rather than record
// whatever this machine happens to be configured for.
CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;

var source = new CountingSource([1, 2, 3, 4]);

// IEnumerable<T> accepts anything, including a query that has not run yet.
// A method that enumerates it twice pays for the source twice.
Console.WriteLine($"average (two passes): {AverageTwice(source)}");
Console.WriteLine($"enumerations so far:  {source.Enumerations}");

source.Reset();
Console.WriteLine($"average (one pass):   {AverageOnce(source)}");
Console.WriteLine($"enumerations so far:  {source.Enumerations}");

// Asking for IReadOnlyCollection<T> instead moves the requirement into the
// signature: the caller must hand over something already counted.
var materialised = new List<int> { 1, 2, 3, 4 };
Console.WriteLine($"count without enumerating: {CountCheaply(materialised)}");

// The cost of the narrower parameter is that a lazy caller has to materialise,
// which is a decision the caller is better placed to make than the callee.
source.Reset();
Console.WriteLine($"count via ToList:     {CountCheaply(source.ToList())}");
Console.WriteLine($"enumerations for that: {source.Enumerations}");

// The widest useful parameter is still IEnumerable<T> when one pass is enough,
// because it accepts arrays, lists, sets, dictionaries and queries alike.
Console.WriteLine($"array:      {AverageOnce([1, 2, 3, 4])}");
Console.WriteLine($"hash set:   {AverageOnce(new HashSet<int> { 1, 2, 3, 4 })}");
Console.WriteLine($"query:      {AverageOnce(Enumerable.Range(1, 4))}");

static double AverageTwice(IEnumerable<int> values) => (double)values.Sum() / values.Count();

static double AverageOnce(IEnumerable<int> values)
{
    var sum = 0;
    var count = 0;
    foreach (var value in values)
    {
        sum += value;
        count++;
    }

    return count == 0 ? 0 : (double)sum / count;
}

static int CountCheaply(IReadOnlyCollection<int> values) => values.Count;

sealed class CountingSource(int[] items) : IEnumerable<int>
{
    public int Enumerations { get; private set; }

    public void Reset() => Enumerations = 0;

    public IEnumerator<int> GetEnumerator()
    {
        Enumerations++;
        foreach (var item in items)
        {
            yield return item;
        }
    }

    System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() => GetEnumerator();
}
