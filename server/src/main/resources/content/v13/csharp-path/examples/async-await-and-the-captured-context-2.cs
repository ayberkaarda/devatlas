// The context an await captures, and what ConfigureAwait(false) declines.
// C# 14, .NET 10.

// A console application has no SynchronizationContext at all, which is why the
// deadlocks this lesson describes are invisible here until one is installed.
Console.WriteLine($"console app starts with no context: {SynchronizationContext.Current is null}");

// This one queues everything posted to it and runs the queue only when Pump is
// called, which is how a single-threaded UI loop behaves.
var ctx = new PumpContext();
SynchronizationContext.SetSynchronizationContext(ctx);

// RunContinuationsAsynchronously stops the completing thread from running the
// continuation inline, so the capture is the only route back.
var gate1 = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var onContext = ResumeOnContext(gate1);
Console.WriteLine($"posted while suspended:            {ctx.Posted}");

gate1.SetResult();
Console.WriteLine($"posted once the task completed:    {ctx.Posted}");
Console.WriteLine($"method finished before pumping:    {onContext.IsCompleted}");

ctx.Pump();
Console.WriteLine($"method finished after pumping:     {onContext.IsCompleted}");
Console.WriteLine($"resumed on the captured context:   {onContext.Result}");

// ConfigureAwait(false) says "do not capture". Nothing is posted, and the
// continuation runs where the runtime chooses instead.
var posted = ctx.Posted;
var gate2 = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
var anywhere = ResumeAnywhere(gate2);
gate2.SetResult();
var sawContext = anywhere.GetAwaiter().GetResult();
Console.WriteLine($"posts added by ConfigureAwait(false): {ctx.Posted - posted}");
Console.WriteLine($"resumed on the captured context:   {sawContext}");

SynchronizationContext.SetSynchronizationContext(null);
Console.WriteLine($"context removed:                   {SynchronizationContext.Current is null}");

static async Task<bool> ResumeOnContext(TaskCompletionSource gate)
{
    await gate.Task;
    return SynchronizationContext.Current is PumpContext;
}

static async Task<bool> ResumeAnywhere(TaskCompletionSource gate)
{
    await gate.Task.ConfigureAwait(false);
    return SynchronizationContext.Current is PumpContext;
}

sealed class PumpContext : SynchronizationContext
{
    private readonly Queue<(SendOrPostCallback Callback, object? State)> _queue = new();

    public int Posted { get; private set; }

    public override void Post(SendOrPostCallback callback, object? state)
    {
        Posted++;
        _queue.Enqueue((callback, state));
    }

    public void Pump()
    {
        while (_queue.Count > 0)
        {
            var (callback, state) = _queue.Dequeue();
            SetSynchronizationContext(this);
            callback(state);
        }
    }
}
