"""A ModelForm, and the door that stays open behind it.

Run with: python forms-and-validation-3.py
"""

import django
from django.conf import settings

settings.configure(
    INSTALLED_APPS=["__main__"],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_I18N=False,
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django import forms
from django.core.exceptions import ValidationError
from django.db import connection, models


class Booking(models.Model):
    email = models.EmailField()
    seats = models.IntegerField()
    note = models.CharField(max_length=20, blank=True)

    class Meta:
        app_label = "__main__"
        db_table = "booking"


with connection.schema_editor() as editor:
    editor.create_model(Booking)


class BookingForm(forms.ModelForm):
    class Meta:
        model = Booking
        fields = ["email", "seats", "note"]

    def clean_seats(self):
        seats = self.cleaned_data["seats"]
        if seats > 4:
            raise ValidationError("at most four seats")
        return seats


print("fields the form built from the model:", list(BookingForm().fields))
print("email field class:", type(BookingForm().fields["email"]).__name__)
print("note is optional because blank=True:", BookingForm().fields["note"].required)

good = BookingForm({"email": "ada@example.com", "seats": "2", "note": "window"})
print("is_valid:", good.is_valid())
booking = good.save()
print("saved with a primary key:", booking.pk is not None)
print("rows:", Booking.objects.count())

bad = BookingForm({"email": "not-an-email", "seats": "9"})
print("is_valid:", bad.is_valid())
print("fields with errors:", sorted(bad.errors))
try:
    bad.save()
except ValueError as error:
    print("save() on an invalid form raises:", type(error).__name__)
print("rows unchanged:", Booking.objects.count())

# The form is a door, not a wall. Nothing about the model requires it.
Booking.objects.create(email="not-an-email", seats=9999)
print("rows after going round the form:", Booking.objects.count())
print("the row that no form would have accepted:", Booking.objects.get(seats=9999).email)

# full_clean() applies the model's own field validation, but not the form's rules.
loose = Booking(email="also-not-an-email", seats=9999)
caught = {}
try:
    loose.full_clean()
except ValidationError as error:
    caught = error.message_dict
print("model full_clean() caught:", sorted(caught))
print("model full_clean() knows nothing of clean_seats:", "seats" not in caught)
