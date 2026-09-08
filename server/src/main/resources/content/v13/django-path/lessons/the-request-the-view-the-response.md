## Why this exists

Most Django questions that begin "why is this not working" are really questions
about where in the request's path something went wrong. A 404 can come from the
URLconf finding no pattern or from a view raising `Http404`, and those are
different bugs. A header that never arrives may have been set by a view that a
middleware short-circuited past. A view that behaves in a test and not in the
browser is usually a view that was called directly, with none of the wrapping
that the real path applies. Knowing the order in which the pieces run turns all
of these from mysteries into one question with an obvious place to look.

## The idea

A request goes through the application the way a parcel goes through a sorting
office. The address is read once, at the door, and decides which desk the parcel
goes to. On the way in it passes a line of inspectors; any of them may stamp it,
open it, or turn it back without it ever reaching the desk. The desk does the
actual work and produces something to send back, and that reply walks out past
the same inspectors in reverse order, each of whom may add a stamp of their own.

### Where the analogy breaks

A sorting office reads the address to find a physical destination that exists
whether or not anything is addressed to it. Django's URLconf is a list of
patterns tried in order, and the first one that matches wins — so two patterns
can both describe a path, and the one written first takes it. Listing 2 shows
`/articles/42/` matching a `slug` pattern perfectly well and never reaching it,
because an `int` pattern is listed above.

The inspectors are also not passive. Django's middleware are nested callables,
not a queue: each one *calls* the next and receives its return value. That is
why an inspector further out still gets to touch a response produced by one
further in, including a response that short-circuits the view entirely.

## How it works

A URLconf maps a route to a callable and gives it a name. The angle-bracket
converters both match and convert.

```python
urlpatterns = [
    path("articles/<int:article_id>/", article_detail, name="article-detail"),
]
```

`article_id` arrives as an `int`, not a string; listing 2 prints the type.
`reverse("article-detail", args=[42])` builds the path back, and raises
`NoReverseMatch` if the argument does not fit the converter — which is why
naming routes and reversing them beats writing URLs as literals.

A view is an ordinary function that takes an `HttpRequest` and returns an
`HttpResponse`. Nothing else is required of it.

```python
def article_detail(request, article_id):
    if article_id > 100:
        raise Http404
    return JsonResponse({"id": article_id, "method": request.method})
```

Middleware in Django 6.1 is a callable that is handed the next callable in the
chain when it is constructed:

```python
class GateMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if request.headers.get("X-Blocked") == "yes":
            return HttpResponse(status=403)     # the view never runs
        return self.get_response(request)
```

Listing 3 records the order and gets
`['trace: before', 'gate: before', 'view', 'gate: after', 'trace: after']`.
When the gate short-circuits, the trace becomes
`['trace: before', 'gate: before', 'gate: short circuit', 'trace: after']` —
no `view`, but the outer middleware's response phase still ran and its header
is still on the 403.

## Common mistakes

**Reading `MIDDLEWARE` order as one direction.** It is first-to-last on the way
in and last-to-first on the way out. Listing 3 prints both halves.

**Calling a view directly and thinking the path was exercised.** Listing 3 calls
`home(request)` with a `RequestFactory` request; the body is `nothing` rather
than the middleware's stamp, and the response carries no `X-Trace` header. That
is the right tool for unit-testing a view in isolation and the wrong one for
proving the page works.

**Hard-coding paths.** `reverse()` fails loudly when a route changes; a literal
`"/articles/42/"` in a template goes quietly wrong.

**Assuming a 404 came from routing.** Listing 1 produces one from a matched
route whose view raised `Http404`, and one from no pattern matching. Same status,
different fix.

## Check yourself

<details><summary>Two patterns could match a path. Which view runs?</summary>

The one whose pattern appears first in `urlpatterns`. Listing 2 demonstrates a
numeric segment that the `slug` converter would accept, taken by the `int`
pattern above it.

</details>

<details><summary>A middleware returns a response without calling <code>get_response</code>. What still runs?</summary>

Everything outside it, on the way out. The view and any middleware listed after
it do not run at all. Listing 3 shows a 403 that still carries the outermost
middleware's header.

</details>

<details><summary>Why prefer <code>reverse()</code> to a literal URL?</summary>

Because the URLconf becomes the single description of the path. A rename breaks
the build rather than the page, and `NoReverseMatch` catches arguments the route
could never have accepted.

</details>

## Listings

1. `the-request-the-view-the-response-1.py` — the whole path end to end.
2. `the-request-the-view-the-response-2.py` — resolution, reversal and
   converters.
3. `the-request-the-view-the-response-3.py` — middleware order, and what a
   direct call skips.
