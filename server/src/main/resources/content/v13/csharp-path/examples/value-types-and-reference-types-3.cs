// Boxing, and the equality each kind gets for free. C# 14, .NET 10.

var value = new PointStruct { X = 1, Y = 2 };

// Assigning a struct to 'object' allocates a box and copies the fields in.
object boxed = value;
value.X = 99;
Console.WriteLine($"original after mutation: {value.X}");
Console.WriteLine($"box still holds the old copy: {((PointStruct)boxed).X}");

// Unboxing copies out again, so mutating the unboxed variable is invisible
// to the box.
var unboxed = (PointStruct)boxed;
unboxed.X = -1;
Console.WriteLine($"box unaffected by the unboxed copy: {((PointStruct)boxed).X}");

// Equality by default. A struct inherits memberwise Equals from ValueType;
// a class inherits reference equality from object.
var s1 = new PointStruct { X = 1, Y = 2 };
var s2 = new PointStruct { X = 1, Y = 2 };
Console.WriteLine($"two structs with equal fields are Equal: {s1.Equals(s2)}");

var c1 = new PointClass { X = 1, Y = 2 };
var c2 = new PointClass { X = 1, Y = 2 };
Console.WriteLine($"two classes with equal fields are Equal: {c1.Equals(c2)}");
Console.WriteLine($"the same class reference is Equal to itself: {c1.Equals(c1)}");

// Implementing IEquatable<T> gives the struct a comparison that does not box.
var t1 = new TypedPoint(1, 2);
var t2 = new TypedPoint(1, 2);
Console.WriteLine($"IEquatable<T> comparison: {t1.Equals(t2)}");

// A struct can implement an interface, but reaching it through the interface
// stores the struct in a box, and the box is what gets mutated.
IShiftable asInterface = new MutablePoint { X = 0 };
asInterface.Shift();
Console.WriteLine($"through the interface, the box moved: {((MutablePoint)asInterface).X}");

var direct = new MutablePoint { X = 0 };
var boxedCopy = (IShiftable)direct;
boxedCopy.Shift();
Console.WriteLine($"but the local it was copied from did not: {direct.X}");

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

readonly struct TypedPoint(int x, int y) : IEquatable<TypedPoint>
{
    public int X { get; } = x;
    public int Y { get; } = y;

    public bool Equals(TypedPoint other) => X == other.X && Y == other.Y;

    public override bool Equals(object? obj) => obj is TypedPoint p && Equals(p);

    public override int GetHashCode() => HashCode.Combine(X, Y);
}

interface IShiftable
{
    void Shift();
}

struct MutablePoint : IShiftable
{
    public int X;

    public void Shift() => X += 1;
}
