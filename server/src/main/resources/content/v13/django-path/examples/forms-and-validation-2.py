"""What a failed form does and does not contain.

Run with: python forms-and-validation-2.py
"""

import django
from django.conf import settings

settings.configure(
    INSTALLED_APPS=[],
    DATABASES={},
    USE_I18N=False,
    USE_TZ=True,
)
django.setup()

from django import forms
from django.core.exceptions import ValidationError


class BookingForm(forms.Form):
    email = forms.EmailField()
    seats = forms.IntegerField(min_value=1, max_value=4)
    note = forms.CharField(max_length=20, required=False)

    def clean_seats(self):
        seats = self.cleaned_data["seats"]
        if seats == 3:
            raise ValidationError("three is not bookable")
        return seats

    def clean(self):
        data = super().clean()
        print("  inside clean(), cleaned_data holds:", sorted(data))
        return data


form = BookingForm({"email": "not-an-email", "seats": "99", "note": "fine"})
print("is_valid:", form.is_valid())
print("fields with errors:", sorted(form.errors))
print("cleaned_data after failure:", sorted(form.cleaned_data))
print("the failing fields were dropped:", "email" not in form.cleaned_data and "seats" not in form.cleaned_data)
print("the passing field survived:", form.cleaned_data["note"])

# clean_<field> only runs if the field itself validated.
skipped = BookingForm({"email": "ada@example.com", "seats": "not a number"})
print("is_valid:", skipped.is_valid())
print("error code on seats:", skipped.errors["seats"].as_data()[0].code)
print("clean_seats never saw a value:", "seats" not in skipped.cleaned_data)

reached = BookingForm({"email": "ada@example.com", "seats": "3"})
print("is_valid:", reached.is_valid())
print("error from clean_seats:", reached.errors["seats"].as_data()[0].messages)

# A bound form with no data at all is not the same as an unbound one.
unbound = BookingForm()
print("unbound is_bound:", unbound.is_bound, "is_valid:", unbound.is_valid())
print("unbound errors:", sorted(unbound.errors))
empty = BookingForm({})
print("bound to nothing is_bound:", empty.is_bound, "is_valid:", empty.is_valid())
print("bound to nothing errors:", sorted(empty.errors))
