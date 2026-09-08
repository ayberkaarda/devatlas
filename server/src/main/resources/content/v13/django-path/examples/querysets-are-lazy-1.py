"""Building a query costs nothing. Counting the queries proves it.

Run with: python querysets-are-lazy-1.py
"""

import django
from django.conf import settings

settings.configure(
    INSTALLED_APPS=["__main__"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.db import connection, models
from django.test.utils import CaptureQueriesContext


class Article(models.Model):
    headline = models.CharField(max_length=20)
    words = models.IntegerField()

    class Meta:
        app_label = "__main__"
        db_table = "article"
        ordering = ["headline"]


with connection.schema_editor() as editor:
    editor.create_model(Article)

Article.objects.bulk_create(
    [Article(headline=f"a{n:02d}", words=n * 10) for n in range(1, 21)]
)


def queries(label, action):
    """Run action and report how many SQL statements it caused."""
    with CaptureQueriesContext(connection) as captured:
        action()
    print(f"{label}: {len(captured)}")


queries("assign Article.objects.all()", lambda: Article.objects.all())
queries(
    "filter, exclude and order_by, chained",
    lambda: Article.objects.filter(words__gt=50).exclude(headline="a09").order_by("words"),
)

pending = Article.objects.filter(words__gt=50)
queries("slice it [:3]", lambda: pending[:3])
queries("ask for its .query attribute", lambda: str(pending.query))

# Now ask for the results.
queries("iterate it", lambda: [article.headline for article in pending])
queries("call list() on a fresh queryset", lambda: list(Article.objects.all()))
queries("call len() on a fresh queryset", lambda: len(Article.objects.all()))
queries("call bool() on a fresh queryset", lambda: bool(Article.objects.all()))
queries("take a slice with a step [::2]", lambda: Article.objects.all()[::2])
queries("index into it [0]", lambda: Article.objects.all()[0])
queries("call repr() on it", lambda: repr(Article.objects.all()))

print("ordering came from Meta:", [a.headline for a in Article.objects.all()[:3]])
