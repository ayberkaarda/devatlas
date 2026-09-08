## Why this exists

The first thing a browser has to do with an application is download it. Every
screen you add makes that download larger, including the screens a given reader
will never open — an administration section, a settings page, a mind map. A
route table is the one place in an Angular 22 application that already knows
which code belongs to which screen, so it is the natural place to say "fetch
this part when it is needed". Angular's documentation states the payoff
plainly: those parts "compile into separate JavaScript chunks that the router
requests only when the user visits the corresponding route".

## The idea

A lazy route is a library with closed stacks. The catalogue is on the desk and
lists everything the library holds; the books are in the basement. Asking for
one sends somebody downstairs, and a reader who never asks never causes a trip.
The catalogue is small enough to hand to every visitor, which is exactly what
you want to be true of the code a browser downloads first.

### Where the analogy breaks

The trip to the basement is invisible in a library and is not in a browser. It
is a network request: it takes time, it can fail, and the reader may have
navigated somewhere else before it lands. Route activation is asynchronous, and
a design that assumes the component is simply there is a design that has no
answer for a slow connection.

A catalogue is also inert. A route table is code, and Angular's router "executes
`loadComponent` and `loadChildren` within the injection context of the current
route", so a loader function may call `inject` and decide something before it
resolves.

The third leak is the one that quietly undoes the whole exercise. A library
fetches whole books; a bundler decides the boundaries. Anything the route file
imports at the top of the file is in the initial download regardless of what
the route says — so a guard, a constant or a type helper imported statically to
be used in a lazy route drags its module into the first chunk with it.

## How it works

`loadComponent` takes a function returning a promise of a component;
`loadChildren` takes one returning a promise of routes. The standard dynamic
`import` produces both.

```ts
{
  path: 'tracks',
  loadComponent: () => import('./track-list.page').then((m) => m.TrackListPage),
},
{
  path: 'admin',
  loadChildren: () => import('./admin.routes').then((m) => m.ADMIN_ROUTES),
}
```

The function is not called when the table is built. It is called when the route
is activated, and never before. The third listing shows that shape with no
framework involved: it prints `loader declared, module not requested` before
anything is fetched, and `same function: true` on the second call, because a
module is evaluated once and a revisited route is not a second download.

Matching is first-match-wins — "once Angular matches a URL with a route `path`,
it stops checking any further routes" — so a table is read in order and the
wildcard belongs at the end.

```ts
{ path: '', pathMatch: 'full', redirectTo: 'tracks' },
{ path: '**', loadComponent: () => import('./not-found.page').then((m) => m.NotFoundPage) }
```

Even the not-found screen can be lazy: nothing about it needs to be in the code
every reader downloads.

## Common mistakes

**Returning the module instead of the component.** Omitting the `.then` fails
to compile: `error TS2322: Type '() => Promise<typeof import("./page")>' is not
assignable to type '() => Type<unknown> | Observable<...> | Promise<...>'`. A
file with a default export may be returned directly; anything else must select
its class.

**Putting the wildcard route above the others.** With first-match-wins, `**`
answers for every path below it, and every URL renders the not-found screen.
The table still compiles.

**Importing from the lazy module at the top of the route file.** A guard or a
constant pulled in with a static `import` is in the initial bundle, and so is
everything that module imports. Guards belong in a small file of their own.

**Guarding inside the lazy section only.** A guard on the child routes runs
after the chunk has been downloaded. Placed on the parent route, it refuses
entry before anything is fetched.

## Check yourself

<details><summary>When does the function passed to <code>loadComponent</code> run?</summary>
When the route is activated. Building the route table only stores the function;
nothing is requested until a navigation matches that path.
</details>

<details><summary>Why is a revisited lazy route not a second download?</summary>
Because the module has already been evaluated and the dynamic import resolves
from the module registry. The third listing prints
<code>same function: true</code> to show it.
</details>

<details><summary>Where should a guard live, and why?</summary>
In a small file of its own, imported by the main route table so it can guard the
parent route. Importing it from inside the lazy section either pulls that
section into the initial bundle or lets the chunk download before the refusal.
</details>

## Full listings

1. A route table: redirect, two lazy screens, a lazy section and a lazy wildcard.
2. The lazily loaded components, the child route table and a guard built by a factory.
3. A dynamic import, shown to fetch nothing until it is called and nothing again afterwards.
