## Why this exists

Almost everything an application shows came from somewhere else, and the trip
is the part that goes wrong: a token expires, a connection drops, a server
answers with something nobody planned for. Angular 22 gives you `HttpClient`
for the request and interceptors for everything that must happen to every
request, and the design question this lesson answers is not how to call an
endpoint. It is where a failure is allowed to be a failure — which layer turns
a status code into something the rest of the application can reason about, and
which layer turns that into words a reader sees.

## The idea

An interceptor chain is a row of desks a form crosses on its way out of a
building. The first desk stamps the language you want to be answered in, the
second attaches your pass, the third notes the time. Each desk hands the form
to the next, and the answer comes back along the same row in reverse, so a desk
can act on the way out, on the way back, or both.

### Where the analogy breaks

A clerk writes on the form. An interceptor may not: "most aspects of
`HttpRequest` and `HttpResponse` instances are immutable, and interceptors
cannot directly modify them". A desk that wants a change makes a copy with
`clone` and passes the copy on, so the request an earlier desk saw is still the
request it saw.

The second leak is that nothing is moving. The desks are arranged, and the form
does not leave the building until somebody subscribes: "no actual request
happens until the `Observable` is subscribed. Only then is the request actually
dispatched to the server." Arranging the chain is not sending anything, and
arranging it twice sends twice — "subscribing to the same `Observable` multiple
times will trigger multiple backend requests".

Finally, the desks are not people who decide anything. Order is what gives them
their meaning, and it is yours to choose: interceptors "are chained together in
the order that you've listed them in the providers".

## How it works

An interceptor is a function taking the outgoing request and the next step.

```ts
export const localeHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  const locale = inject(ACTIVE_LOCALE)();
  return next(request.clone({ setHeaders: { 'Accept-Language': locale } }));
};
```

`inject` works there because interceptors run "in the injection context of the
injector which registered them". They are registered once, in order.

```ts
provideHttpClient(withInterceptors([localeHeaderInterceptor, timeoutInterceptor]))
```

Order is behaviour. A deadline placed last is innermost, so it bounds each
network attempt rather than a whole exchange including a retry; placed first it
would bound the exchange instead. Neither is wrong, and choosing without
noticing is.

A failure arrives as an `HttpErrorResponse`. Its `status` is the server's, with
one exception that decides most of the error handling in an offline-capable
application: a request that never reached a server has status nought.

```ts
if (error.status === 0) {
  return new PlatformError('NETWORK_UNAVAILABLE');
}
return new PlatformError(body.code ?? 'INTERNAL_ERROR');
```

That translation happens once, at the edge. Above it the application deals in
codes; at the screen a code becomes a translation key. The third listing runs
that mapping over five failures and prints what each becomes, including a
server code the interface has no words for, which becomes the generic one
rather than being shown raw.

## Common mistakes

**Assigning to a request instead of cloning it.** `request.url = ...` fails
with `error TS2540: Cannot assign to 'url' because it is a read-only property.`
The nastier sibling compiles: `request.headers.set(...)` returns a new
`HttpHeaders` and changes nothing, so the header is silently missing.

**Forgetting that nothing happens without a subscription.** A method that
builds a request and returns without subscribing sends no request, and the
absence looks exactly like a server that answered with nothing.

**Subscribing twice for one answer.** Two bindings over the same observable are
two requests. Where one answer is wanted, take the value once.

**Showing the server's message.** The body's `message` is written for a
developer, in one language, and it leaks internals. The `code` is the stable
part; the sentence a reader sees belongs to the interface.

## Check yourself

<details><summary>Why does an interceptor clone the request?</summary>
Because the request is immutable. Cloning also keeps the chain honest: each
desk passes on its own copy, so a later change cannot rewrite what an earlier
one acted upon.
</details>

<details><summary>What does a status of nought mean, and why is it special?</summary>
The request never reached the server. It says nothing about credentials or
content, so it must not be handled as though the server had refused something.
</details>

<details><summary>Where is the right place to turn a failure into a sentence?</summary>
At the screen, from a code. The edge produces the code; the interface owns the
words, which is also what makes them translatable.
</details>

## Full listings

1. Two interceptors and the registration whose order decides their meaning.
2. A typed client that turns transport failures into one domain error.
3. The mapping from status and code to a translation key, run over five failures.
