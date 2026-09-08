"""What a template variable lookup actually does.

Run with: python templates-render-they-do-not-decide-1.py
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

from django.template import Context, Template


class Basket:
    def __init__(self, lines):
        self.lines = lines
        self.owner = "ada"

    def total(self):
        return sum(quantity * price for quantity, price in self.lines)

    def discounted(self, rate):
        return self.total() * (1 - rate)

    @property
    def line_count(self):
        return len(self.lines)


context = Context(
    {
        "basket": Basket([(2, 300), (1, 450)]),
        "prices": {"total": 1050},
        "names": ["first", "second"],
    }
)


def show(label, source):
    print(f"{label}: {Template(source).render(context)!r}")


# The lookup tries dictionary key, then attribute, then numeric index.
show("dictionary key", "{{ prices.total }}")
show("attribute", "{{ basket.owner }}")
show("list index", "{{ names.0 }}")

# A callable found by attribute lookup is called with no arguments.
show("method taking no arguments", "{{ basket.total }}")
show("property", "{{ basket.line_count }}")

# A method that needs an argument cannot be called, so the lookup fails and
# renders as the empty string.
show("method needing an argument", "{{ basket.discounted }}")

# Failed lookups are silent, by design.
show("name that is not in the context", "{{ nowhere }}")
show("attribute that does not exist", "{{ basket.nothing }}")
show("index past the end", "{{ names.9 }}")

# A filter is the way to change a value on the way out.
show("default filter", "{{ nowhere|default:'fallback' }}")
show("chained filters", "{{ basket.owner|upper|slice:':2' }}")
