// 'with' copies the members, and a member that is a reference is copied as a
// reference. C# 14, .NET 10.

var original = new Basket("ada", new List<string> { "book" });
var copy = original with { Owner = "grace" };

// The two records are different objects with different owners...
Console.WriteLine($"owners differ:            {original.Owner} / {copy.Owner}");

// ...and one list, because copying a List<string> member copies the reference.
Console.WriteLine($"same list instance:       {ReferenceEquals(original.Items, copy.Items)}");
copy.Items.Add("pen");
Console.WriteLine($"original item count:      {original.Items.Count}");

// Value equality is also memberwise, so a member compared by reference makes
// the whole record compared by reference for that member.
var left = new Basket("ada", new List<string> { "book" });
var right = new Basket("ada", new List<string> { "book" });
Console.WriteLine($"equal contents, distinct lists, records Equal: {left == right}");

// Swapping the member for one with value equality fixes it. A record holding
// only records, strings and numbers compares the way a reader expects.
var l2 = new Shipment("ada", new Address("1 High St", "Leeds"));
var r2 = new Shipment("ada", new Address("1 High St", "Leeds"));
Console.WriteLine($"record member, records Equal:                  {l2 == r2}");

// An explicitly implemented Equals on the member restores value semantics for
// a collection too, but it has to be written; nothing is generated for you.
var l3 = new FrozenBasket("ada", new ItemList(["book"]));
var r3 = new FrozenBasket("ada", new ItemList(["book"]));
Console.WriteLine($"sequence-comparing member, records Equal:      {l3 == r3}");
Console.WriteLine($"and a different sequence is not:               {l3 == new FrozenBasket("ada", new ItemList(["pen"]))}");

record Basket(string Owner, List<string> Items);

record Address(string Line1, string City);

record Shipment(string Owner, Address Destination);

record FrozenBasket(string Owner, ItemList Items);

sealed class ItemList(IEnumerable<string> items) : IEquatable<ItemList>
{
    private readonly string[] _items = items.ToArray();

    public bool Equals(ItemList? other) => other is not null && _items.SequenceEqual(other._items);

    public override bool Equals(object? obj) => Equals(obj as ItemList);

    public override int GetHashCode()
    {
        var hash = new HashCode();
        foreach (var item in _items)
        {
            hash.Add(item);
        }

        return hash.ToHashCode();
    }
}
