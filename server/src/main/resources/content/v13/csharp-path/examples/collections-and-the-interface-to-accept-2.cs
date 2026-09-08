// What a return type promises the caller. C# 14, .NET 10.

var repository = new Repository();

// Returning IEnumerable<T> from an iterator returns a description. Every
// enumeration re-runs the method body, including its cost.
var lazy = repository.LazyNames();
Console.WriteLine($"work done after the call:      {repository.Work}");
Console.WriteLine($"count:                         {lazy.Count()}");
Console.WriteLine($"first:                         {lazy.First()}");
Console.WriteLine($"work done after two uses:      {repository.Work}");

// Returning a materialised list costs one pass, whatever the caller does next.
repository.Reset();
var eager = repository.Names();
Console.WriteLine($"work done after the call:      {repository.Work}");
Console.WriteLine($"count:                         {eager.Count}");
Console.WriteLine($"first:                         {eager[0]}");
Console.WriteLine($"work done after two uses:      {repository.Work}");

// IReadOnlyList<T> is a view, not a guarantee. Handing back the stored list
// under a read-only interface stops honest callers, not determined ones.
var view = repository.Names();
Console.WriteLine($"the view has no Add:           {view is IReadOnlyList<string>}");
if (view is List<string> writable)
{
    writable.Add("smuggled");
}

Console.WriteLine($"after casting back and adding:  {view.Count}");
Console.WriteLine($"the repository saw it:          {repository.StoredCount}");

// A defensive copy costs an allocation and keeps the promise. It can still be
// cast and mutated; what it cannot do is reach the repository.
var storedBefore = repository.StoredCount;
var copied = repository.NamesCopy();
Console.WriteLine($"copy is castable to List:       {copied is List<string>}");
if (copied is List<string> alsoWritable)
{
    alsoWritable.Add("smuggled again");
}

Console.WriteLine($"copy grew:                      {copied.Count}");
Console.WriteLine($"repository unchanged:           {repository.StoredCount == storedBefore}");

// ReadOnlyCollection<T> wraps rather than copies: it refuses at run time and
// still reflects later changes to the list underneath.
var wrapped = repository.NamesWrapped();
Console.WriteLine($"wrapper is castable to List:    {wrapped is List<string>}");
Console.WriteLine($"wrapper count before:           {wrapped.Count}");
repository.Add("added later");
Console.WriteLine($"wrapper count after:            {wrapped.Count}");

sealed class Repository
{
    private readonly List<string> _names = ["ada", "grace", "alan"];

    public int Work { get; private set; }

    public int StoredCount => _names.Count;

    public void Reset() => Work = 0;

    public void Add(string name) => _names.Add(name);

    public IEnumerable<string> LazyNames()
    {
        foreach (var name in _names)
        {
            Work++;
            yield return name;
        }
    }

    public IReadOnlyList<string> Names()
    {
        Work += _names.Count;
        return _names;
    }

    public IReadOnlyList<string> NamesCopy() => new List<string>(_names);

    public IReadOnlyList<string> NamesWrapped() => _names.AsReadOnly();
}
