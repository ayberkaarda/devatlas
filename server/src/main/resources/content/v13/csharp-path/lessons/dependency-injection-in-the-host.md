## Why this exists

A class that constructs its own collaborators decides three things at once: what it needs, which
implementation it gets, and how long that implementation lives. The first is its business; the
other two belong to whoever assembles the application. `Microsoft.Extensions.DependencyInjection`
— the container that ships with the .NET 10 shared framework and backs every generic host —
splits them apart. A class declares its dependencies as constructor parameters and nothing else;
a registration in one place says which implementation satisfies each, and a **lifetime** says how
often a new one is made. Getting the lifetime wrong is the interesting failure, because it
compiles, starts, and produces wrong answers under load.

## The idea

The container is a hire shop with three counters. At the singleton counter there is one item and
everybody who asks gets that one. At the scoped counter there is one item per visit, shared by
everything you do during that visit. At the transient counter you get a fresh item every time you
ask, even twice in the same minute.

### Where the analogy breaks

You cannot walk out of a hire shop with something from a counter that closes before you do — and
in a container you can. A singleton whose constructor takes a scoped service keeps that instance
for the life of the process. The second listing builds exactly that and shows the singleton
reporting `(unset)` in both scopes while each scope's own instance holds `ada` and `grace`.
Nothing throws. That is the captured dependency, and it is a data-corruption bug in a web
application, where "visit" means "request".

The shop also does not tidy up after you. The container disposes what **it** created — scoped
`IDisposable` instances when the scope ends, singletons when the provider is disposed — and it
does not dispose an instance you constructed and registered yourself. The third listing shows the
container-created resource disposed and the caller-supplied one not.

And there is not one item per counter, but one per *service type*. Registering three
implementations of one interface leaves all three in the collection: resolving the interface
returns the last one registered, and resolving `IEnumerable<T>` returns every one, in registration
order. That is documented behaviour, not a quirk to guess at.

## How it works

Registration is a lifetime plus a mapping. Resolution walks the constructor parameters and does
the same for each of them, recursively:

```csharp
services.AddSingleton<Cache>();
services.AddScoped<RequestContext>();
services.AddTransient<IClock, SystemClock>();

using var provider = services.BuildServiceProvider();
using var scope = provider.CreateScope();
var cache = scope.ServiceProvider.GetRequiredService<Cache>();
```

`GetService<T>` returns `null` when nothing is registered; `GetRequiredService<T>` throws. Both
exist because "optional dependency" and "misconfigured application" deserve different code.

The container can be told to check the rule the analogy breaks on:

```csharp
services.BuildServiceProvider(new ServiceProviderOptions
{
    ValidateScopes = true,   // resolving a scoped service from the root now throws
    ValidateOnBuild = true,  // and the whole graph is checked at build time
});
```

The generic host turns both on in the Development environment, which is why the bug reaches
production from a machine where the developer ran it in Release.

A singleton that genuinely needs per-operation state injects `IServiceScopeFactory` and opens a
scope itself, which is the fix at the end of the second listing. `TryAddSingleton` registers only
if the service type is absent, which is how a library supplies a default the host can override.

## Common mistakes

**Registering an implementation that does not implement the service.** The compiler catches this
one, and names both types:

```text
Program.cs(4,10): error CS0311: The type 'Unrelated' cannot be used as type parameter 'TImplementation' in the generic type or method 'ServiceCollectionServiceExtensions.AddSingleton<TService, TImplementation>(IServiceCollection)'. There is no implicit reference conversion from 'Unrelated' to 'INotifier'.
```

**Registering an interface with no implementation.** `services.AddSingleton<INotifier>();`
compiles cleanly and fails only when something resolves it.

**Injecting a scoped service into a singleton.** No diagnostic at all unless `ValidateScopes` is
on, at which point resolving throws an `InvalidOperationException`; with `ValidateOnBuild` the
same failure arrives as an `AggregateException` from `BuildServiceProvider`.

**Resolving a scoped service from the root provider.** Same rule, same silence: without
validation you get an instance that lives forever.

**Expecting the container to dispose an instance you registered.** No diagnostic. The container
disposes only what it constructed.

## Check yourself

<details><summary>Three implementations of one interface are registered. What does <code>GetRequiredService&lt;T&gt;</code> return, and what does <code>GetRequiredService&lt;IEnumerable&lt;T&gt;&gt;</code> return?</summary>

The single resolve returns the last registration; the enumerable returns all three, in the order
they were registered. That is what makes a later `AddSingleton` an override for one caller and an
addition for the other.

</details>

<details><summary>Why does the captured-dependency bug survive testing so often?</summary>

Because a single-request test never opens a second scope, so the singleton's captured instance is
the right one. It needs two scopes to show up — and in production the second scope is the second
request.

</details>

<details><summary>A singleton needs per-request data. What does it inject?</summary>

`IServiceScopeFactory`. It opens a scope for each unit of work, resolves the scoped service inside
that scope, and disposes the scope when done — rather than holding one instance for the life of
the process.

</details>

## Listings

1. `dependency-injection-in-the-host-1.cs` — the three lifetimes, counted.
2. `dependency-injection-in-the-host-2.cs` — the captured dependency, the validation, and the fix.
3. `dependency-injection-in-the-host-3.cs` — multiple registrations, `TryAdd`, and who disposes what.
