"""The database a test gets, and the one it is not allowed to touch.

Run with: python testing-a-django-application-2.py
"""

import contextlib
import io
import sys

import django
from django.conf import settings

sys.modules["catalogue"] = sys.modules["__main__"]

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    ALLOWED_HOSTS=["testserver"],
    INSTALLED_APPS=["catalogue"],
    MIDDLEWARE=[],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.apps import apps
from django.db import connection, models
from django.test import SimpleTestCase, TestCase, TransactionTestCase
from django.test.runner import DiscoverRunner
from django.test.testcases import DatabaseOperationForbidden


class Product(models.Model):
    name = models.CharField(max_length=50)

    class Meta:
        app_label = "catalogue"
        db_table = "product"


apps.get_app_config("catalogue").models_module = sys.modules["catalogue"]

configured_name = settings.DATABASES["default"]["NAME"]
observed = {}


class NamesTest(TestCase):
    def test_the_connection_points_somewhere_else(self):
        observed["in_test"] = connection.settings_dict["NAME"]


class ForbiddenTest(SimpleTestCase):
    def test_simple_test_case_refuses_the_database(self):
        try:
            Product.objects.count()
        except DatabaseOperationForbidden:
            observed["forbidden"] = True
        else:
            observed["forbidden"] = False


class RollbackTest(TestCase):
    def test_a_write(self):
        Product.objects.create(name="written by TestCase")
        observed["testcase_rows"] = Product.objects.count()


class TruncateTest(TransactionTestCase):
    def test_a_write(self):
        Product.objects.create(name="written by TransactionTestCase")
        observed["transaction_rows"] = Product.objects.count()


class AfterwardsTest(TransactionTestCase):
    def test_z_nothing_survived(self):
        observed["rows_left"] = Product.objects.count()


if __name__ == "__main__":
    runner = DiscoverRunner(verbosity=0, interactive=False)
    runner.setup_test_environment()
    old_config = runner.setup_databases()
    with contextlib.redirect_stderr(io.StringIO()):
        result = runner.run_suite(runner.build_suite(["__main__"]))
    runner.teardown_databases(old_config)
    runner.teardown_test_environment()

    print("tests run:", result.testsRun, "failures:", len(result.failures), "errors:", len(result.errors))
    print("configured database name:", configured_name)
    print("the tests ran against a different name:", observed["in_test"] != configured_name)
    print("SimpleTestCase refused a query:", observed["forbidden"])
    print("TestCase saw its own row:", observed["testcase_rows"])
    print("TransactionTestCase saw its own row:", observed["transaction_rows"])
    print("a later test found:", observed["rows_left"], "rows")
    print("no test left a row behind for another:", observed["rows_left"] == 0)
