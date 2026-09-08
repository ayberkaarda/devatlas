"""The ledger: why a second migrate does nothing at all.

This listing writes a small app with two real migrations into a temporary
directory, so there is nothing to set up first.

Run with: python migrations-that-are-safe-to-run-twice-1.py
"""

import os
import sys
import tempfile
import textwrap

import django
from django.conf import settings

root = tempfile.mkdtemp(prefix="ledger_app_")
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
        currency = models.CharField(max_length=3, default="EUR")
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
                ],
            )
        ]
    """,
)
write(
    package,
    "migrations",
    "0002_currency.py",
    body="""
    from django.db import migrations, models


    class Migration(migrations.Migration):
        dependencies = [("ledger", "0001_initial")]
        operations = [
            migrations.AddField(
                model_name="account",
                name="currency",
                field=models.CharField(default="EUR", max_length=3),
            )
        ]
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
from django.db import connection
from django.db.migrations.executor import MigrationExecutor
from django.db.migrations.recorder import MigrationRecorder

from ledger.models import Account

recorder = MigrationRecorder(connection)


def plan_length():
    executor = MigrationExecutor(connection)
    executor.loader.build_graph()
    return len(executor.migration_plan(executor.loader.graph.leaf_nodes("ledger")))


print("migrations on disk:", 2)
print("plan before the first migrate:", plan_length())
call_command("migrate", "ledger", verbosity=0)
print("recorded as applied:", recorder.migration_qs.filter(app="ledger").count())
print("names recorded:", sorted(recorder.migration_qs.filter(app="ledger").values_list("name", flat=True)))

Account.objects.create(name="operations", balance=100)
print("row created, currency:", Account.objects.get(name="operations").currency)

print("plan before the second migrate:", plan_length())
call_command("migrate", "ledger", verbosity=0)
print("recorded after the second migrate:", recorder.migration_qs.filter(app="ledger").count())
print("rows after the second migrate:", Account.objects.count())
print("balance unchanged:", Account.objects.get(name="operations").balance)

# The ledger is a table like any other. Forget a row and the migration runs again.
recorder.migration_qs.filter(app="ledger", name="0002_currency").delete()
print("plan after deleting one ledger row:", plan_length())
