"""A test that fails for the right reason: pinning the query count.

Run with: python testing-a-django-application-3.py
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
from django.test import TestCase
from django.test.runner import DiscoverRunner
from django.urls import path


class Category(models.Model):
    name = models.CharField(max_length=50)

    class Meta:
        app_label = "catalogue"
        db_table = "category"


class Product(models.Model):
    name = models.CharField(max_length=50)
    category = models.ForeignKey(Category, on_delete=models.CASCADE, related_name="products")

    class Meta:
        app_label = "catalogue"
        db_table = "product"
        ordering = ["name"]


apps.get_app_config("catalogue").models_module = sys.modules["catalogue"]


def optimised(request):
    rows = [
        {"product": p.name, "category": p.category.name}
        for p in Product.objects.select_related("category")
    ]
    return JsonResponse({"rows": rows})


def regressed(request):
    # The same page after somebody removed select_related.
    rows = [
        {"product": p.name, "category": p.category.name} for p in Product.objects.all()
    ]
    return JsonResponse({"rows": rows})


urlpatterns = [path("optimised/", optimised), path("regressed/", regressed)]


class QueryCountTest(TestCase):
    @classmethod
    def setUpTestData(cls):
        for n in range(5):
            category = Category.objects.create(name=f"category{n}")
            Product.objects.create(name=f"product{n}", category=category)

    def test_the_optimised_page_answers_correctly(self):
        response = self.client.get("/optimised/")
        self.assertEqual(len(response.json()["rows"]), 5)

    def test_the_regressed_page_answers_correctly(self):
        response = self.client.get("/regressed/")
        self.assertEqual(len(response.json()["rows"]), 5)

    def test_the_optimised_page_runs_one_query(self):
        with self.assertNumQueries(1):
            self.client.get("/optimised/")

    def test_the_regressed_page_would_fail_the_same_assertion(self):
        with self.assertRaises(AssertionError):
            with self.assertNumQueries(1):
                self.client.get("/regressed/")

    def test_the_regressed_page_runs_one_query_per_row(self):
        with self.assertNumQueries(6):
            self.client.get("/regressed/")


if __name__ == "__main__":
    runner = DiscoverRunner(verbosity=0, interactive=False)
    runner.setup_test_environment()
    old_config = runner.setup_databases()
    with contextlib.redirect_stderr(io.StringIO()):
        result = runner.run_suite(runner.build_suite(["__main__"]))
    runner.teardown_databases(old_config)
    runner.teardown_test_environment()

    print("tests run:", result.testsRun)
    print("failures:", len(result.failures), "errors:", len(result.errors))
    print("both pages produce the same answer, and only one is cheap:", result.wasSuccessful())
