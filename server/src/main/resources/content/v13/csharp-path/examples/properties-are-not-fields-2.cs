// Auto-properties, the C# 14 'field' keyword, init and required.
// C# 14, .NET 10.

using System.Globalization;

// double formats itself with the ambient culture, so pin it rather than record
// whatever this machine happens to be configured for.
CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;

// An auto-property is the same pair of methods with a backing field the
// compiler writes. Adding validation used to mean writing that field yourself;
// in C# 14 the accessor can name it with the contextual keyword 'field'.
var reading = new Reading();
reading.Celsius = -500;
Console.WriteLine($"clamped below absolute zero: {reading.Celsius}");
reading.Celsius = 21.5;
Console.WriteLine($"ordinary value kept:         {reading.Celsius}");

// A get-only accessor over 'field' still allows a private write path.
Console.WriteLine($"writes so far:               {reading.Writes}");

// 'init' allows exactly one assignment, in an object initialiser.
var endpoint = new Endpoint { Host = "example.internal", Port = 8443 };
Console.WriteLine($"init-only property:          {endpoint.Host}:{endpoint.Port}");

// 'required' moves the check to the compiler: the object initialiser must set
// it, and there is no runtime cost and no nullable warning to suppress.
var configured = new Endpoint { Host = "other.internal" };
Console.WriteLine($"required set, port defaulted: {configured.Host}:{configured.Port}");

// A computed property is a method too. It has no storage at all, so it always
// agrees with whatever it derives from.
var basket = new Basket();
Console.WriteLine($"empty basket total:          {basket.Total}");
basket.Add(3);
basket.Add(4);
Console.WriteLine($"after two additions:         {basket.Total}");
Console.WriteLine($"and the count agrees:        {basket.Count}");

class Reading
{
    public double Celsius
    {
        get => field;
        set
        {
            Writes++;
            field = value < -273.15 ? -273.15 : value;
        }
    }

    public int Writes { get; private set; }
}

class Endpoint
{
    public required string Host { get; init; }

    public int Port { get; init; } = 443;
}

class Basket
{
    private readonly List<int> _prices = [];

    public int Count => _prices.Count;

    public int Total => _prices.Sum();

    public void Add(int price) => _prices.Add(price);
}
