"""What the template language refuses to do, and why that is the point.

Run with: python templates-render-they-do-not-decide-2.py
"""

import django
from django.conf import settings

settings.configure(
    DEBUG=False,
    INSTALLED_APPS=[],
    DATABASES={},
    USE_TZ=True,
    TEMPLATES=[
        {
            "BACKEND": "django.template.backends.django.DjangoTemplates",
            "DIRS": [],
            "APP_DIRS": False,
            "OPTIONS": {},
        }
    ],
)
django.setup()

from django.template import Context, Template, TemplateSyntaxError

context = Context({"a": 2, "b": 3, "seats": 4, "note": ""})


def attempt(label, source):
    try:
        rendered = Template(source).render(context)
    except TemplateSyntaxError:
        print(f"{label}: TemplateSyntaxError")
    else:
        print(f"{label}: rendered {rendered!r}")


# Comparison is allowed; arithmetic is not.
attempt("comparison in if", "{% if seats > 2 %}many{% else %}few{% endif %}")
attempt("boolean operators", "{% if seats and not note %}needs a note{% endif %}")
attempt("in operator", "{% if note in 'abc' %}substring{% else %}no{% endif %}")
attempt("addition", "{% if a + b %}yes{% endif %}")
attempt("calling with an argument", "{{ a.bit_length(2) }}")
attempt("assignment", "{% seats = 5 %}")

# What is available instead: filters, and a value computed before rendering.
attempt("add filter", "{{ a|add:b }}")
attempt("with tag names a value", "{% with total=seats %}{{ total }}{% endwith %}")

# Looping is a rendering concern, so it is allowed, including the empty case.
attempt("for with empty", "{% for x in nothing %}{{ x }}{% empty %}nothing here{% endfor %}")
attempt("forloop counter", "{% for x in '..' %}{{ forloop.counter }}{% endfor %}")
