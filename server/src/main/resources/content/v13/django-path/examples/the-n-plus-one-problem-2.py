"""select_related: one query with a join, for what a row points at.

Run with: python the-n-plus-one-problem-2.py
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


class Publisher(models.Model):
    name = models.CharField(max_length=50)

    class Meta:
        app_label = "__main__"
        db_table = "publisher"


class Author(models.Model):
    name = models.CharField(max_length=50)
    publisher = models.ForeignKey(Publisher, on_delete=models.CASCADE)

    class Meta:
        app_label = "__main__"
        db_table = "author"


class Book(models.Model):
    title = models.CharField(max_length=50)
    author = models.ForeignKey(Author, on_delete=models.CASCADE, related_name="books")

    class Meta:
        app_label = "__main__"
        db_table = "book"
        ordering = ["title"]


with connection.schema_editor() as editor:
    for model in (Publisher, Author, Book):
        editor.create_model(model)

publisher = Publisher.objects.create(name="house")
for n in range(10):
    author = Author.objects.create(name=f"author{n:02d}", publisher=publisher)
    Book.objects.create(title=f"book{n:02d}", author=author)


def queries(label, action):
    with CaptureQueriesContext(connection) as captured:
        result = action()
    print(f"{label}: {len(captured)}")
    return result


queries("plain, reading author.name", lambda: [b.author.name for b in Book.objects.all()])
queries(
    "select_related('author'), reading author.name",
    lambda: [b.author.name for b in Book.objects.select_related("author")],
)
queries(
    "select_related('author'), reading author.publisher.name",
    lambda: [b.author.publisher.name for b in Book.objects.select_related("author")],
)
queries(
    "select_related('author__publisher')",
    lambda: [b.author.publisher.name for b in Book.objects.select_related("author__publisher")],
)
queries(
    "select_related() with no arguments follows every non-null FK",
    lambda: [b.author.publisher.name for b in Book.objects.select_related()],
)

# select_related only helps if the loop actually reads the related object.
queries(
    "select_related('author') but only titles are read",
    lambda: [b.title for b in Book.objects.select_related("author")],
)

titles = queries(
    "the join does not multiply rows for a forward FK",
    lambda: [b.title for b in Book.objects.select_related("author__publisher")],
)
print("rows returned:", len(titles), "books in the table:", Book.objects.count())
print("first three titles:", titles[:3])
