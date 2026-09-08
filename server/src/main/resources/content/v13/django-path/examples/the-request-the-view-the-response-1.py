"""The whole path, in one file: URLconf, view, response.

Run with: python the-request-the-view-the-response-1.py
"""

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

from django.http import Http404, HttpResponse, JsonResponse
from django.test import Client
from django.urls import path


def article_detail(request, article_id):
    if article_id > 100:
        raise Http404("no such article")
    return JsonResponse({"id": article_id, "method": request.method})


def echo(request):
    body = f"{request.method} q={request.GET.get('q', '-')}"
    response = HttpResponse(body, content_type="text/plain")
    response["X-Handled-By"] = "echo"
    return response


urlpatterns = [
    path("articles/<int:article_id>/", article_detail, name="article-detail"),
    path("echo/", echo, name="echo"),
]

client = Client()

response = client.get("/articles/7/")
print("status:", response.status_code)
print("content type:", response.headers["Content-Type"])
print("body:", response.content.decode())

response = client.get("/echo/", {"q": "hello"})
print("status:", response.status_code)
print("body:", response.content.decode())
print("header the view set:", response.headers["X-Handled-By"])

print("view raised Http404:", client.get("/articles/500/").status_code)
print("no pattern matched:", client.get("/nowhere/").status_code)
print("method the view saw:", client.post("/echo/").content.decode())
