// An async test is a Func<Task> the runner awaits. C# 14, .NET 10.

var runner = new AsyncRunner();

runner.Add("an async success is reported as a pass", async () =>
{
    var store = new Store();
    await store.SaveAsync("book");
    Assert.Equal(1, await store.CountAsync());
});

runner.Add("an async failure is reported because the runner awaits", async () =>
{
    var store = new Store();
    Assert.Equal(1, await store.CountAsync());
});

runner.Add("asserting on an async throw needs the await inside the assertion", async () =>
{
    var store = new Store();
    await Assert.ThrowsAsync<InvalidOperationException>(() => store.RemoveAsync("book"));
});

// The same assertion written without awaiting the operation. The task faults
// after the helper's try block has already finished, so the helper concludes
// that nothing was thrown and reports a failure whose message is misleading.
runner.Add("the same assertion with the await missing", () =>
{
    var store = new Store();
    Assert.ThrowsWithoutAwaiting<InvalidOperationException>(() => store.RemoveAsync("book"));
    return Task.CompletedTask;
});

// A test body that starts an operation and does not await it. The task faults
// after the body has already returned, so the runner has nothing to catch.
runner.Add("a test that forgets to await reports a pass", () =>
{
    var store = new Store();
    _ = store.RemoveAsync("book");
    return Task.CompletedTask;
});

await runner.RunAsync();

sealed class AsyncRunner
{
    private readonly List<(string Name, Func<Task> Body)> _tests = [];

    public void Add(string name, Func<Task> body) => _tests.Add((name, body));

    public async Task RunAsync()
    {
        var failed = 0;
        foreach (var (name, body) in _tests)
        {
            try
            {
                await body();
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

    public static async Task ThrowsAsync<TException>(Func<Task> action)
        where TException : Exception
    {
        try
        {
            await action();
        }
        catch (TException)
        {
            return;
        }

        throw new AssertionException($"expected {typeof(TException).Name}, nothing was thrown");
    }

    // Deliberately wrong: the returned task is dropped, so its fault is never
    // seen and the assertion reports whatever the synchronous part did.
    public static void ThrowsWithoutAwaiting<TException>(Func<Task> action)
        where TException : Exception
    {
        try
        {
            _ = action();
        }
        catch (TException)
        {
            return;
        }

        throw new AssertionException($"expected {typeof(TException).Name}, nothing was thrown");
    }
}

sealed class AssertionException(string message) : Exception(message);

sealed class Store
{
    private readonly List<string> _items = [];

    public async Task SaveAsync(string item)
    {
        await Task.Yield();
        _items.Add(item);
    }

    public async Task<int> CountAsync()
    {
        await Task.Yield();
        return _items.Count;
    }

    public async Task RemoveAsync(string item)
    {
        await Task.Yield();
        if (!_items.Remove(item))
        {
            throw new InvalidOperationException($"'{item}' is not in the store");
        }
    }
}
