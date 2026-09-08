"""The same migration, an empty table and a table with rows.

Two small apps are written into a temporary directory: 'risky' adds a NOT NULL
column in one step, 'careful' adds it in three.

Run with: python migrations-that-are-safe-to-run-twice-3.py
"""

import os
import sys
import tempfile
import textwrap

import django
from django.conf import settings

root = tempfile.mkdtemp(prefix="ledger_rows_")


def write(*parts, body=""):
    path = os.path.join(root, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(textwrap.dedent(body).lstrip())


INITIAL = """
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
                ],
            )
        ]
    """

for app in ("risky", "careful"):
    write(app, "__init__.py")
    write(app, "migrations", "__init__.py")
    write(app, "migrations", "0001_initial.py", body=INITIAL)

write(
    "risky",
    "models.py",
    body="""
    from django.db import models


    class Account(models.Model):
        name = models.CharField(max_length=50)
        currency = models.CharField(max_length=3)
    """,
)
write(
    "risky",
    "migrations",
    "0002_currency.py",
    body="""
    from django.db import migrations, models


    class Migration(migrations.Migration):
        dependencies = [("risky", "0001_initial")]
        operations = [
            migrations.AddField(
                model_name="account",
                name="currency",
                field=models.CharField(max_length=3),
            )
        ]
    """,
)

write(
    "careful",
    "models.py",
    body="""
    from django.db import models


    class Account(models.Model):
        name = models.CharField(max_length=50)
        currency = models.CharField(max_length=3)
    """,
)
write(
    "careful",
    "migrations",
    "0002_currency_nullable.py",
    body="""
    from django.db import migrations, models


    class Migration(migrations.Migration):
        dependencies = [("careful", "0001_initial")]
        operations = [
            migrations.AddField(
                model_name="account",
                name="currency",
                field=models.CharField(max_length=3, null=True),
            )
        ]
    """,
)
write(
    "careful",
    "migrations",
    "0003_backfill_currency.py",
    body="""
    from django.db import migrations


    def backfill(apps, schema_editor):
        Account = apps.get_model("careful", "Account")
        Account.objects.filter(currency__isnull=True).update(currency="EUR")


    class Migration(migrations.Migration):
        dependencies = [("careful", "0002_currency_nullable")]
        operations = [migrations.RunPython(backfill, migrations.RunPython.noop)]
    """,
)
write(
    "careful",
    "migrations",
    "0004_currency_required.py",
    body="""
    from django.db import migrations, models


    class Migration(migrations.Migration):
        dependencies = [("careful", "0003_backfill_currency")]
        operations = [
            migrations.AlterField(
                model_name="account",
                name="currency",
                field=models.CharField(max_length=3),
            )
        ]
    """,
)

sys.path.insert(0, root)
settings.configure(
    INSTALLED_APPS=["risky", "careful"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.core.management import call_command
from django.db import IntegrityError, connection

import careful.models
import risky.models


def try_migrate(label, app):
    try:
        call_command("migrate", app, verbosity=0)
    except IntegrityError as error:
        print(f"{label}: refused with {type(error).__name__}")
    else:
        print(f"{label}: applied")


# An empty table takes the one-step migration without complaint.
call_command("migrate", "risky", "0001", verbosity=0)
print("rows in risky:", risky.models.Account.objects.count())
try_migrate("one step, empty table", "risky")

# The same migration, against rows that have no value for the new column.
# Rolling back removed the column, so these rows go in through raw SQL: the
# model class still declares a field the table no longer has.
call_command("migrate", "risky", "0001", verbosity=0)
with connection.cursor() as cursor:
    cursor.execute("INSERT INTO risky_account (name) VALUES ('operations'), ('payroll')")
    cursor.execute("SELECT COUNT(*) FROM risky_account")
    print("rows in risky:", cursor.fetchone()[0])
try_migrate("one step, table with rows", "risky")

# Three steps: nullable column, backfill, then tighten.
call_command("migrate", "careful", "0001", verbosity=0)
with connection.cursor() as cursor:
    cursor.execute("INSERT INTO careful_account (name) VALUES ('operations'), ('payroll')")
    cursor.execute("SELECT COUNT(*) FROM careful_account")
    print("rows in careful:", cursor.fetchone()[0])
try_migrate("three steps, table with rows", "careful")

print("values written into the existing rows:", sorted(careful.models.Account.objects.values_list("currency", flat=True)))
with connection.cursor() as cursor:
    description = connection.introspection.get_table_description(cursor, "careful_account")
print("currency column allows null:", {c.name: c.null_ok for c in description}["currency"])
print("rows survived:", careful.models.Account.objects.count())
