// Lifetimes, counted rather than described. C# 14, .NET 10.
// Built against the Microsoft.AspNetCore.App shared framework, which carries
// Microsoft.Extensions.DependencyInjection; no external package is needed.

using Microsoft.Extensions.DependencyInjection;

var services = new ServiceCollection();
services.AddSingleton<SingletonService>();
services.AddScoped<ScopedService>();
services.AddTransient<TransientService>();

using var provider = services.BuildServiceProvider();

// Resolving twice from the same scope.
using (var scope = provider.CreateScope())
{
    var p = scope.ServiceProvider;
    Console.WriteLine($"singleton, same scope, same instance: {ReferenceEquals(p.GetRequiredService<SingletonService>(), p.GetRequiredService<SingletonService>())}");
    Console.WriteLine($"scoped, same scope, same instance:    {ReferenceEquals(p.GetRequiredService<ScopedService>(), p.GetRequiredService<ScopedService>())}");
    Console.WriteLine($"transient, same scope, same instance: {ReferenceEquals(p.GetRequiredService<TransientService>(), p.GetRequiredService<TransientService>())}");
}

// Resolving once from each of two scopes.
SingletonService s1, s2;
ScopedService c1, c2;
using (var scope = provider.CreateScope())
{
    s1 = scope.ServiceProvider.GetRequiredService<SingletonService>();
    c1 = scope.ServiceProvider.GetRequiredService<ScopedService>();
}

using (var scope = provider.CreateScope())
{
    s2 = scope.ServiceProvider.GetRequiredService<SingletonService>();
    c2 = scope.ServiceProvider.GetRequiredService<ScopedService>();
}

Console.WriteLine($"singleton across scopes, same instance: {ReferenceEquals(s1, s2)}");
Console.WriteLine($"scoped across scopes, same instance:    {ReferenceEquals(c1, c2)}");

// Construction counts say the same thing without relying on identity.
Console.WriteLine($"SingletonService constructed: {SingletonService.Constructions}");
Console.WriteLine($"ScopedService constructed:    {ScopedService.Constructions}");
Console.WriteLine($"TransientService constructed: {TransientService.Constructions}");

// The container disposes what it created: scoped instances when the scope
// ends, singletons when the provider is disposed.
Console.WriteLine($"scoped instances disposed:    {ScopedService.Disposals}");
Console.WriteLine($"singleton disposed so far:    {SingletonService.Disposals}");

sealed class SingletonService : IDisposable
{
    public static int Constructions { get; private set; }

    public static int Disposals { get; private set; }

    public SingletonService() => Constructions++;

    public void Dispose() => Disposals++;
}

sealed class ScopedService : IDisposable
{
    public static int Constructions { get; private set; }

    public static int Disposals { get; private set; }

    public ScopedService() => Constructions++;

    public void Dispose() => Disposals++;
}

sealed class TransientService
{
    public static int Constructions { get; private set; }

    public TransientService() => Constructions++;
}
