// The captured dependency: a singleton that holds a scoped service.
// C# 14, .NET 10, Microsoft.AspNetCore.App shared framework.

using Microsoft.Extensions.DependencyInjection;

var services = new ServiceCollection();
services.AddScoped<RequestContext>();
services.AddSingleton<Cache>();

// Without validation the container builds it and nothing complains.
using (var quiet = services.BuildServiceProvider())
{
    var cache = quiet.GetRequiredService<Cache>();

    string firstSeen, secondSeen, firstInScope, secondInScope;
    using (var scope = quiet.CreateScope())
    {
        var context = scope.ServiceProvider.GetRequiredService<RequestContext>();
        context.User = "ada";
        firstInScope = context.User;
        firstSeen = cache.CurrentUser;
    }

    using (var scope = quiet.CreateScope())
    {
        var context = scope.ServiceProvider.GetRequiredService<RequestContext>();
        context.User = "grace";
        secondInScope = context.User;
        secondSeen = cache.CurrentUser;
    }

    Console.WriteLine($"scope's own context, first:    '{firstInScope}'");
    Console.WriteLine($"scope's own context, second:   '{secondInScope}'");
    Console.WriteLine($"cache saw in the first scope:  '{firstSeen}'");
    Console.WriteLine($"cache saw in the second scope: '{secondSeen}'");
    Console.WriteLine($"cache reported the same value both times: {firstSeen == secondSeen}");
    Console.WriteLine($"RequestContext instances created: {RequestContext.Constructions}");
}

// ValidateScopes turns the same registration into a failure at resolve time.
var validating = services.BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true });
Console.WriteLine($"resolving the singleton with ValidateScopes: {Catch(() => validating.GetRequiredService<Cache>())}");
Console.WriteLine($"resolving a scoped service from the root:    {Catch(() => validating.GetRequiredService<RequestContext>())}");
Console.WriteLine($"resolving it inside a scope:                 {Catch(() => validating.CreateScope().ServiceProvider.GetRequiredService<RequestContext>())}");
validating.Dispose();

// ValidateOnBuild moves the same failure to build time, before any request.
Console.WriteLine($"building with ValidateOnBuild:               {Catch(() => services.BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true, ValidateOnBuild = true }))}");

// The fix is to inject the factory, not the instance, and open a scope per
// unit of work.
var fixedUp = new ServiceCollection();
fixedUp.AddScoped<RequestContext>();
fixedUp.AddSingleton<ScopeAwareCache>();
using var provider = fixedUp.BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true });
var aware = provider.GetRequiredService<ScopeAwareCache>();
Console.WriteLine($"factory-based cache, first scope:  '{aware.ReadUser("ada")}'");
Console.WriteLine($"factory-based cache, second scope: '{aware.ReadUser("grace")}'");

// Only the exception's type is printed; the message belongs to the library.
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

sealed class RequestContext
{
    public static int Constructions { get; private set; }

    public RequestContext() => Constructions++;

    public string User { get; set; } = "(unset)";
}

sealed class Cache(RequestContext context)
{
    public string CurrentUser => context.User;
}

sealed class ScopeAwareCache(IServiceScopeFactory scopeFactory)
{
    public string ReadUser(string user)
    {
        using var scope = scopeFactory.CreateScope();
        var context = scope.ServiceProvider.GetRequiredService<RequestContext>();
        context.User = user;
        return context.User;
    }
}
