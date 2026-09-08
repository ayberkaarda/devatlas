// What await guarantees, and what it deliberately does not.
// C# 14, .NET 10.

var log = new List<string>();

// An async method runs synchronously until its first await of something that
// has not finished. The call itself is not "starting a thread".
log.Add("before the call");
var gate = new TaskCompletionSource();
var pending = Work(gate, log);
log.Add("after the call, before the gate opens");
Console.WriteLine($"order so far: {string.Join(" | ", log)}");
Console.WriteLine($"task finished already: {pending.IsCompleted}");

gate.SetResult();
await pending;
log.Add("after the await");
Console.WriteLine($"final order:  {string.Join(" | ", log)}");

// Awaiting a task that is already complete yields its value without
// suspending. That is a language rule, not an optimisation you may not rely on.
var ready = Task.FromResult(41);
Console.WriteLine($"completed before await: {ready.IsCompleted}");
Console.WriteLine($"await yields the value: {await ready + 1}");

// Task.WhenAll returns results in ARGUMENT order. Which task finished first is
// not part of the contract and is not printed here.
var a = Delayed("a", 3);
var b = Delayed("b", 1);
var c = Delayed("c", 2);
var all = await Task.WhenAll(a, b, c);
Console.WriteLine($"WhenAll results in argument order: {string.Join(",", all)}");

// WhenAny returns one of the tasks. Which one is a race, so the only thing
// worth asserting is that it is one of them.
var first = await Task.WhenAny(Delayed("x", 1), Delayed("y", 1));
Console.WriteLine($"WhenAny returned one of the tasks: {(await first) is "x" or "y"}");

// A task is a handle to a result, so it can be stored, passed and awaited more
// than once. Awaiting a completed task again just re-reads the result.
var shared = Delayed("shared", 1);
Console.WriteLine($"first await:  {await shared}");
Console.WriteLine($"second await: {await shared}");

static async Task Work(TaskCompletionSource gate, List<string> log)
{
    log.Add("method body started, synchronously");
    await gate.Task;
    log.Add("method body resumed");
}

static async Task<string> Delayed(string name, int steps)
{
    for (var i = 0; i < steps; i++)
    {
        await Task.Yield();
    }

    return name;
}
