"""What the database enforces, and what only validation enforces.

Run with: python a-model-is-the-schema-2.py
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

from django.core.exceptions import ValidationError
from django.db import IntegrityError, connection, models, transaction


class Speaker(models.Model):
    name = models.CharField(max_length=10)
    bio = models.TextField(blank=True)
    website = models.URLField(null=True)

    class Meta:
        app_label = "__main__"
        db_table = "speaker"


with connection.schema_editor() as editor:
    editor.create_model(Speaker)

# save() does not validate. It builds an INSERT and sends it.
too_long = Speaker(name="x" * 40)
too_long.save()
print("save() stored a name of length:", len(Speaker.objects.get(pk=too_long.pk).name))
print("the model said max_length was:", Speaker._meta.get_field("name").max_length)

# full_clean() is where max_length, choices and validators are checked.
try:
    Speaker(name="x" * 40, website="https://example.org/").full_clean()
except ValidationError as error:
    print("full_clean() rejected fields:", sorted(error.message_dict))

# blank=False is a validation rule, not a database rule: null=True means the
# column accepts NULL, and blank=False still makes a form or full_clean() ask
# for a value.
try:
    Speaker(name="ada").full_clean()
except ValidationError as error:
    print("null=True but blank=False still required:", sorted(error.message_dict))
print("the same instance saves without complaint:", Speaker.objects.create(name="ada").pk is not None)

# NOT NULL is a database rule and it does refuse.
try:
    with transaction.atomic():
        Speaker.objects.create(name="ok", bio=None)
except IntegrityError as error:
    print("bio=None refused by the database:", type(error).__name__)

print("rows now:", Speaker.objects.count())
