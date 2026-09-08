"""Constraints live in the schema, so nothing gets past them.

Run with: python a-model-is-the-schema-3.py
"""

import django
from django.conf import settings

settings.configure(
    INSTALLED_APPS=["__main__"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.db import IntegrityError, connection, models, transaction


class Ticket(models.Model):
    code = models.CharField(max_length=12, unique=True)
    seats = models.IntegerField()

    class Meta:
        app_label = "__main__"
        db_table = "ticket"
        constraints = [
            models.CheckConstraint(
                condition=models.Q(seats__gt=0), name="ticket_seats_positive"
            )
        ]


with connection.schema_editor() as editor:
    editor.create_model(Ticket)

Ticket.objects.create(code="A1", seats=2)


def attempt(label, action):
    try:
        with transaction.atomic():
            action()
    except IntegrityError as error:
        print(f"{label}: refused with {type(error).__name__}")
    else:
        print(f"{label}: accepted")


attempt("duplicate code through the ORM", lambda: Ticket.objects.create(code="A1", seats=1))
attempt("zero seats through the ORM", lambda: Ticket.objects.create(code="B2", seats=0))


def raw_insert():
    with connection.cursor() as cursor:
        cursor.execute("INSERT INTO ticket (code, seats) VALUES ('C3', -5)")


attempt("negative seats through raw SQL", raw_insert)
attempt("a legitimate row", lambda: Ticket.objects.create(code="D4", seats=3))

with connection.cursor() as cursor:
    constraints = connection.introspection.get_constraints(cursor, "ticket")
print("named check constraint present:", "ticket_seats_positive" in constraints)
print("rows:", Ticket.objects.count())
print("codes:", sorted(Ticket.objects.values_list("code", flat=True)))
