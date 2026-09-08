"""Four ways to put the queries back after you removed them.

Run with: python the-n-plus-one-problem-4.py
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
from django.db.models import Count
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


# 1. Filtering inside the loop ignores the prefetched cache.
queries(
    "prefetch, then .all() in the loop",
    lambda: [[t.label for t in b.tags.all()] for b in Book.objects.prefetch_related("tags")],
)
queries(
    "prefetch, then .filter() in the loop",
    lambda: [
        [t.label for t in b.tags.filter(label__gt="tag0")]
        for b in Book.objects.prefetch_related("tags")
    ],
)

# 2. only() defers the rest of the row, including the foreign key column.
queries(
    "select_related, reading author.name",
    lambda: [b.author.name for b in Book.objects.select_related("author")],
)
queries(
    "only('title'), then reading author.name",
    lambda: [b.author.name for b in Book.objects.only("title")],
)
queries(
    "only('title'), then reading title",
    lambda: [b.title for b in Book.objects.only("title")],
)

# 3. Counting related rows one object at a time.
queries(
    "count in the loop",
    lambda: [a.books.count() for a in Author.objects.all()],
)
queries(
    "annotate the count into the first query",
    lambda: [a.book_count for a in Author.objects.annotate(book_count=Count("books"))],
)

# 4. The optimisation applied to a queryset that is then thrown away.
def rebuilt():
    Book.objects.select_related("author")          # discarded
    return [b.author.name for b in Book.objects.all()]


queries("select_related on a queryset nobody kept", rebuilt)

counts = [a.book_count for a in Author.objects.annotate(book_count=Count("books")).order_by("name")]
print("annotated counts:", counts)
print("they agree with the loop:", counts == [a.books.count() for a in Author.objects.order_by("name")])
