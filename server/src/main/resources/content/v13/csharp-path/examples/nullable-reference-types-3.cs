// Telling the analysis what a method guarantees, and where the guarantee stops.
// C# 14, .NET 10.

using System.Diagnostics.CodeAnalysis;

var settings = new Dictionary<string, string> { ["host"] = "example.internal" };

// [NotNullWhen(true)] is what lets the compiler dereference 'host' inside the
// branch: the attribute states the postcondition the signature alone cannot.
if (TryGet(settings, "host", out var host))
{
    Console.WriteLine($"found, and dereferenced without a warning: {host.Length}");
}

Console.WriteLine($"missing key returns false: {TryGet(settings, "port", out _)}");

// [MemberNotNullWhen] does the same for a nullable member that a property
// vouches for, so the true branch may dereference it.
var box = new LazyBox();
Console.WriteLine($"before loading, IsLoaded:  {box.IsLoaded}");
box.Load("payload");
Console.WriteLine($"after loading, IsLoaded:   {box.IsLoaded}");
Console.WriteLine($"and the value is readable: {(box.IsLoaded ? box.Value.Length : -1)}");

// An unconstrained T has no annotation to give. 'T?' there means "may be
// default", and default is null only when T is a reference type.
Console.WriteLine($"FirstOrNone on an empty string[]: {Describe(FirstOrNone(Array.Empty<string>()))}");
Console.WriteLine($"FirstOrNone on an empty int[]:    {Describe(FirstOrNone(Array.Empty<int>()))}");
Console.WriteLine($"FirstOrNone on one item:          {Describe(FirstOrNone(new[] { "only" }))}");

// So a caller that wants "absent" to be distinguishable for every T asks for
// it in the return type instead of relying on default.
Console.WriteLine($"TryFirst on an empty int[]:       {TryFirst(Array.Empty<int>(), out _)}");
Console.WriteLine($"TryFirst on a filled int[]:       {TryFirst(new[] { 7 }, out var first)} value={first}");

static bool TryGet(Dictionary<string, string> source, string key, [NotNullWhen(true)] out string? value)
    => source.TryGetValue(key, out value);

static T? FirstOrNone<T>(IReadOnlyList<T> items) => items.Count > 0 ? items[0] : default;

static bool TryFirst<T>(IReadOnlyList<T> items, out T value)
{
    if (items.Count > 0)
    {
        value = items[0];
        return true;
    }

    value = default!;
    return false;
}

static string Describe<T>(T? value) => value is null ? "none" : $"'{value}'";

class LazyBox
{
    public string? Value { get; private set; }

    [MemberNotNullWhen(true, nameof(Value))]
    public bool IsLoaded => Value is not null;

    public void Load(string value) => Value = value;
}
