"""Resolution and reversal: the URLconf is read in both directions.

Run with: python the-request-the-view-the-response-2.py
"""

import re

import django
from django.conf import settings

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    ROOT_URLCONF="__main__",
    INSTALLED_APPS=[],
    MIDDLEWARE=[],
    DATABASES={},
    USE_TZ=True,
)
django.setup()

from django.http import HttpResponse
from django.urls import NoReverseMatch, Resolver404, path, resolve, reverse


def article_detail(request, article_id):
    return HttpResponse(str(article_id))


def article_slug(request, slug):
    return HttpResponse(slug)


urlpatterns = [
    path("articles/<int:article_id>/", article_detail, name="article-detail"),
    path("articles/<slug:slug>/", article_slug, name="article-slug"),
]

match = resolve("/articles/42/")
print("view chosen:", match.func.__name__)
print("captured:", match.kwargs)
print("converter produced a", type(match.kwargs["article_id"]).__name__)
print("route:", match.route)
print("url name:", match.url_name)

match = resolve("/articles/how-django-routes/")
print("view chosen:", match.func.__name__)
print("captured:", match.kwargs)
print("converter produced a", type(match.kwargs["slug"]).__name__)

print("reverse by name:", reverse("article-detail", args=[42]))
print("reverse the other:", reverse("article-slug", args=["how-django-routes"]))

try:
    resolve("/articles/")
except Resolver404:
    print("nothing matched: Resolver404")

try:
    reverse("article-detail", args=["not-an-int"])
except NoReverseMatch:
    print("argument does not fit the converter: NoReverseMatch")

# Order decides: the int pattern is listed first, so a numeric segment never
# reaches the slug view even though 'slug' would also match it.
print("42 matches slug pattern too:", bool(re.fullmatch(r"[-a-zA-Z0-9_]+", "42")))
print("but resolve() picked:", resolve("/articles/42/").func.__name__)
