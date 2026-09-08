"""Enforcing it: a view that refuses, and a template that only hides.

Run with: python authentication-and-permissions-3.py
"""

import sys

import django
from django.conf import settings

sys.modules["billing"] = sys.modules["__main__"]

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    ROOT_URLCONF="__main__",
    ALLOWED_HOSTS=["testserver"],
    INSTALLED_APPS=[
        "django.contrib.contenttypes",
        "django.contrib.auth",
        "django.contrib.sessions",
        "billing",
    ],
    MIDDLEWARE=[
        "django.contrib.sessions.middleware.SessionMiddleware",
        "django.contrib.auth.middleware.AuthenticationMiddleware",
    ],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    # A deliberately weak hasher, so the listing runs in a moment. Never in an
    # application: the default hasher is slow on purpose.
    PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"],
    LOGIN_URL="/login/",
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
    TEMPLATES=[
        {
            "BACKEND": "django.template.backends.django.DjangoTemplates",
            "DIRS": [],
            "APP_DIRS": False,
            "OPTIONS": {
                "context_processors": [
                    "django.contrib.auth.context_processors.auth"
                ]
            },
        }
    ],
)
django.setup()

from django.apps import apps
from django.contrib.auth import get_user_model
from django.contrib.auth.decorators import login_required, permission_required
from django.contrib.auth.management import create_permissions
from django.core.management import call_command
from django.db import models
from django.http import HttpResponse
from django.template import RequestContext, Template
from django.test import Client
from django.urls import path


class Invoice(models.Model):
    total = models.IntegerField()

    class Meta:
        app_label = "billing"
        db_table = "invoice"
        permissions = [("approve_invoice", "Can approve invoice")]


apps.get_app_config("billing").models_module = sys.modules["billing"]


@login_required
def dashboard(request):
    return HttpResponse("dashboard")


@permission_required("billing.approve_invoice", raise_exception=True)
def approve(request):
    return HttpResponse("approved")


MENU = Template(
    "{% if perms.billing.approve_invoice %}<a href='/approve/'>approve</a>{% endif %}"
)


def menu(request):
    # RequestContext runs the configured context processors, one of which puts
    # `perms` and `user` into the context.
    return HttpResponse(MENU.render(RequestContext(request)))


urlpatterns = [
    path("dashboard/", dashboard),
    path("approve/", approve),
    path("menu/", menu),
    path("login/", lambda request: HttpResponse("login page")),
]

call_command("migrate", run_syncdb=True, verbosity=0)
create_permissions(apps.get_app_config("billing"), verbosity=0)

from django.contrib.auth.models import Permission

User = get_user_model()
plain = User.objects.create_user(username="ada", password="a-long-passphrase")
approver = User.objects.create_user(username="bob", password="another-passphrase")
approver.user_permissions.add(
    Permission.objects.get(content_type__app_label="billing", codename="approve_invoice")
)

client = Client()
print("anonymous on a login_required view:", client.get("/dashboard/").status_code)
print("it redirects to LOGIN_URL:", client.get("/dashboard/").headers["Location"].startswith("/login/"))

client.force_login(plain)
print("signed in on the login_required view:", client.get("/dashboard/").status_code)
print("signed in without the permission:", client.get("/approve/").status_code)
print("the menu the template drew:", repr(client.get("/menu/").content.decode()))

client.force_login(User.objects.get(pk=approver.pk))
print("signed in with the permission:", client.get("/approve/").status_code)
print("the menu the template drew:", repr(client.get("/menu/").content.decode()))

# Hiding the link is not enforcement. Removing the permission and asking the
# view directly is what proves the view refuses.
approver.user_permissions.clear()
client.force_login(User.objects.get(pk=approver.pk))
print("after the permission was taken away:", client.get("/approve/").status_code)
