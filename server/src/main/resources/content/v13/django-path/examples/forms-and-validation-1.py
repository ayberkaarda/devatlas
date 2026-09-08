"""Three layers of cleaning, and what comes out the other side.

Run with: python forms-and-validation-1.py
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

    def clean_email(self):
        # Field-level: runs after the field converted the value.
        value = self.cleaned_data["email"].strip().lower()
        if value.endswith("@example.invalid"):
            raise ValidationError("that domain is not accepted")
        return value

    def clean(self):
        # Form-level: the only place that can see two fields at once.
        data = super().clean()
        if data.get("seats", 0) > 2 and not data.get("note"):
            raise ValidationError("bookings over two seats need a note")
        return data


form = BookingForm({"email": "  ADA@Example.COM ", "seats": "2", "note": ""})
print("is_valid:", form.is_valid())
print("raw value and type:", repr(form.data["seats"]), type(form.data["seats"]).__name__)
print("cleaned value and type:", repr(form.cleaned_data["seats"]), type(form.cleaned_data["seats"]).__name__)
print("clean_email normalised:", form.cleaned_data["email"])
print("optional field became:", repr(form.cleaned_data["note"]))

rejected = BookingForm({"email": "someone@example.invalid", "seats": "2"})
print("is_valid:", rejected.is_valid())
print("clean_email error:", rejected.errors["email"].as_data()[0].messages)

cross_field = BookingForm({"email": "ada@example.com", "seats": "3", "note": ""})
print("is_valid:", cross_field.is_valid())
print("non-field error:", cross_field.non_field_errors()[0])
print("error keys:", sorted(cross_field.errors))

# is_valid() is what runs the cleaning. Touching cleaned_data first is an error.
untouched = BookingForm({"email": "ada@example.com", "seats": "1"})
print("cleaned_data exists before is_valid():", hasattr(untouched, "cleaned_data"))
untouched.is_valid()
print("cleaned_data exists after is_valid():", hasattr(untouched, "cleaned_data"))
