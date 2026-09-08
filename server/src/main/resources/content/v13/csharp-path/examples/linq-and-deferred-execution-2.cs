// Deferred means "read the source later", and later is when things have moved.
// C# 14, .NET 10.

var numbers = new List<int> { 1, 2, 3 };
var doubled = numbers.Select(n => n * 2);

Console.WriteLine($"before adding:     {string.Join(",", doubled)}");
numbers.Add(4);
Console.WriteLine($"after adding 4:    {string.Join(",", doubled)}");

// The query captured the variable, not its value. Changing the variable
// changes what the same query object produces.
var threshold = 2;
var above = numbers.Where(n => n > threshold);
Console.WriteLine($"threshold 2:       {string.Join(",", above)}");
threshold = 3;
Console.WriteLine($"threshold 3:       {string.Join(",", above)}");

// Materialising fixes the answer at the moment of materialisation.
threshold = 2;
var snapshot = numbers.Where(n => n > threshold).ToList();
threshold = 3;
Console.WriteLine($"snapshot taken at threshold 2: {string.Join(",", snapshot)}");

// Mutating the source while a query over it is being enumerated is not
// deferred execution being clever; it is a documented failure.
try
{
    foreach (var n in numbers.Where(n => n > 0))
    {
        if (n == 1)
        {
            numbers.Add(99);
        }
    }
}
catch (InvalidOperationException ex)
{
    Console.WriteLine($"mutating during enumeration: {ex.GetType().Name}");
}

// A query over an iterator method shows the same thing from the other side:
// nothing in the method body runs until something asks for an element.
var log = new List<string>();
var lazy = Generate(log);
Console.WriteLine($"log entries before enumerating: {log.Count}");
Console.WriteLine($"first element:                  {lazy.First()}");
Console.WriteLine($"log entries after First():      {log.Count}");
var all = lazy.ToList();
Console.WriteLine($"elements from ToList():         {all.Count}");
Console.WriteLine($"log entries after a full pass:  {log.Count}");

static IEnumerable<int> Generate(List<string> log)
{
    log.Add("started");
    yield return 1;
    log.Add("resumed");
    yield return 2;
    log.Add("finished");
}
