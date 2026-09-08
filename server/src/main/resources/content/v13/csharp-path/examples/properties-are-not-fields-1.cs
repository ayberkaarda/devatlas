// A property is a pair of methods wearing a field's clothes. C# 14, .NET 10.

using System.Reflection;

var t = typeof(Account);

// The name 'Balance' is a property, and there is no public field by that name.
Console.WriteLine($"Balance is a property:      {t.GetProperty("Balance") is not null}");
Console.WriteLine($"Balance is a public field:  {t.GetField("Balance", BindingFlags.Public | BindingFlags.Instance) is not null}");

// What the property really is: methods.
var balance = t.GetProperty("Balance")!;
Console.WriteLine($"get accessor is a method:   {balance.GetMethod is MethodInfo}");
Console.WriteLine($"set accessor exists:        {balance.SetMethod is not null}");
Console.WriteLine($"Summary has a setter:       {t.GetProperty("Summary")!.SetMethod is not null}");

// Because it is a method, it can count its own calls. Every read runs the
// accessor, including each read inside a loop or an interpolated string.
var account = new Account("ada", 100);
_ = account.Summary;
_ = account.Summary;
var joined = $"{account.Summary}|{account.Summary}";
Console.WriteLine($"reads of Summary so far:    {account.SummaryReads}");
Console.WriteLine($"interpolation read it too:  {joined.Split('|').Length}");

// Because it is a method, it can refuse.
try
{
    account.Balance = -1;
}
catch (ArgumentOutOfRangeException ex)
{
    Console.WriteLine($"setter refused:             {ex.GetType().Name} on {ex.ParamName}");
}

Console.WriteLine($"balance unchanged:          {account.Balance}");

// Because it is a method, an interface can require it. A field cannot appear
// in an interface at all.
INamed named = account;
Console.WriteLine($"reached through interface:  {named.Name}");

// And because it is a method, its implementation can change without changing
// the type's surface. Both of these are 'a property called Balance'.
Console.WriteLine($"stored property:            {account.Balance}");
Console.WriteLine($"computed property:          {account.IsOverdrawn}");

interface INamed
{
    string Name { get; }
}

class Account(string name, int balance) : INamed
{
    private int _balance = balance;

    public string Name { get; } = name;

    public int SummaryReads { get; private set; }

    public int Balance
    {
        get => _balance;
        set
        {
            if (value < 0)
            {
                throw new ArgumentOutOfRangeException(nameof(value), "balance may not be negative");
            }

            _balance = value;
        }
    }

    public bool IsOverdrawn => _balance < 0;

    public string Summary
    {
        get
        {
            SummaryReads++;
            return $"{Name}:{_balance}";
        }
    }
}
