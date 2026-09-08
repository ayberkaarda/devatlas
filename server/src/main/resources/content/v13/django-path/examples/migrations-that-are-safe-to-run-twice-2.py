"""A data migration that is not safe to apply twice, and one that is.

Run with: python migrations-that-are-safe-to-run-twice-2.py
"""

import os
import sys
import tempfile
import textwrap

import django
from django.conf import settings

root = tempfile.mkdtemp(prefix="ledger_data_")
package = os.path.join(root, "ledger")
os.makedirs(os.path.join(package, "migrations"))


def write(*parts, body=""):
    with open(os.path.join(*parts), "w", encoding="utf-8", newline="\n") as handle:
        handle.write(textwrap.dedent(body).lstrip())


write(package, "__init__.py")
write(package, "migrations", "__init__.py")
write(
    package,
    "models.py",
    body="""
    from django.db import models


    class Account(models.Model):
        name = models.CharField(max_length=50)
        balance = models.IntegerField(default=0)
        tier = models.CharField(max_length=10, default="")
    """,
)
write(
    package,
    "migrations",
    "0001_initial.py",
    body="""
    from django.db import migrations, models


    class Migration(migrations.Migration):
        initial = True
        dependencies = []
        operations = [
            migrations.CreateModel(
                name="Account",
                fields=[
                    ("id", models.BigAutoField(primary_key=True, serialize=False)),
                    ("name", models.CharField(max_length=50)),
                    ("balance", models.IntegerField(default=0)),
                    ("tier", models.CharField(default="", max_length=10)),
                ],
            )
        ]
    """,
)
# Relative: adds 100 every time it runs.
write(
    package,
    "migrations",
    "0002_relative_bonus.py",
    body="""
    from django.db import migrations
    from django.db.models import F


    def grant(apps, schema_editor):
        Account = apps.get_model("ledger", "Account")
        Account.objects.update(balance=F("balance") + 100)


    class Migration(migrations.Migration):
        dependencies = [("ledger", "0001_initial")]
        operations = [migrations.RunPython(grant, migrations.RunPython.noop)]
    """,
)
# Absolute and guarded: running it again reaches the same state.
write(
    package,
    "migrations",
    "0003_backfill_tier.py",
    body="""
    from django.db import migrations


    def backfill(apps, schema_editor):
        Account = apps.get_model("ledger", "Account")
        Account.objects.filter(tier="").update(tier="standard")


    class Migration(migrations.Migration):
        dependencies = [("ledger", "0002_relative_bonus")]
        operations = [migrations.RunPython(backfill, migrations.RunPython.noop)]
    """,
)

sys.path.insert(0, root)
settings.configure(
    INSTALLED_APPS=["ledger"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.core.management import call_command

from ledger.models import Account


def state(label):
    account = Account.objects.get(name="operations")
    print(f"{label}: balance={account.balance} tier={account.tier!r}")


call_command("migrate", "ledger", "0001", verbosity=0)
Account.objects.create(name="operations", balance=100)
state("before the data migrations")

call_command("migrate", "ledger", verbosity=0)
state("after applying 0002 and 0003")

call_command("migrate", "ledger", verbosity=0)
state("after running migrate again")

# Rolling back and re-applying is the case the ledger does not protect you from.
call_command("migrate", "ledger", "0001", verbosity=0)
state("after rolling back to 0001")
call_command("migrate", "ledger", verbosity=0)
state("after re-applying 0002 and 0003")

print("the relative update ran twice, so balance moved:", Account.objects.get(name="operations").balance != 200)
print("the guarded backfill reached the same state:", Account.objects.get(name="operations").tier == "standard")
