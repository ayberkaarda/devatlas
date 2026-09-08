"""Measure first: what a loop over related objects actually costs.

Run with: python the-n-plus-one-problem-1.py
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
from django.db.models import FETCH_ONE, FETCH_PEERS, FETCH_RAISE
from django.db.models.fetch_modes import FieldFetchBlocked
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


def seed(book_count):
    Book.objects.all().delete()
    Author.objects.all().delete()
    Publisher.objects.all().delete()
    publisher = Publisher.objects.create(name="house")
    for n in range(book_count):
        author = Author.objects.create(name=f"author{n:02d}", publisher=publisher)
        Book.objects.create(title=f"book{n:02d}", author=author)


def queries(label, action):
    with CaptureQueriesContext(connection) as captured:
        action()
    print(f"{label}: {len(captured)}")


for book_count in (5, 10, 20):
    seed(book_count)
    with CaptureQueriesContext(connection) as captured:
        [book.author.name for book in Book.objects.all()]
    print(f"books={book_count} queries={len(captured)} equals 1 + N: {len(captured) == 1 + book_count}")

seed(10)
queries("read only fields of Book", lambda: [book.title for book in Book.objects.all()])
queries("read book.author.name", lambda: [book.author.name for book in Book.objects.all()])
queries("read book.author.publisher.name", lambda: [b.author.publisher.name for b in Book.objects.all()])
queries("read book.author_id (no join needed)", lambda: [book.author_id for book in Book.objects.all()])

# The related object is cached on the instance once it has been loaded.
book = Book.objects.first()
queries("first access to book.author", lambda: book.author.name)
queries("second access to book.author", lambda: book.author.name)

# Django 6.1 lets a queryset say what an unfetched field should do.
queries(
    "fetch_mode(FETCH_ONE), the default",
    lambda: [b.author.name for b in Book.objects.fetch_mode(FETCH_ONE)],
)
queries(
    "fetch_mode(FETCH_PEERS)",
    lambda: [b.author.name for b in Book.objects.fetch_mode(FETCH_PEERS)],
)
queries(
    "fetch_mode(FETCH_PEERS) for a field only() deferred",
    lambda: [b.author_id for b in Book.objects.only("title").fetch_mode(FETCH_PEERS)],
)

with CaptureQueriesContext(connection) as captured:
    try:
        [b.author.name for b in Book.objects.fetch_mode(FETCH_RAISE)]
    except FieldFetchBlocked as error:
        print(f"fetch_mode(FETCH_RAISE): {type(error).__name__} after {len(captured)} query")

# FETCH_PEERS answers for the instances of one queryset. A reverse many
# relation is a manager, and asking it for rows is a query of its own.
queries(
    "fetch_mode(FETCH_PEERS) across a reverse many relation",
    lambda: [[b.title for b in a.books.all()] for a in Author.objects.fetch_mode(FETCH_PEERS)],
)
