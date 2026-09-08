"""A test suite in one file: the runner, the client, and isolation.

Run with: python testing-a-django-application-1.py
"""

import contextlib
import io
import sys
import unittest

import django
from django.conf import settings

sys.modules["catalogue"] = sys.modules["__main__"]

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    ROOT_URLCONF="__main__",
    ALLOWED_HOSTS=["testserver"],
    INSTALLED_APPS=["catalogue"],
    MIDDLEWARE=[],
    DATABASES={"default": {"ENGINE": "django.db.backends.sqlite3", "NAME": ":memory:"}},
    USE_TZ=True,
    DEFAULT_AUTO_FIELD="django.db.models.BigAutoField",
)
django.setup()

from django.apps import apps
from django.db import models
from django.http import JsonResponse
from django.test import SimpleTestCase, TestCase
from django.test.runner import DiscoverRunner
from django.urls import path, reverse


class Product(models.Model):
    name = models.CharField(max_length=50)
    price = models.IntegerField()

    class Meta:
        app_label = "catalogue"
        db_table = "product"


apps.get_app_config("catalogue").models_module = sys.modules["catalogue"]


def product_list(request):
    names = list(Product.objects.order_by("name").values_list("name", flat=True))
    return JsonResponse({"names": names})


urlpatterns = [path("products/", product_list, name="product-list")]


class IsolationTest(TestCase):
    """Methods run in name order, so 'a' writes and 'b' checks what survived."""

    def test_a_writes_a_row(self):
        Product.objects.create(name="lamp", price=20)
        self.assertEqual(Product.objects.count(), 1)

    def test_b_starts_empty_again(self):
        self.assertEqual(Product.objects.count(), 0)


class ClientTest(TestCase):
    @classmethod
    def setUpTestData(cls):
        # Runs once for the class, inside a transaction that is rolled back
        # after the last test in it.
        Product.objects.create(name="desk", price=100)
        Product.objects.create(name="chair", price=50)

    def test_the_view_answers(self):
        response = self.client.get(reverse("product-list"))
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["names"], ["chair", "desk"])

    def test_the_view_runs_one_query(self):
        with self.assertNumQueries(1):
            self.client.get(reverse("product-list"))

    def test_a_write_here_does_not_reach_the_next_test(self):
        Product.objects.create(name="stool", price=10)
        self.assertEqual(Product.objects.count(), 3)

    def test_the_class_fixture_is_back_to_two(self):
        self.assertEqual(Product.objects.count(), 2)


class NoDatabaseTest(SimpleTestCase):
    def test_reversing_a_url_needs_no_rows(self):
        self.assertEqual(reverse("product-list"), "/products/")


if __name__ == "__main__":
    runner = DiscoverRunner(verbosity=0, interactive=False)
    runner.setup_test_environment()
    old_config = runner.setup_databases()
    suite = runner.build_suite(["__main__"])


    def flatten(item):
        if isinstance(item, unittest.TestSuite):
            for child in item:
                yield from flatten(child)
        else:
            yield item.id()


    collected = sorted(flatten(suite))
    with contextlib.redirect_stderr(io.StringIO()):
        result = runner.run_suite(suite)
    runner.teardown_databases(old_config)
    runner.teardown_test_environment()

    print("tests collected:")
    for test_id in collected:
        print("  ", test_id)
    print("tests run:", result.testsRun)
    print("failures:", len(result.failures))
    print("errors:", len(result.errors))
    print("everything passed:", result.wasSuccessful())
