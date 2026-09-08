"""prefetch_related: a second query, then a join done in Python.

Run with: python the-n-plus-one-problem-3.py
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
from django.db.models import Prefetch
from django.test.utils import CaptureQueriesContext


class Tag(models.Model):
    label = models.CharField(max_length=20)

    class Meta:
        app_label = "__main__"
        db_table = "tag"


class Author(models.Model):
    name = models.CharField(max_length=50)

    class Meta:
        app_label = "__main__"
        db_table = "author"
        ordering = ["name"]


class Book(models.Model):
    title = models.CharField(max_length=50)
    author = models.ForeignKey(Author, on_delete=models.CASCADE, related_name="books")
    tags = models.ManyToManyField(Tag, related_name="books")

    class Meta:
        app_label = "__main__"
        db_table = "book"
        ordering = ["title"]


with connection.schema_editor() as editor:
    for model in (Tag, Author, Book):
        editor.create_model(model)

tags = [Tag.objects.create(label=f"tag{n}") for n in range(3)]
for n in range(10):
    author = Author.objects.create(name=f"author{n:02d}")
    book = Book.objects.create(title=f"book{n:02d}", author=author)
    book.tags.set(tags[: (n % 3) + 1])


def queries(label, action):
    with CaptureQueriesContext(connection) as captured:
        action()
    print(f"{label}: {len(captured)}")


# Reverse foreign key: one author, many books.
queries("reverse FK, plain", lambda: [[b.title for b in a.books.all()] for a in Author.objects.all()])
queries(
    "reverse FK, prefetch_related('books')",
    lambda: [[b.title for b in a.books.all()] for a in Author.objects.prefetch_related("books")],
)

# Many to many.
queries("m2m, plain", lambda: [[t.label for t in b.tags.all()] for b in Book.objects.all()])
queries(
    "m2m, prefetch_related('tags')",
    lambda: [[t.label for t in b.tags.all()] for b in Book.objects.prefetch_related("tags")],
)

# A forward FK can be prefetched, but it costs a query select_related does not.
queries(
    "forward FK with prefetch_related",
    lambda: [b.author.name for b in Book.objects.prefetch_related("author")],
)
queries(
    "forward FK with select_related",
    lambda: [b.author.name for b in Book.objects.select_related("author")],
)

# Prefetch() lets the second query be shaped, and stores it under a new name.
queries(
    "Prefetch with a filtered inner queryset",
    lambda: [
        [t.label for t in b.busy_tags]
        for b in Book.objects.prefetch_related(
            Prefetch("tags", queryset=Tag.objects.filter(label__gt="tag0"), to_attr="busy_tags")
        )
    ],
)

books = list(Book.objects.prefetch_related("tags"))
print("books:", len(books))
print("tag labels on the last book:", sorted(t.label for t in books[-1].tags.all()))
