"""One model, and the table it describes.

Django needs settings before anything can be imported from django.db, so this
listing configures them in place and uses an in-memory SQLite database. Run it
with: python a-model-is-the-schema-1.py
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

from django.db import connection, models


class Speaker(models.Model):
    name = models.CharField(max_length=10)
    bio = models.TextField(blank=True)
    website = models.URLField(null=True)
    talks = models.IntegerField(default=0)

    class Meta:
        app_label = "__main__"
        db_table = "speaker"


# schema_editor does what a migration's CreateModel operation does.
with connection.schema_editor() as editor:
    editor.create_model(Speaker)

print("table:", Speaker._meta.db_table)
for field in Speaker._meta.get_fields():
    print(
        f"  {field.name}: type={type(field).__name__}"
        f" column={field.column} null={field.null} blank={field.blank}"
    )

with connection.cursor() as cursor:
    described = connection.introspection.get_table_description(cursor, "speaker")
print("columns the database reports:", [column.name for column in described])
print(
    "database agrees with the model:",
    [column.name for column in described] == [field.column for field in Speaker._meta.get_fields()],
)

speaker = Speaker.objects.create(name="ada")
print("default applied without mentioning it:", speaker.talks)
print("primary key was assigned:", speaker.pk is not None)
print("primary key field:", type(Speaker._meta.pk).__name__, "named", Speaker._meta.pk.name)
print("website may hold NULL:", Speaker.objects.get(pk=speaker.pk).website is None)
