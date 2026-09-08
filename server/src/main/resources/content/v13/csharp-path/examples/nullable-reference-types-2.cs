// Flow analysis, and the operators that go with it. C# 14, .NET 10.

string? maybe = Read(hasValue: false);

// A test narrows the variable for the rest of the branch. After 'is not null'
// the compiler treats 'maybe' as a plain string and the dereference is allowed.
Console.WriteLine(maybe is not null
    ? $"narrowed to non-null: {maybe.Length}"
    : "narrowed to null: nothing to read");

string? present = Read(hasValue: true);
if (present is not null)
{
    Console.WriteLine($"narrowed to non-null: {present.Length}");
}

// ?. short-circuits the whole chain, not just the one link.
Order? noOrder = null;
Console.WriteLine($"chain on null is null:      {noOrder?.Customer.Name is null}");

var order = new Order { Customer = new Customer { Name = "Ada" } };
Console.WriteLine($"chain on a real order:      {order?.Customer.Name}");

// ?. on a member of value type produces a nullable value type.
Console.WriteLine($"length through ?. is int?:  {noOrder?.Customer.Name.Length is null}");

// ?? evaluates its right operand only when the left is null. The counter makes
// that visible without depending on anything the runtime is free to change.
var calls = 0;
string Fallback() { calls++; return "(default)"; }

var fromNull = ((string?)null) ?? Fallback();
Console.WriteLine($"?? on null used the fallback: {fromNull}, calls={calls}");

var fromValue = "given" ?? Fallback();
Console.WriteLine($"?? on a value skipped it:     {fromValue}, calls={calls}");

// ??= assigns only when the target is null.
string? slot = null;
slot ??= "first";
slot ??= "second";
Console.WriteLine($"??= assigned once:            {slot}");

// Nullable value types lift arithmetic: any null operand makes the result null.
int? a = 4;
int? b = null;
Console.WriteLine($"4 + null is null:             {(a + b) is null}");
Console.WriteLine($"and HasValue reports it:      {(a + b).HasValue}");
Console.WriteLine($"GetValueOrDefault supplies 0: {(a + b).GetValueOrDefault()}");

static string? Read(bool hasValue) => hasValue ? "hello" : null;

class Order
{
    public required Customer Customer { get; init; }
}

class Customer
{
    public required string Name { get; init; }
}
