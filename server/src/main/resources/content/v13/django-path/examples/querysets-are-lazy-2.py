"""The result cache: one query per queryset object, not per use.

Run with: python querysets-are-lazy-2.py
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
    with CaptureQueriesContext(connection) as captured:
        action()
    print(f"{label}: {len(captured)}")


held = Article.objects.filter(words__gt=100)
queries("first iteration of a held queryset", lambda: [a.headline for a in held])
queries("second iteration of the same object", lambda: [a.headline for a in held])
queries("len() once it is cached", lambda: len(held))
queries("bool() once it is cached", lambda: bool(held))
print("cached rows:", len(held))

# A new queryset object has an empty cache, even for the same rows.
queries(
    "the same filter written again",
    lambda: [a.headline for a in Article.objects.filter(words__gt=100)],
)
queries("filtering the cached one produces a new query", lambda: list(held.filter(words__lt=150)))
queries("the cached one is still cached", lambda: len(held))

# count(), exists() and iterator() deliberately do not use the cache.
fresh = Article.objects.all()
queries("count() on an unevaluated queryset", lambda: fresh.count())
queries("len() after that count()", lambda: len(fresh))
queries("count() once the cache is full", lambda: fresh.count())
queries("exists() on a fresh queryset", lambda: Article.objects.filter(words__gt=1000).exists())
queries("iterator() ignores the cache", lambda: list(fresh.iterator()))
