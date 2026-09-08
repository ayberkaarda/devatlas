// Registration order, multiple implementations, and who disposes what.
// C# 14, .NET 10, Microsoft.AspNetCore.App shared framework.

using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

var services = new ServiceCollection();
services.AddSingleton<INotifier, EmailNotifier>();
services.AddSingleton<INotifier, SmsNotifier>();
services.AddSingleton<INotifier, PushNotifier>();

// TryAdd is a no-op when the service type is already registered, which is how
// a library supplies a default a host may override.
services.TryAddSingleton<INotifier, EmailNotifier>();

// An instance the caller constructed. The container did not create it, so the
// container does not dispose it.
var owned = new OwnedResource();
services.AddSingleton(owned);
services.AddSingleton<ContainerResource>();

var provider = services.BuildServiceProvider();

// Resolving the service type alone gives the LAST registration.
var one = provider.GetRequiredService<INotifier>();
Console.WriteLine($"single resolve returns:        {one.Name}");

// Resolving IEnumerable<T> gives every registration, in registration order.
var all = provider.GetRequiredService<IEnumerable<INotifier>>().ToList();
Console.WriteLine($"registrations resolved:        {all.Count}");
Console.WriteLine($"in registration order:         {string.Join(",", all.Select(n => n.Name))}");
Console.WriteLine($"TryAdd added nothing:          {all.Count(n => n.Name == "email") == 1}");

// GetService returns null for something unregistered; GetRequiredService
// throws. The difference is the whole point of having both.
Console.WriteLine($"GetService for an unregistered type:         {provider.GetService<IReport>() is null}");
Console.WriteLine($"GetRequiredService for an unregistered type: {Catch(() => provider.GetRequiredService<IReport>())}");

// Constructor injection: the container picks the constructor it can satisfy
// and passes the dependencies in.
var digest = provider.GetRequiredService<ContainerResource>();
Console.WriteLine($"injected notifier count:       {digest.NotifierCount}");

Console.WriteLine($"before disposing the provider: container resource disposed: {ContainerResource.Disposals}");
Console.WriteLine($"before disposing the provider: owned resource disposed:     {owned.Disposals}");
provider.Dispose();
Console.WriteLine($"after disposing the provider:  container resource disposed: {ContainerResource.Disposals}");
Console.WriteLine($"after disposing the provider:  owned resource disposed:     {owned.Disposals}");

static string Catch(Action action)
{
    try
    {
        action();
        return "no exception";
    }
    catch (Exception ex)
    {
        return ex.GetType().Name;
    }
}

interface INotifier
{
    string Name { get; }
}

interface IReport;

sealed class EmailNotifier : INotifier
{
    public string Name => "email";
}

sealed class SmsNotifier : INotifier
{
    public string Name => "sms";
}

sealed class PushNotifier : INotifier
{
    public string Name => "push";
}

sealed class OwnedResource : IDisposable
{
    public int Disposals { get; private set; }

    public void Dispose() => Disposals++;
}

sealed class ContainerResource(IEnumerable<INotifier> notifiers) : IDisposable
{
    public static int Disposals { get; private set; }

    public int NotifierCount { get; } = notifiers.Count();

    public void Dispose() => Disposals++;
}
