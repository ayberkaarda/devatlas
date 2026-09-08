## Why this exists

Input arrives as strings, from someone you do not control, and every part of the
application downstream would like to assume it is already correct. A form is the
one place that assumption is earned. Without it the checks scatter: a length
test in the view, a type conversion in the template, a rule about two fields
that lives in whichever branch somebody remembered. Each copy drifts, and the
one that is missing is the one an attacker finds. Django's forms exist so that
refusal happens once, in a named place, with the reasons attached to the fields
they belong to.

## The idea

A form is a customs desk. Everything arriving is declared, inspected item by
item, and either admitted in a known form or turned back with a reason written
against the item that failed. Nothing crosses in its original packaging: what
comes out the other side has been converted, not merely approved.

### Where the analogy breaks

A customs desk stands across the whole border. A form stands across one door,
and the wall around it has other doors. Listing 3 defines a `ModelForm` that
rejects an invalid email and more than four seats, then calls
`Booking.objects.create(email="not-an-email", seats=9999)` — which succeeds. The
form was never consulted, because nothing requires it to be. `full_clean()` on
the model catches the email, because that is a field-level rule the model owns;
it knows nothing of the form's `clean_seats`.

The desk also stops inspecting an item once it has rejected it. A field that
failed is not in `cleaned_data` at all — listing 2 shows `clean()` receiving
only `['note']` when the other two were rejected — so cross-field rules must use
`.get()` and cope with the value being absent.

## How it works

Cleaning runs in three layers, in order. The field converts and applies its own
constraints; `clean_<fieldname>()` adds rules about that one field; `clean()`
sees whatever survived and can compare fields.

```python
class BookingForm(forms.Form):
    email = forms.EmailField()
    seats = forms.IntegerField(min_value=1, max_value=4)

    def clean_email(self):
        return self.cleaned_data["email"].strip().lower()

    def clean(self):
        data = super().clean()
        if data.get("seats", 0) > 2 and not data.get("note"):
            raise ValidationError("bookings over two seats need a note")
        return data
```

`is_valid()` is what runs all of it. Listing 1 shows `cleaned_data` not existing
before that call and existing after, and shows the conversion: `form.data["seats"]`
is the string `'2'`, `form.cleaned_data["seats"]` is the integer `2`.

A `clean_<fieldname>()` method only runs if the field itself validated. Listing
2 sends `"not a number"` to an `IntegerField`, gets error code `invalid`, and
confirms `clean_seats` never saw a value. Errors raised in `clean()` land under
`__all__` and are read with `non_field_errors()`.

A `ModelForm` builds its fields from the model, so `blank=True` becomes
`required=False`, and `save()` writes a row:

```python
class BookingForm(forms.ModelForm):
    class Meta:
        model = Booking
        fields = ["email", "seats", "note"]
```

Calling `save()` on an invalid form raises `ValueError` rather than writing
something half-checked — listing 3 measures the row count before and after and
it does not move.

## Common mistakes

**Reading `form.data` instead of `form.cleaned_data`.** `data` holds the raw
strings. Listing 1 prints both types side by side.

**Touching `cleaned_data` before `is_valid()`.** The attribute does not exist
yet; listing 1 shows `False` then `True`.

**Assuming every key is in `cleaned_data` inside `clean()`.** Rejected fields
were removed. Use `.get()`, or the `KeyError` becomes a 500 on bad input — which
is exactly the input the form was written for.

**Believing the form protects the model.** It does not. Listing 3 writes a row
that no form would have accepted, in one line, through the manager.

**Returning nothing from `clean_<fieldname>()`.** The return value *is* the
cleaned value, so a method with no `return` sets the field to `None`.

## Check yourself

<details><summary>Where does a rule about two fields go?</summary>

In `clean()`. It is the only layer that sees more than one field. Listing 1
raises there and reads it back through `non_field_errors()`.

</details>

<details><summary>An <code>IntegerField</code> gets "abc". Does <code>clean_seats</code> run?</summary>

No. The field failed first, so the value never reached it and `seats` is absent
from `cleaned_data`. Listing 2 measures both facts.

</details>

<details><summary>A <code>ModelForm</code> rejects a value. Can that value still reach the table?</summary>

Yes — through `Model.objects.create()`, which consults no form. Listing 3 does
it. If the value must never exist, the rule belongs in a database constraint as
well as in the form.

</details>

## Listings

1. `forms-and-validation-1.py` — three cleaning layers, and raw against cleaned.
2. `forms-and-validation-2.py` — what a failed form contains and what it dropped.
3. `forms-and-validation-3.py` — a `ModelForm`, and the door beside it.
