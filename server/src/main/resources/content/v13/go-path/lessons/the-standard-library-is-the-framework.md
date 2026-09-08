## Why this exists

The usual first question about a language is which web framework to adopt, and in Go the
answer is usually that the question can be deferred. `net/http` in Go 1.27 already carries a
server, a router whose patterns include the method and named wildcards, a client, and a testing
package that runs a real server on a loopback port or captures a response without a socket at
all. `encoding/json` already maps structs to the wire and refuses fields you did not declare.
`context` already carries deadlines and cancellation across a call graph. None of that is a
minimal core waiting for a library on top; it is the thing a framework would be wrapping. The
practical consequence is that a service can be read in one sitting, because there is no
framework between the route and the function.

## The idea

Middleware is a set of nesting dolls, and `http.Handler` — one method, `ServeHTTP` — is the
shape they all share. Each doll holds the next; the innermost one does the work. A function
that takes a handler and returns a handler is how a new doll is carved.

### Where the analogy breaks

Three ways.

A doll is opened once, from the outside in. A middleware chain runs past each layer *twice*,
because `next.ServeHTTP(w, r)` returns: everything written before the call happens on the way
in, and everything after it happens on the way out. That is where timing, recovery and response
logging live.

Dolls are symmetric, and chains are not. Order decides behaviour, and the effect is visible:
in the third listing the audit wrapper is outermost, so it records all three requests including
the two the authentication wrapper rejected — `audit log: [GET /whoami GET /whoami GET
/whoami]`. Put it inside and it would have recorded one.

And a doll contains exactly one doll. A handler is not so confined: `http.ServeMux` is itself
an `http.Handler`, so a router nests inside a chain, a chain nests inside a router, and a
sub-router nests inside either.

## How it works

A pattern names a method and a path, and a wildcard in braces is read back by name. The handler
never parses a URL:

```go
mux := http.NewServeMux()
mux.HandleFunc("GET /items/{id}", func(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")             // captured by the pattern
	...
})
```

JSON is described by struct tags. The decoder can be told to refuse unknown fields, which turns
a silently dropped typo into a `400` the caller can act on, and every failure returns one body
shape rather than prose:

```go
decoder := json.NewDecoder(r.Body)
decoder.DisallowUnknownFields()
if err := decoder.Decode(&item); err != nil {
	writeJSON(w, http.StatusBadRequest, APIError{Code: "MALFORMED_BODY", ...})
	return
}
```

Middleware is one signature, `func(http.Handler) http.Handler`, and request-scoped data travels
in the context rather than in a global. A key of a private type is what keeps two packages'
values apart:

```go
type ctxKey int
const userKey ctxKey = iota

ctx := context.WithValue(r.Context(), userKey, user)
next.ServeHTTP(w, r.WithContext(ctx))
```

Testing needs no running service: `httptest.NewRecorder` is an `http.ResponseWriter` that keeps
what was written, and `httptest.NewServer` gives the whole stack a real port when you want one.

## Common mistakes

**Writing the body and then the status.** The status is already sent, so the call is ignored
and the server logs it — here with the log's leading date and time omitted, since they differ
on every run:

```text
http: superfluous response.WriteHeader call from main.handler (superfluous-writeheader.go:11)
status: 200
```

**Registering two patterns that match the same requests.** This panics as the routes are built,
which is the right time to find out; the registration locations in the message are absolute
paths and are elided here:

```text
panic: pattern "GET /items/{name}" (registered at ...) conflicts with pattern "GET /items/{id}" (registered at ...):
	GET /items/{name} matches the same requests as GET /items/{id}
```

**Unmarshalling into a value instead of a pointer.** The struct comes back untouched, and the
error says why:

```text
item={ID:} err=json: Unmarshal(non-pointer main.Item)
```

**Expecting an unexported field to be marshalled.** It is skipped in silence, with no error to
notice:

```text
{"ID":"a-1"} err=<nil>
```

## Check yourself

<details><summary>What does <code>DisallowUnknownFields</code> buy, and what does it cost?</summary>

It turns a misspelled field from a silently ignored key into a decode error the handler can
report. The cost is that a client sending a field a newer version added will be refused by an
older server, so it suits internal APIs and strict contracts more than public ones.

</details>

<details><summary>Where does a middleware put work that must run after the handler?</summary>

After the call to `next.ServeHTTP`. The call returns once the inner handler is done, so the
lines below it run on the way out — which is where response logging and recovery belong.

</details>

<details><summary>Why is a context key declared with a private named type rather than a string?</summary>

Because context values share one namespace across every package in the process. Two packages
using the string `"user"` would overwrite each other; a key of an unexported type cannot be
constructed outside the package that declared it.

</details>

## Listings

1. `the-standard-library-is-the-framework-1.go` — method-and-wildcard routing, and two ways to test it.
2. `the-standard-library-is-the-framework-2.go` — JSON decoding, one error shape, and what the encoder sorts.
3. `the-standard-library-is-the-framework-3.go` — middleware, context values, and cancellation.
