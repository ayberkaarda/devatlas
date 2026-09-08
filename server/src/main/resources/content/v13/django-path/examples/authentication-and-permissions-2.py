"""What the user is allowed to do, and the cache that hides a change.

Run with: python authentication-and-permissions-2.py
"""

import sys

import django
from django.conf import settings

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
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AnonymousUser, Group, Permission
from django.core.management import call_command
from django.db import models


class Invoice(models.Model):
    total = models.IntegerField()

    class Meta:
        app_label = "billing"
        db_table = "invoice"
        permissions = [("approve_invoice", "Can approve invoice")]


apps.get_app_config("billing").models_module = sys.modules["billing"]
call_command("migrate", run_syncdb=True, verbosity=0)

from django.contrib.auth.management import create_permissions

create_permissions(apps.get_app_config("billing"), verbosity=0)

User = get_user_model()

codenames = sorted(
    Permission.objects.filter(content_type__app_label="billing").values_list("codename", flat=True)
)
print("permissions Django created for one model:", codenames)

approve = Permission.objects.get(content_type__app_label="billing", codename="approve_invoice")
print("a permission is a row:", f"{approve.content_type.app_label}.{approve.codename}")

user = User.objects.create_user(username="ada", password="a-long-passphrase")
print("before granting:", user.has_perm("billing.approve_invoice"))

user.user_permissions.add(approve)
print("the row exists now:", user.user_permissions.filter(pk=approve.pk).exists())
print("the same instance still says:", user.has_perm("billing.approve_invoice"))
print("a freshly loaded instance says:", User.objects.get(pk=user.pk).has_perm("billing.approve_invoice"))
print("the same codename without its app label:", User.objects.get(pk=user.pk).has_perm("approve_invoice"))

# Groups are the same answer reached differently.
group = Group.objects.create(name="approvers")
group.permissions.add(approve)
bob = User.objects.create_user(username="bob", password="another-passphrase")
bob.groups.add(group)
print("through a group:", User.objects.get(pk=bob.pk).has_perm("billing.approve_invoice"))
print("bob holds no permission of his own:", not bob.user_permissions.exists())

# The two answers that ignore the rows entirely.
root = User.objects.create_superuser(username="root", password="third-passphrase")
print("superuser holds a permission nobody defined:", root.has_perm("billing.no_such_permission"))
print("anonymous holds nothing:", AnonymousUser().has_perm("billing.approve_invoice"))

inactive = User.objects.get(pk=bob.pk)
inactive.is_active = False
print("inactive user answers:", inactive.has_perm("billing.approve_invoice"))
print("while the group membership is untouched:", inactive.groups.count())
