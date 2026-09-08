// Which operators stream and which must buffer, and what the terminal
// operators do at the edges. C# 14, .NET 10.

var source = new[] { 5, 3, 8, 1, 9, 2 };

var projected = 0;
int Project(int n)
{
    projected++;
    return n;
}

// Where and Select stream: First() stops the whole chain at the first match.
projected = 0;
var firstBig = source.Select(Project).Where(n => n > 4).First();
Console.WriteLine($"First on a streaming chain:  value={firstBig} projections={projected}");

// Take is the same story with a bound.
projected = 0;
var firstTwo = source.Select(Project).Take(2).ToList();
Console.WriteLine($"Take(2):                     count={firstTwo.Count} projections={projected}");

// OrderBy cannot yield anything until it has seen everything, so the same
// First() now pays for the whole source.
projected = 0;
var smallest = source.Select(Project).OrderBy(n => n).First();
Console.WriteLine($"First after OrderBy:         value={smallest} projections={projected}");

// GroupBy is buffering for the same reason: a group is not complete until the
// source is exhausted, so the first group cannot be yielded before the last
// element has been read.
projected = 0;
var firstGroup = source.Select(Project).GroupBy(n => n % 2).First();
Console.WriteLine($"First after GroupBy:         key={firstGroup.Key} projections={projected}");

// Ordering is part of the contract where the operator specifies it. OrderBy is
// documented as a stable sort, so equal keys keep their source order.
var words = new[] { "bee", "ant", "cow", "ape", "bat" };
var byLength = words.OrderBy(w => w.Length).ThenBy(w => w).ToList();
Console.WriteLine($"OrderBy then ThenBy:         {string.Join(",", byLength)}");

var byFirstLetter = words.OrderBy(w => w[0]).ToList();
Console.WriteLine($"stable on equal keys:        {string.Join(",", byFirstLetter)}");

// The terminal operators differ in what they do when the answer is not there.
Console.WriteLine($"FirstOrDefault on empty:     {Array.Empty<int>().FirstOrDefault()}");
Console.WriteLine($"First on empty:              {Catch(() => Array.Empty<int>().First())}");
Console.WriteLine($"Single on two matches:       {Catch(() => source.Where(n => n > 4).Single())}");
Console.WriteLine($"SingleOrDefault on two:      {Catch(() => source.Where(n => n > 4).SingleOrDefault())}");
Console.WriteLine($"Single on exactly one:       {source.Where(n => n > 8).Single()}");
Console.WriteLine($"Max on empty:                {Catch(() => Array.Empty<int>().Max())}");
Console.WriteLine($"Sum on empty:                {Array.Empty<int>().Sum()}");

// Only the exception's type is printed: the message text belongs to the
// library and can be reworded between versions.
static string Catch(Action action)
{
    try
    {
        action();
        return "no exception";
    }
    catch (Exception ex)
    {
        return ex.GetType().Name;
    }
}
