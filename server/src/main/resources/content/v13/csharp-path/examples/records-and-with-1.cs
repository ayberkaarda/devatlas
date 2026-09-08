// What a positional record generates for you. C# 14, .NET 10.

using System.Globalization;

// decimal formats itself with the ambient culture, so pin the culture rather
// than record whatever this machine happens to be configured for.
CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;

var a = new Money(12.50m, "EUR");
var b = new Money(12.50m, "EUR");
var c = new Money(12.50m, "GBP");

// Value equality: Equals, == and GetHashCode are all synthesised from the
// declared members.
Console.WriteLine($"same values are Equal:   {a.Equals(b)}");
Console.WriteLine($"same values compare ==:  {a == b}");
Console.WriteLine($"but are distinct objects: {ReferenceEquals(a, b)}");
Console.WriteLine($"different values:        {a == c}");
Console.WriteLine($"equal values, equal hash: {a.GetHashCode() == b.GetHashCode()}");

// ToString is synthesised in the form documented for records:
// TypeName { Member = value, ... }
Console.WriteLine($"ToString:                {a}");

// Deconstruct is synthesised for a positional record, so pattern matching and
// tuple-style destructuring both work.
var (amount, currency) = a;
Console.WriteLine($"deconstructed:           {amount} {currency}");
Console.WriteLine($"pattern matched:         {Describe(a)}");
Console.WriteLine($"pattern matched:         {Describe(new Money(0m, "EUR"))}");

// 'with' produces a new instance with the named members replaced. The original
// is untouched, because the properties are init-only.
var raised = a with { Amount = 20m };
Console.WriteLine($"with produced:           {raised}");
Console.WriteLine($"original unchanged:      {a}");
Console.WriteLine($"with on all-same values is still a new object: {ReferenceEquals(a, a with { })}");
Console.WriteLine($"...but an equal one:     {a == (a with { })}");

static string Describe(Money m) => m switch
{
    (0m, var cur) => $"nothing in {cur}",
    ( < 0m, var cur) => $"a debt in {cur}",
    var (amt, cur) => $"{amt} in {cur}",
};

record Money(decimal Amount, string Currency);
