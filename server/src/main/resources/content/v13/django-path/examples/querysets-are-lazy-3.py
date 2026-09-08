"""Where laziness costs you: the same rows fetched more than once.

Run with: python querysets-are-lazy-3.py
"""

import django
from django.conf import settings

settings.configure(
    DEBUG=False,
    INSTALLED_APPS=["__main__"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
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

from django.db import connection, models
from django.db.models.signals import post_init
from django.template import Context, Template
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


def rebuilt_each_time():
    """The mistake: a function that returns a new queryset, called three times."""
    def recent():
        return Article.objects.filter(words__gt=100)

    return len(recent()), list(recent())[:1], recent().count()


def built_once():
    recent = list(Article.objects.filter(words__gt=100))
    return len(recent), recent[:1], len(recent)


queries("queryset rebuilt for each use", rebuilt_each_time)
queries("evaluated once into a list", built_once)

# The same trap through a template: two loops over one queryset object cost one
# query, over two queryset objects cost two.
template = Template("{% for a in items %}x{% endfor %}{% for a in items %}y{% endfor %}")
one_object = Article.objects.filter(words__gt=150)
queries(
    "template loops twice over one queryset",
    lambda: template.render(Context({"items": one_object})),
)

# Asking whether anything matched.
# Asking whether anything matched. All three cost one query, but they do not
# cost the same: post_init fires once per model instance Django builds.
built = []


def count_instance(**kwargs):
    built.append(1)


# weak=False: a signal keeps only a weak reference to its receiver by default.
post_init.connect(count_instance, sender=Article, weak=False)


def instances(label, action):
    built.clear()
    with CaptureQueriesContext(connection) as captured:
        answer = action()
    print(f"{label}: queries={len(captured)} instances={len(built)} answer={answer}")


instances("len(qs) > 0", lambda: len(Article.objects.all()) > 0)
instances("qs.count() > 0", lambda: Article.objects.all().count() > 0)
instances("qs.exists()", lambda: Article.objects.all().exists())
