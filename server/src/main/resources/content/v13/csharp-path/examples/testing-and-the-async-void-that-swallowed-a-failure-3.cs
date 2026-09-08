// The async void test, and where its failure goes. C# 14, .NET 10.

// A runner whose test type is Action cannot await anything. An async lambda
// assigned to an Action compiles as an async void method: it returns at its
// first suspension and there is no task for the caller to observe.
var ctx = new RecordingContext();
SynchronizationContext.SetSynchronizationContext(ctx);

var runner = new ActionRunner();

runner.Add("a synchronous failure is caught by the runner", () =>
{
    throw new AssertionException("expected 1, got 0");
});

runner.Add("an async void failure is not", async () =>
{
    await Task.Yield();
    throw new AssertionException("expected 1, got 0");
});

runner.Run();

// The async void method told the context it had started an operation, and
// delivered its exception there rather than to the runner.
Console.WriteLine($"operations the context was told about: {ctx.Started}");
ctx.Drain();
Console.WriteLine($"failures delivered to the context:     {ctx.Failures}");
Console.WriteLine($"failures the runner saw:               {runner.Failed}");
Console.WriteLine($"tests the runner called passing:       {runner.Passed}");
Console.WriteLine($"actual failures:                       {runner.Failed + ctx.Failures}");

// Remove the context and there is nowhere left to deliver such a failure: it
// reaches the thread pool instead, which is why an async void failure can end
// a process rather than a test. That case is described rather than run here,
// because running it would terminate this program.
SynchronizationContext.SetSynchronizationContext(null);
Console.WriteLine($"with no context installed, Current is null: {SynchronizationContext.Current is null}");

sealed class ActionRunner
{
    private readonly List<(string Name, Action Body)> _tests = [];

    public int Passed { get; private set; }

    public int Failed { get; private set; }

    public void Add(string name, Action body) => _tests.Add((name, body));

    public void Run()
    {
        foreach (var (name, body) in _tests)
        {
            try
            {
                body();
                Passed++;
                Console.WriteLine($"pass  {name}");
            }
            catch (Exception ex)
            {
                Failed++;
                Console.WriteLine($"FAIL  {name}: {ex.Message}");
            }
        }
    }
}

// A test framework installs a context exactly like this one so that async void
// failures have somewhere to land. This one records them instead of rethrowing.
sealed class RecordingContext : SynchronizationContext
{
    private readonly Queue<(SendOrPostCallback Callback, object? State)> _queue = new();

    public int Started { get; private set; }

    public int Failures { get; private set; }

    public override void OperationStarted() => Started++;

    public override void Post(SendOrPostCallback callback, object? state) => _queue.Enqueue((callback, state));

    public void Drain()
    {
        while (_queue.Count > 0)
        {
            var (callback, state) = _queue.Dequeue();
            try
            {
                callback(state);
            }
            catch (Exception)
            {
                Failures++;
            }
        }
    }
}

sealed class AssertionException(string message) : Exception(message);
