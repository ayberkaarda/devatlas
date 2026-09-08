"""Autoescaping, and the view that decided instead of the template.

Run with: python templates-render-they-do-not-decide-3.py
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

from django.http import HttpResponse
from django.template import Context, Template
from django.template.loader import render_to_string  # noqa: F401  (documents the usual path)
from django.test import Client
from django.urls import path
from django.utils.safestring import mark_safe

hostile = "<script>alert(1)</script>"
context = Context({"comment": hostile, "trusted": mark_safe("<em>ok</em>")})

print("escaped by default:", Template("{{ comment }}").render(context))
print("the safe filter turns it off:", Template("{{ comment|safe }}").render(context))
print("mark_safe marks the value, not the template:", Template("{{ trusted }}").render(context))
print(
    "autoescape block:",
    Template("{% autoescape off %}{{ comment }}{% endautoescape %}").render(context),
)

# The decision belongs in the view. The template renders whatever it is handed.
TEMPLATE = Template(
    "{% if lines %}"
    "{% for line in lines %}<li>{{ line.label }}: {{ line.amount }}</li>{% endfor %}"
    "{% else %}<p>{{ message }}</p>{% endif %}"
)


def summary(request):
    raw = [("desk", 100), ("chair", -50), ("lamp", 20)]
    lines = [{"label": label, "amount": amount} for label, amount in raw if amount > 0]
    payload = {"lines": lines, "message": "nothing to show"}
    return HttpResponse(TEMPLATE.render(Context(payload)))


urlpatterns = [path("summary/", summary, name="summary")]

response = Client().get("/summary/")
print("status:", response.status_code)
print("body:", response.content.decode())
print("the negative line never reached the template:", "chair" not in response.content.decode())
