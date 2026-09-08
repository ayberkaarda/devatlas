"""Middleware wraps the view, in both directions.

Run with: python the-request-the-view-the-response-3.py
"""

import django
from django.conf import settings

settings.configure(
    DEBUG=False,
    SECRET_KEY="only-used-by-this-listing",
    ROOT_URLCONF="__main__",
    INSTALLED_APPS=[],
    MIDDLEWARE=["__main__.TraceMiddleware", "__main__.GateMiddleware"],
    DATABASES={},
    USE_TZ=True,
)
django.setup()

from django.http import HttpResponse
from django.test import Client, RequestFactory
from django.urls import path

trace = []


class TraceMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        trace.append("trace: before")
        request.trace_stamp = "set by TraceMiddleware"
        response = self.get_response(request)
        trace.append("trace: after")
        response["X-Trace"] = "yes"
        return response


class GateMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        trace.append("gate: before")
        if request.headers.get("X-Blocked") == "yes":
            trace.append("gate: short circuit")
            return HttpResponse("blocked", status=403)
        response = self.get_response(request)
        trace.append("gate: after")
        return response


def home(request):
    trace.append("view")
    return HttpResponse(getattr(request, "trace_stamp", "nothing"))


urlpatterns = [path("", home, name="home")]

client = Client()

trace.clear()
response = client.get("/")
print("status:", response.status_code)
print("body:", response.content.decode())
print("order:", trace)

trace.clear()
response = client.get("/", headers={"x-blocked": "yes"})
print("status:", response.status_code)
print("order:", trace)
print("view ran:", "view" in trace)
print("outer middleware still edited the response:", "X-Trace" in response.headers)

# A view called directly is just a function. Nothing wrapped it.
request = RequestFactory().get("/")
direct = home(request)
print("direct call status:", direct.status_code)
print("direct call body:", direct.content.decode())
print("direct call has the header:", "X-Trace" in direct.headers)
