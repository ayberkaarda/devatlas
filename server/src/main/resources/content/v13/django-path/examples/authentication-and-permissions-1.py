"""Who the user is: credentials, hashing, and the anonymous case.

Run with: python authentication-and-permissions-1.py
"""

import sys

import django
from django.conf import settings

# One file cannot be a package, so the module registers itself under an app
# name and the models below name that label explicitly.
sys.modules["billing"] = sys.modules["__main__"]

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    INSTALLED_APPS=["django.contrib.contenttypes", "django.contrib.auth", "billing"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    # A deliberately weak hasher, so the listing runs in a moment. Never in an
    # application: the default hasher is slow on purpose.
    PASSWORD_HASHERS=["django.contrib.auth.hashers.MD5PasswordHasher"],
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.apps import apps
from django.contrib.auth import authenticate, get_user_model
from django.contrib.auth.hashers import identify_hasher
from django.contrib.auth.models import AnonymousUser
from django.core.management import call_command
from django.db import models


class Invoice(models.Model):
    total = models.IntegerField()

    class Meta:
        app_label = "billing"
        db_table = "invoice"


apps.get_app_config("billing").models_module = sys.modules["billing"]
call_command("migrate", run_syncdb=True, verbosity=0)

User = get_user_model()
user = User.objects.create_user(username="ada", password="a-long-passphrase")

print("user model:", User.__name__)
print("password stored in the clear:", user.password == "a-long-passphrase")
print("stored value names its hasher:", identify_hasher(user.password).algorithm)
print("check_password with the right one:", user.check_password("a-long-passphrase"))
print("check_password with the wrong one:", user.check_password("a-long-passphras"))

print("authenticate returns a user:", authenticate(username="ada", password="a-long-passphrase") is not None)
print("authenticate with a bad password returns:", authenticate(username="ada", password="nope"))
print("authenticate with an unknown user returns:", authenticate(username="nobody", password="nope"))

anonymous = AnonymousUser()
print("anonymous is_authenticated:", anonymous.is_authenticated)
print("user is_authenticated:", user.is_authenticated)
print("anonymous is not None, so a truth test is not an auth test:", anonymous is not None)

user.set_password("a-different-passphrase")
print("set_password changed the instance:", user.check_password("a-different-passphrase"))
print("but nothing was written yet:", User.objects.get(pk=user.pk).check_password("a-long-passphrase"))
user.save()
print("after save():", User.objects.get(pk=user.pk).check_password("a-different-passphrase"))

user.is_active = False
user.save()
print("authenticate refuses an inactive user:", authenticate(username="ada", password="a-different-passphrase") is None)
print("the row is still there:", User.objects.filter(username="ada").exists())
