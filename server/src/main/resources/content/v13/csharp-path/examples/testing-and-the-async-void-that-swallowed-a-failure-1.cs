// A test is a function that either returns or throws. Everything a framework
// adds is discovery, reporting and isolation. C# 14, .NET 10.
// No test package is used here: the runner is thirty lines, so that what a
// framework does and what it cannot do are both visible.

var runner = new Runner();

runner.Add("a new basket is empty", () =>
{
    var basket = new Basket(new FixedClock(new DateTime(2026, 1, 1)));
    Assert.Equal(0, basket.Count);
});

runner.Add("adding an item raises the count", () =>
{
    var basket = new Basket(new FixedClock(new DateTime(2026, 1, 1)));
    basket.Add("book");
    Assert.Equal(1, basket.Count);
});

runner.Add("the clock is injected, not read from the machine", () =>
{
    var clock = new FixedClock(new DateTime(2026, 1, 1, 9, 30, 0));
    var basket = new Basket(clock);
    basket.Add("book");
    Assert.Equal("2026-01-01T09:30:00", basket.LastChangedAt);
});

runner.Add("a failing test reports rather than crashing the run", () =>
{
    var basket = new Basket(new FixedClock(new DateTime(2026, 1, 1)));
    Assert.Equal(1, basket.Count);
});

runner.Add("removing what is not there throws", () =>
{
    var basket = new Basket(new FixedClock(new DateTime(2026, 1, 1)));
    Assert.Throws<InvalidOperationException>(() => basket.Remove("book"));
});

runner.Run();

sealed class Runner
{
    private readonly List<(string Name, Action Body)> _tests = [];

    public void Add(string name, Action body) => _tests.Add((name, body));

    public void Run()
    {
        var failed = 0;
        foreach (var (name, body) in _tests)
        {
            try
            {
                body();
                Console.WriteLine($"pass  {name}");
            }
            catch (Exception ex)
            {
                failed++;
                Console.WriteLine($"FAIL  {name}: {ex.Message}");
            }
        }

        Console.WriteLine($"{_tests.Count} tests, {failed} failed");
    }
}

static class Assert
{
    public static void Equal<T>(T expected, T actual)
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new AssertionException($"expected '{expected}', got '{actual}'");
        }
    }

    public static void Throws<TException>(Action action)
        where TException : Exception
    {
        try
        {
            action();
        }
        catch (TException)
        {
            return;
        }

        throw new AssertionException($"expected {typeof(TException).Name}, nothing was thrown");
    }
}

sealed class AssertionException(string message) : Exception(message);

interface IClock
{
    DateTime Now { get; }
}

sealed class FixedClock(DateTime now) : IClock
{
    public DateTime Now => now;
}

sealed class Basket(IClock clock)
{
    private readonly List<string> _items = [];

    public int Count => _items.Count;

    public string LastChangedAt { get; private set; } = "never";

    public void Add(string item)
    {
        _items.Add(item);
        // "s" is the round-trip sortable format and is culture-independent, so the
        // assertion does not depend on the machine's regional settings.
        LastChangedAt = clock.Now.ToString("s");
    }

    public void Remove(string item)
    {
        if (!_items.Remove(item))
        {
            throw new InvalidOperationException($"'{item}' is not in the basket");
        }
    }
}
