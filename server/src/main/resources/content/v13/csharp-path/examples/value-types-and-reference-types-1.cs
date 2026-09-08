// Where the copy happens: assignment and argument passing.
// C# 14, .NET 10. Project defaults: ImplicitUsings enable, Nullable enable.

var a = new PointStruct { X = 1, Y = 1 };
var b = a;                 // copies the two fields
b.X = 99;
Console.WriteLine($"struct assignment: a.X={a.X} b.X={b.X}");

var c = new PointClass { X = 1, Y = 1 };
var d = c;                 // copies the reference, not the object
d.X = 99;
Console.WriteLine($"class assignment:  c.X={c.X} d.X={d.X}");
Console.WriteLine($"class assignment aliases the same object: {ReferenceEquals(c, d)}");

// Arguments follow the same rule: the parameter is a new variable
// initialised by copying whatever the argument held.
var s = new PointStruct { X = 1, Y = 1 };
ShiftStruct(s);
Console.WriteLine($"struct by value:   s.X={s.X}");

ShiftStructByRef(ref s);
Console.WriteLine($"struct by ref:     s.X={s.X}");

var o = new PointClass { X = 1, Y = 1 };
ShiftClass(o);
Console.WriteLine($"class by value:    o.X={o.X}");

ReplaceClass(o);
Console.WriteLine($"class reassigned inside callee: o.X={o.X}");

// Default values differ in kind, not only in value.
PointStruct defaultStruct = default;
PointClass? defaultClass = default;
Console.WriteLine($"default struct is an object with zeroed fields: X={defaultStruct.X}");
Console.WriteLine($"default class reference is null: {defaultClass is null}");

static void ShiftStruct(PointStruct p) => p.X += 10;
static void ShiftStructByRef(ref PointStruct p) => p.X += 10;
static void ShiftClass(PointClass p) => p.X += 10;
static void ReplaceClass(PointClass p) => p = new PointClass { X = -1, Y = -1 };

struct PointStruct
{
    public int X;
    public int Y;
}

class PointClass
{
    public int X;
    public int Y;
}
