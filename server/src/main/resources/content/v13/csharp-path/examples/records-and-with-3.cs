// record struct, and the equality contract that inheritance adds.
// C# 14, .NET 10.

// A record struct is a value type: the record syntax changes what is generated,
// not where the data lives.
var p1 = new Pixel(1, 2);
var p2 = p1;                 // a copy, as with any struct
Console.WriteLine($"copies are Equal:                   {p1 == p2}");

// A non-readonly record struct has settable properties, which makes the copy
// visible: assigning to one does not touch the other.
var m1 = new MutablePixel(1, 2);
var m2 = m1;
m2.X = 9;
Console.WriteLine($"assignment copied the fields:       m1={m1} m2={m2}");

var moved = p1 with { X = 9 };
Console.WriteLine($"with on a record struct:            {moved}");
Console.WriteLine($"original untouched:                 {p1}");

// Inheritance: the synthesised Equals compares a hidden EqualityContract
// property first, so a base and a derived instance are never equal even when
// every shared member matches.
Shape baseShape = new Shape("red");
Shape derivedShape = new Circle("red", 3);
Console.WriteLine($"base vs derived, same colour:       {baseShape == derivedShape}");
Console.WriteLine($"and the comparison is symmetric:    {derivedShape.Equals(baseShape)}");

Shape otherCircle = new Circle("red", 3);
Console.WriteLine($"two circles with equal members:     {derivedShape == otherCircle}");
Console.WriteLine($"ToString names the runtime type:    {derivedShape}");

// 'with' on a base-typed variable produces the runtime type, because the
// synthesised copy constructor is virtual.
var recoloured = derivedShape with { Colour = "blue" };
Console.WriteLine($"with through a base reference:      {recoloured}");
Console.WriteLine($"still a Circle:                     {recoloured is Circle}");

readonly record struct Pixel(int X, int Y);

record struct MutablePixel(int X, int Y);

record Shape(string Colour);

record Circle(string Colour, int Radius) : Shape(Colour);
