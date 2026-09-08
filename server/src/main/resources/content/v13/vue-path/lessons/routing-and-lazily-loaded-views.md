## Why this exists

An application with more than one screen has to answer two questions. Which
component belongs to this URL, and when is that component's code loaded? The
first is routing. The second is the one that decides whether the first screen
appears in a moment or after every screen in the product has been downloaded.

Vue 3.5's routing guide points at Vue Router for anything real — "for most SPAs,
it's recommended to use the officially-supported Vue Router library" — while
noting that if you "only need very simple routing", it can be done "with Dynamic
Components" and "a routes object mapping paths to components". Both approaches
share a shape, and the shape is what this lesson is about: a route is a record
in a table, and its component may be a value or a promise for one.

## The idea

A route table is a library catalogue. The URL is a call number; matching turns
it into a shelf mark. Popular books are on the open shelves; the rest live in
off-site storage and are fetched when a reader asks. Nobody carts the whole
store into the reading room in the morning on the chance that somebody wants it.

### Where the analogy breaks

A catalogue always produces the book eventually. A lazily loaded view has states
a catalogue has no card for: the request is in flight, or it failed and will not
succeed on a retry either. Vue 3.5's async components guide is direct about
this: "Asynchronous operations inevitably involve loading and error states -
`defineAsyncComponent()` supports handling these states via advanced options",
naming `loadingComponent`, `delay`, `errorComponent` and `timeout`. A router
that renders nothing while a chunk is in flight shows a blank screen, and a
blank screen is indistinguishable from a crash.

The analogy leaks a second time in a more interesting place. A catalogue entry
is inert data. A route entry is not: the function stored in it decides *when a
module's top-level code executes*. Whatever your view module does on import — a
registration, a singleton, a side effect — now happens on first navigation
rather than at start-up, and its ordering relative to everything else changes
with it.

## How it works

A route record pairs a path with a component. Making the component a function
that returns a promise is what defers the code.

```js
import { defineAsyncComponent } from 'vue'

const routes = [
  { path: '/', component: HomeView },
  { path: '/books/:id', component: () => import('./views/BookDetailView.vue') },
  {
    path: '/reports',
    component: defineAsyncComponent({
      loader: () => import('./views/ReportsView.vue'),
      loadingComponent: Spinner,
      errorComponent: LoadFailed,
      delay: 200,
    }),
  },
]
```

"ES module dynamic import also returns a Promise, so most of the time we will
use it in combination with `defineAsyncComponent`. Bundlers like Vite and
webpack also support the syntax (and will use it as bundle split points)."

The difference between deferred and not is one arrow:

```js
{ path: '/reports', component: import('./views/ReportsView.vue') }   // loads now
{ path: '/reports', component: () => import('./views/ReportsView.vue') } // on visit
```

The second listing makes the deferral observable: its two views are real ES
modules, each announcing itself when its body evaluates.

```
modules evaluated before any navigation: 0
navigate("/") rendered: <h1>HomeView</h1>
modules evaluated: HomeView
the other view is still unloaded: true
loader calls after visiting each view twice: 2
```

Two visits to each view, two loader calls in total: once resolved, the component
is kept, and no second request is made.

## Common mistakes

**Passing `import(…)` instead of `() => import(…)`.** Without the arrow, the
import starts while the route table is being built, and every view loads at
start-up. Everything still works, so nothing tells you the splitting is gone
except a bundle report.

**Rendering nothing while a chunk is in flight.** The third listing shows a
route with no loading state and no error state: `""` while pending and `""`
after failure — two very different situations that look identical to the person
using it.

**Treating a failed chunk as impossible.** A deploy during a session, a dropped
connection, a proxy: the promise rejects and the view never arrives. Declare an
error component and give it a retry.

**Assuming a route parameter is a number.** Matching produces strings. The first
listing prints it: `params arrive as strings: true`. Comparing `params.id === 42`
is false for `/books/42`.

## Check yourself

<details><summary>What actually splits the bundle?</summary>
The dynamic <code>import()</code> inside a function. The bundler treats it as a
split point; the function is what stops it running before somebody navigates.
</details>

<details><summary>Why does a lazily loaded route need two extra components?</summary>
Because it has two states before it has a view. Without a loading component the
screen is blank while the request is open; without an error component it stays
blank forever if the request fails.
</details>

<details><summary>Navigating away and back re-fetches nothing. Why?</summary>
The resolved component is kept after the first successful load, so the loader
runs once per view rather than once per visit.
</details>

## Full listings

1. A route table and the matcher over it, including the fall-through.
2. Deferred loading, watched: real modules that announce when they evaluate.
3. Pending, failed and retried, with the blank screen you get without them.
