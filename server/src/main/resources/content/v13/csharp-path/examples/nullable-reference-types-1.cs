// Nullable reference types are a compile-time analysis. This listing shows what
// survives to run time and what does not. C# 14, .NET 10, <Nullable>enable</Nullable>.

// typeof answers the question directly: a nullable *value* type is a different
// type, a nullable *reference* type is the same type with an annotation.
Console.WriteLine($"int? is a distinct type:    {typeof(int?) != typeof(int)}");
Console.WriteLine($"int? unwraps to:            {Nullable.GetUnderlyingType(typeof(int?))}");
// typeof(string?) does not even compile (CS8639), so reflection is the way to
// ask: both properties below report the same runtime type.
var required = typeof(Profile).GetProperty(nameof(Profile.Handle))!.PropertyType;
var optional = typeof(Profile).GetProperty(nameof(Profile.Nickname))!.PropertyType;
Console.WriteLine($"string and string? report:  {required} and {optional}");
Console.WriteLine($"same runtime type:          {required == optional}");

// The annotation is erased, so nothing at run time stops a null arriving in a
// parameter the signature calls non-nullable. The '!' below is the author
// telling the compiler to stop warning; it generates no check.
string smuggled = null!;
try
{
    Console.WriteLine(Shout(smuggled));
}
catch (Exception ex)
{
    Console.WriteLine($"non-nullable parameter, null argument: {ex.GetType().Name}");
}

// A guard that does exist at run time.
try
{
    ShoutChecked(null!);
}
catch (ArgumentNullException ex)
{
    Console.WriteLine($"guarded:                              {ex.GetType().Name}");
    Console.WriteLine($"and it names the parameter:            {ex.ParamName}");
}

// Declaring the parameter nullable moves the decision into the method, and the
// compiler now insists the method handles it.
Console.WriteLine($"nullable parameter, null argument:     {ShoutMaybe(null)}");
Console.WriteLine($"nullable parameter, real argument:     {ShoutMaybe("hello")}");

static string Shout(string text) => text.ToUpperInvariant();

static string ShoutChecked(string text)
{
    ArgumentNullException.ThrowIfNull(text);
    return text.ToUpperInvariant();
}

static string ShoutMaybe(string? text) => text?.ToUpperInvariant() ?? "(nothing to say)";

class Profile
{
    public string Handle { get; init; } = "";
    public string? Nickname { get; init; }
}
