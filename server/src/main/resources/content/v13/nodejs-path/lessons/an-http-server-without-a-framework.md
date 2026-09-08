## Why this exists

You will use a framework. The reason to write a server without one first is that a framework's
defaults become your defaults, and defaults you did not choose are hard to argue with later. How
large may a request body be? What does a malformed JSON body return? Which header does a 405
have to carry? Node.js 22's `node:http` answers none of those for you, which makes every one of
them a decision you can see. Afterwards the framework stops being magic and becomes a list of
decisions someone else made, most of which you will keep.

## The idea

The `http` module is a sorting desk with no clerk. An envelope lands in front of you and a blank
sheet of paper sits beside it. A framework is the clerk you hire: they open the envelope, file it
under a heading, and write the standard reply.

### Where the analogy breaks

The clerk does nothing you could not do. Every routing table, body parser and error handler is
ordinary JavaScript over the same two objects; there is no privileged access, and the framework is
not talking to the socket in a way your code cannot.

The analogy also suggests the clerk's cost is their salary — the code you pull in. The real cost is
different: it is the decisions taken silently. A body limit you never set, an error page that leaks a
stack trace, a route that matches more than you meant. Those are not lines of code, they are
defaults, and the reason to have written the desk yourself once is to know which ones to look up.

Finally, a clerk works one envelope at a time. Your handler is called once per request and may
have many in flight, so anything stored outside the handler is shared between them.

## How it works

`createServer` takes a function called with a request and a response. The request is a readable
stream; the response is a writable one. `req.url` is a path and query, never an absolute URL, so
parsing it needs a base.

```js
const url = new URL(req.url, 'http://localhost');
url.searchParams.getAll('tag');   // every value; get() returns only the first
```

Header names arrive lower-cased in `req.headers`, and repeated headers are joined into one string
there while `req.headersDistinct` keeps them apart. Every value is text: a `limit` parameter is the
string `'10'` until you convert it.

Reading a body means consuming the stream, and the size is the client's choice until you make it
yours.

```js
let size = 0;
for await (const chunk of req) {
  size += chunk.length;
  if (size > MAX_BODY_BYTES) throw tooLarge();
  chunks.push(chunk);
}
```

A response starts once. After `writeHead` or the first write, the status and headers are gone, and
`res.headersSent` is `true`; calling `writeHead` again throws `ERR_HTTP_HEADERS_SENT` rather than
pretending to work. Errors go back as a code and a sentence, never as a stack.

```js
res.writeHead(404, { 'content-type': 'application/json; charset=utf-8' });
res.end(JSON.stringify({ code: 'not_found', message: 'no such route' }));
```

## Common mistakes

**Responding twice.** An error handler that writes a 500 after the handler already replied throws
`ERR_HTTP_HEADERS_SENT`. Check `res.headersSent` before writing an error response.

**Reading a body with no limit.** Without a cap, one client decides how much memory your process
uses.

**Looking up a header with the casing the client sent.** `req.headers['X-Request-Id']` is
`undefined`; the keys are lower-cased.

**Returning 200 with an error body.** Status codes are the part every proxy, client and monitor
understands. Listing 1 answers 404, 405 with an `allow` header, and 200 as appropriate.

## Check yourself

<details><summary>Why does <code>new URL(req.url)</code> throw?</summary>

`req.url` is a path and query, not an absolute URL. It needs a base; the base is discarded once
you have the pathname and search parameters.

</details>

<details><summary>What does <code>res.headersSent</code> being <code>true</code> tell you?</summary>

The status line and headers have already been written to the socket. Nothing about them can be
changed, so an error path must not try to send its own response.

</details>

<details><summary>Two requests arrive together. What do they share?</summary>

Everything outside the handler function: module-level variables, caches, counters. The request and
response objects are per-request; anything else you reach for is not.

</details>

## Listings

1. `an-http-server-without-a-framework-1.js` — routing by method and path, with 200, 404 and 405.
2. `an-http-server-without-a-framework-2.js` — reading a body, capping it, and one error shape.
3. `an-http-server-without-a-framework-3.js` — headers, query strings, and the response that has
   already started.
