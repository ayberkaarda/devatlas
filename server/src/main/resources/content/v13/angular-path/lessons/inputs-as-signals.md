## Why this exists

A component that holds all of its own data is easy and rarely useful. The
interesting ones are told things: a card is told which lesson to show, an editor
is told which post to open, a progress bar is told two numbers. In Angular 22
that channel is `input()`, and what arrives through it is a signal — so the
same rules you learned for state apply to data you did not create. The part
that surprises people is not the reading. It is that the router will hand a
component `undefined` on purpose, and a component that treats absence as a
mistake will get the wrong screen rather than an error.

## The idea

An input is a letterbox in a door. The parent posts through it from outside;
the component reads what came through. Nobody inside the house can post a
letter to themselves through their own letterbox, and that restriction is the
whole reason the flow of data is followable: to find out why a value is wrong,
you look at whoever posted it.

### Where the analogy breaks

A letterbox holds a letter until you take it out, and then it is yours. An
input is not a copy you take out; it is a live reading. Calling `title()` asks
what the value is now, and calling it inside a derivation or an effect
subscribes to it, exactly as a signal you declared yourself would.

The second leak is the interesting one. A letterbox cannot deliver "nothing" in
a way you would notice, and the router can. Angular's router documentation says
that "when an input does not have an item in the route data with a matching
key, this input is set to `undefined`. This prevents previous information from
being retained if the data got removed from the route." Absence is a delivery,
it overwrites what was there, and it is the mechanism behind one component
serving both `blog/new` and `blog/:id`.

Third, a letterbox cannot insist on being used. `input.required` can, and a
required input read before anything has been bound — in the constructor, for
example — reports `NG0950: Required input is accessed before a value is set`.

## How it works

`input()` declares an optional input; `input.required()` declares one the
parent must bind. Both return read-only signals — "signals created by the
`input` function are read-only" — and "if an input without a default value is
not set, its value is `undefined`".

```ts
readonly title = input.required<string>();
readonly minutes = input(0, { transform: numberAttribute });
readonly slug = input.required<string>({ alias: 'lessonSlug' });
```

A transform converts what a template binding provides into what the class
wants, which is how an attribute written with no value becomes `true` rather
than the empty string. An alias separates the name a template uses from the
property name in TypeScript.

Route parameters reach inputs only when the router is asked to bind them.

```ts
provideRouter(routes, withComponentInputBinding())
```

That feature binds query parameters, path and matrix parameters, static route
data and resolver results, and duplicate keys resolve "from least to greatest,
meaning that resolvers have the highest precedence". Without it, a component
whose input is named after a route parameter simply never receives one.

Because the input is a signal, the way to react to a new value is to read it in
a derivation or an effect rather than to implement a lifecycle hook.

```ts
protected readonly isCreateMode = computed(() => this.id() === undefined);
```

## Common mistakes

**Writing to an input.** The component owns the reading, not the value:
`this.title.set('')` fails with `error TS2339: Property 'set' does not exist on
type 'InputSignal<string>'`.

**Testing an optional input for truthiness.** `id ? 'edit' : 'create'` treats
the empty string as absence. The third listing prints the difference:
`'' -> loose: create, strict: malformed`. On a route, an empty segment is a
malformed URL and should be refused, while a missing segment is a different
screen.

**Forgetting `withComponentInputBinding()`.** Every route-bound input stays
`undefined`, and a component written as above concludes that every post is a
new one — with no error anywhere, because `undefined` is a value that input is
declared to accept.

**Reading a required input in the constructor.** Bindings have not been applied
yet, and Angular reports NG0950.

## Check yourself

<details><summary>Why is an optional input's absence not an error?</summary>
Because absence carries information. The router sets an unmatched input to
<code>undefined</code>, which is how one component distinguishes a create route
from an edit route.
</details>

<details><summary>What does a transform buy you?</summary>
It converts at the boundary, once, so the class works with the type it wants
rather than with whatever a template binding happened to supply.
</details>

<details><summary>Where does a component react to a changed input?</summary>
In a derivation, if the reaction is a value, and in an effect if it is work.
The input is a signal, so both are ordinary reads.
</details>

## Full listings

1. Required, optional, transformed and aliased inputs, and the write that does not compile.
2. One editor component behind two routes, told apart by an absent input.
3. Absent versus empty, printed side by side.
