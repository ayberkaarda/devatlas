// Where an exception from an async method goes, and how the shape of the wait
// changes what you catch. C# 14, .NET 10.

// The exception does not escape the call. It is stored in the returned task.
// Fail never awaits anything incomplete, so it runs to its throw synchronously
// and the task it returns is already faulted when the call returns.
var failing = Fail("boom");
Console.WriteLine($"call returned without throwing: {failing is Task}");
Console.WriteLine($"task is faulted:                {failing.IsFaulted}");
Console.WriteLine($"stored exception type:          {failing.Exception!.InnerException!.GetType().Name}");

// await rethrows the original exception.
try
{
    await failing;
}
catch (InvalidOperationException ex)
{
    Console.WriteLine($"await threw:                    {ex.GetType().Name} '{ex.Message}'");
}

// Blocking wraps it. Wait() and .Result throw AggregateException; the awaiter
// used by await does not.
try
{
    Fail("blocked").Wait();
}
catch (Exception ex)
{
    Console.WriteLine($"Wait() threw:                   {ex.GetType().Name}");
    Console.WriteLine($"with inner:                     {((AggregateException)ex).InnerException!.GetType().Name}");
}

try
{
    Fail("unwrapped").GetAwaiter().GetResult();
}
catch (Exception ex)
{
    Console.WriteLine($"GetAwaiter().GetResult() threw: {ex.GetType().Name}");
}

// WhenAll collects every failure but await rethrows only one of them, so the
// count has to come from the task's own Exception.
var whenAll = Task.WhenAll(Fail("first"), Fail("second"), Succeed());
try
{
    await whenAll;
}
catch (InvalidOperationException)
{
    var messages = whenAll.Exception!.InnerExceptions.Select(e => e.Message).Order().ToList();
    Console.WriteLine($"await rethrew one exception, task recorded: {whenAll.Exception.InnerExceptions.Count}");
    Console.WriteLine($"all messages (sorted):          {string.Join(",", messages)}");
}

// Cancellation is not a fault: the task ends canceled and await throws an
// OperationCanceledException.
using var cts = new CancellationTokenSource();
cts.Cancel();
var cancelled = Task.FromCanceled(cts.Token);
Console.WriteLine($"cancelled task IsCanceled:      {cancelled.IsCanceled}");
Console.WriteLine($"cancelled task IsFaulted:       {cancelled.IsFaulted}");
try
{
    await cancelled;
}
catch (OperationCanceledException ex)
{
    Console.WriteLine($"await on a cancelled task threw an OperationCanceledException: {ex is OperationCanceledException}");
}

static async Task Fail(string message)
{
    await Task.CompletedTask;
    throw new InvalidOperationException(message);
}

static async Task Succeed() => await Task.CompletedTask;
