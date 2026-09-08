## Why this exists

Django's template language is deliberately less capable than Python, and people
new to it read that as a limitation to be worked around. It is a boundary. A
template that can only arrange values it was given cannot hide a business rule
where nobody will look for it, cannot open a database connection in the middle
of a page, and cannot be the reason a page is slow in a way the view's tests
would never catch. The restrictions are the feature. Knowing exactly which ones
exist — and which apparent restrictions are actually silent failures — is what
stops you fighting the language.

## The idea

A template is a printing press. It is handed a tray of type — the context — and
it stamps out a page. It can arrange what it was given, repeat it, and choose
between two arrangements it already holds. What it cannot do is invent a word
that was not in the tray, or go and fetch more type mid-run.

### Where the analogy breaks

A press that ran out of a letter would jam and stop. Django's template engine
does the opposite: a lookup that fails renders as the empty string and carries
on. Listing 1 shows four different failures — a name not in the context, an
attribute that does not exist, an index past the end of a list, and a method
that needs an argument — and every one of them produces `''`. Nothing is logged
at the point of failure. A blank space on a page is the only symptom, and it
looks exactly like a value that was legitimately empty.

The press image also implies the tray is inert. It is not. A callable found by
attribute lookup is *called*, with no arguments, during rendering. A property is
evaluated. If that property runs a query, the template ran a query.

## How it works

A variable is resolved by trying, in order, dictionary lookup, attribute
lookup, then numeric index. Listing 1 walks all three. When attribute lookup
finds something callable, the engine calls it:

```html
{{ basket.total }}       {# calls basket.total() — renders 1050 #}
{{ basket.line_count }}  {# a property, evaluated — renders 2 #}
{{ basket.discounted }}  {# needs an argument, so renders '' #}
```

The tag language allows comparison and boolean logic, and refuses arithmetic,
calls with arguments, and assignment. Listing 2 measures which is which:

```html
{% if seats > 2 %}many{% endif %}   {# allowed #}
{% if a + b %}yes{% endif %}        {# TemplateSyntaxError #}
{{ a.bit_length(2) }}               {# TemplateSyntaxError #}
```

The difference is deliberate. Choosing between two arrangements is a rendering
decision. Computing a new value is not, and Django 6.1 makes you do it in the
view or in a filter.

Autoescaping is on by default, so a value containing markup is escaped on the
way out unless something explicitly says otherwise:

```html
{{ comment }}         {# &lt;script&gt;alert(1)&lt;/script&gt; #}
{{ comment|safe }}    {# <script>alert(1)</script> #}
```

Listing 3 shows all three ways to switch it off — the `safe` filter, an
`autoescape off` block, and `mark_safe()` on the value in Python — and then
shows the pattern that keeps decisions where they belong: the view filters the
rows and hands the template a list it can only loop over.

## Common mistakes

**Reading a blank page as a template bug.** It is usually a lookup that failed
silently. Print the value in the view first; if it is right there and blank in
the template, the name or the path is wrong.

**Putting a method that takes arguments in a template.** It renders as nothing.
Listing 1's `basket.discounted` is the case. Compute it in the view, or expose a
no-argument property.

**Calling a queryset attribute in a loop.** The template will happily evaluate
it once per iteration; that is the N+1 problem in a place where no test is
looking.

**Reaching for `|safe` to make markup appear.** It disables the protection that
stops a user's comment becoming a script tag. Use it on values you built, never
on values a user supplied.

## Check yourself

<details><summary><code>{{ order.total }}</code> renders nothing. What are the possibilities?</summary>

`order` is not in the context, `order` has no `total`, or `total` is a method
that requires an argument. All three render `''`. Listing 1 produces each of
them.

</details>

<details><summary>Why does <code>{% if a + b %}</code> not work when <code>{% if a > b %}</code> does?</summary>

Comparison chooses between arrangements the template already holds; addition
produces a new value. Django 6.1 raises `TemplateSyntaxError` for the second and
accepts the first, which keeps computation on the Python side of the boundary.

</details>

<details><summary>Where should the rule "hide lines with a negative amount" live?</summary>

In the view. Listing 3 filters before rendering, so the template never receives
the row. A `{% if %}` in the template would work and would put a business rule
somewhere no unit test can reach.

</details>

## Listings

1. `templates-render-they-do-not-decide-1.py` — variable lookup, and four ways
   to fail silently.
2. `templates-render-they-do-not-decide-2.py` — what the tag language allows and
   refuses.
3. `templates-render-they-do-not-decide-3.py` — autoescaping, and a view that
   decided first.
