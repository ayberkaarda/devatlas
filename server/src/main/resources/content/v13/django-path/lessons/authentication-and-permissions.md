## Why this exists

Two questions get muddled into one word, "auth", and they have different
answers. *Who is this?* is authentication, and it is settled once per request by
a password check or a session. *What may they do?* is authorisation, and it is
asked again at every point where it matters. Code that conflates them produces
the most common security defect in a web application: a page that is hidden from
the menu, reachable by anyone who types the URL. Django keeps the two apart, and
the separation only helps if you know which one you are asking.

## The idea

Authentication is the photograph in a passport; authorisation is the set of
stamps in it. The photograph is checked once, at the desk. The stamps are
checked wherever somebody needs to know what you may do, and a valid passport
with no stamp for this country gets you no further than the border.

### Where the analogy breaks

A stamp in a passport is the whole answer. Django's permission check is not a
lookup — it is a question put to every configured authentication backend, and
some of them answer without consulting a single row. Listing 2 shows a
superuser holding `billing.no_such_permission`, a permission that does not
exist and was never granted; and the same listing shows an inactive user
answering `False` while their group membership sits in the table, untouched.

The stamps are also cached in the passport. Listing 2 grants a permission,
confirms the row exists, and finds `user.has_perm(...)` still returning `False`
on the instance in hand. The permission set was resolved when that object was
loaded. A freshly fetched user answers `True`.

## How it works

`authenticate()` asks each backend in turn and returns a user or `None`. It
never raises, and a wrong password and an unknown user are indistinguishable to
the caller — deliberately, since telling them apart tells an attacker which
usernames exist.

```python
authenticate(username="ada", password="a-long-passphrase")  # a User
authenticate(username="ada", password="nope")               # None
```

Passwords are stored hashed. Listing 1 confirms the stored value is not the
password and reads back the hasher that produced it, and shows `set_password()`
changing the instance while the database still holds the old value until
`save()` runs. It also confirms that Django 6.1's `ModelBackend` refuses an
inactive user even with the right password.

For "who is this" in a request, `AnonymousUser` is a real object with
`is_authenticated` set to `False`. Testing the user for truthiness is not an
authentication test — listing 1 makes that explicit.

Permissions are rows. Django creates four per model, and `Meta.permissions`
adds more:

```
['add_invoice', 'approve_invoice', 'change_invoice',
 'delete_invoice', 'view_invoice']
```

A user reaches one directly or through a group, and `has_perm` takes the label
`"<app_label>.<codename>"`. Enforcement belongs in the view:

```python
@permission_required("billing.approve_invoice", raise_exception=True)
def approve(request):
    ...
```

Listing 3 exercises all of it through the test client: anonymous gets `302` to
`LOGIN_URL`, a signed-in user without the permission gets `403`, and a user with
it gets `200`. The template's `{% if perms.billing.approve_invoice %}` hides the
link for the first two — and hiding is not refusing, which is why the last check
in listing 3 removes the permission and asks the view directly.

## Common mistakes

**Checking a permission on a user object you have been holding.** The set was
cached at load. Re-fetch the user, or use the request's user, which is loaded
per request.

**Protecting the link instead of the view.** `{% if perms... %}` changes what is
drawn. It does not change what happens when the URL is typed.

**Testing `if request.user:` for signed-in.** `AnonymousUser` is truthy. Use
`request.user.is_authenticated`.

**Forgetting the app label.** Listing 2 grants the permission and then asks
`has_perm("approve_invoice")`, without the label, and gets `False`. The argument
is `"billing.approve_invoice"`.

**Assuming deactivating a user revokes permissions.** Listing 2 shows the group
row still there. Reactivate the account and the access returns.

## Check yourself

<details><summary>You add a permission and the check still says no. Why?</summary>

The user object in hand cached its permissions when it was loaded. Listing 2
shows the same instance answering `False` and a freshly loaded one answering
`True`.

</details>

<details><summary>What does <code>authenticate()</code> return for a wrong password?</summary>

`None` — the same as for a username that does not exist. Listing 1 prints both.
The symmetry is deliberate.

</details>

<details><summary>The link is hidden. Is the page protected?</summary>

No. Listing 3 hides it with `perms` in the template and separately refuses it
with `permission_required`; only the second returns 403 to someone who types the
URL.

</details>

## Listings

1. `authentication-and-permissions-1.py` — credentials, hashing and the
   anonymous case.
2. `authentication-and-permissions-2.py` — permissions, groups, and the cache
   that hides a change.
3. `authentication-and-permissions-3.py` — a view that refuses and a template
   that only hides.
