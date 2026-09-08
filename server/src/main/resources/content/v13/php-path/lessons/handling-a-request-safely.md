## Why this exists

Everything that arrives in an HTTP request is a string. `$_GET`, `$_POST` and `$_COOKIE` hold
strings and arrays of strings, and PHP 8.2 attaches no type, no length and no meaning to any of
them. Three separate jobs follow from that, and conflating them is where most PHP security advice
goes wrong: deciding whether a value is acceptable at all, transforming it for the place it is
about to be written, and keeping it out of the syntax of a query.

The three are not interchangeable, and no single function does all of them. A value that is safe
in HTML text is not safe in a URL; a value validated as an integer still cannot be concatenated
into SQL as a column name; and escaping a value twice produces visibly wrong output rather than an
error, which is why it survives review.

## The idea

Think of a diplomatic pouch. Validation is the check at the sending end: does this belong in the
bag at all? Escaping is the translation done at the receiving end, once, into the language actually
spoken there. Parameter binding is refusing to put the message in the envelope at all, and handing
it over separately so it can never be read as an instruction.

### Where the analogy breaks

Translation is not a property of the message, it is a property of the destination, and the same
string needs four different transformations for HTML text, an HTML attribute, a URL path segment
and a JSON document. `htmlspecialchars()` applied to a path produces a path that does not exist;
`rawurlencode()` applied to HTML text produces visible percent signs. Neither raises anything.

The pouch also suggests that escaping *protects* the message. In an unquoted HTML attribute it
protects nothing at all, because there is no delimiter for the escaping to defend:

```
<a title=&quot; onmouseover=&quot;steal()>x</a>
```

Every dangerous character was escaped, and the attribute is still broken open by the space. And the
last difference is the important one: a bound parameter is not escaped. It never becomes part of
the statement, which is why a placeholder can hold a value and never a column name or a keyword.

## How it works

`filter_var()` validates and returns the typed value, or `false`. Options carry the range, and
`FILTER_NULL_ON_FAILURE` distinguishes "absent" from "present and not a boolean".

```php
$page = filter_var($_GET['page'] ?? null, FILTER_VALIDATE_INT, [
    'options' => ['default' => 1, 'min_range' => 1],
]);
$sort = in_array($_GET['sort'] ?? '', ['name', 'created', 'price'], true) ? $_GET['sort'] : 'name';
```

The second line is the pattern for anything that becomes syntax: an allow-list, not an escape.
`FILTER_VALIDATE_URL` is syntactic only, so `ftp://example.com` passes it and a scheme check is
what actually decides.

For output, PHP 8.1 changed the default flags of `htmlspecialchars()` to
`ENT_QUOTES | ENT_SUBSTITUTE | ENT_HTML401`, so in PHP 8.2 single quotes are escaped without being
asked and invalid UTF-8 is substituted rather than emptying the whole string. Readers who
remember having to pass `ENT_QUOTES` are remembering PHP 8.0 and earlier — and the habit is still
worth keeping, because a call that also names the encoding does not depend on the default.

For SQL, a prepared statement sends the statement and the values apart:

```
concatenated: 3 rows for a table of 3
prepared: 0 rows
prepared with a real name: 1 rows
```

The first line is `WHERE name = 'ana' OR '1'='1'` after concatenation: no error, no warning, the
whole table. The second is the same input bound as a value. Setting `ATTR_EMULATE_PREPARES` to
`false` makes the driver send it that way rather than building the string itself, and
`ERRMODE_EXCEPTION` turns a failed call into a `PDOException` instead of a `false` that the next
line will use as an object.

## Common mistakes

**Escaping on input.** A value transformed on the way in is stored transformed, and every
destination gets the wrong transformation. Validate on input; escape at the point of output.

**Escaping twice.** `htmlspecialchars(htmlspecialchars($name))` renders `&amp;amp;`. Nothing fails.

**Treating a placeholder as a general substitution.** `ORDER BY :column` binds a string literal, not
an identifier. Column names, table names, `ASC`/`DESC` and `IN` lists all come from an allow-list
or from generated placeholders.

**Leaving `ENT_SUBSTITUTE` off with untrusted bytes.** Without it, malformed UTF-8 makes
`htmlspecialchars()` return the empty string, and a field silently disappears from the page:

```
input:13 dropped:0 substituted:15 valid-utf8:false
```

**Comparing a password with `===`.** `password_hash()` embeds a random salt, so two hashes of the
same password differ. `password_verify()` is the comparison.

## Check yourself

<details><summary>Why is a value validated as an integer still unsafe as a column name?</summary>

Because validation says what the value *is*, not where it may go. A column name is syntax, and
syntax comes from an allow-list the code owns — never from the request, whatever its type.

</details>

<details><summary><code>filter_var($url, FILTER_VALIDATE_URL)</code> passed. Is the URL safe to put in an <code>href</code>?</summary>

Not yet. The check is syntactic and admits schemes such as `ftp:`. Compare `parse_url()`'s scheme
against `['http', 'https']` before using it.

</details>

<details><summary>What does <code>ATTR_EMULATE_PREPARES => false</code> change?</summary>

The driver sends the statement and the values separately to the server instead of interpolating
them itself. With emulation on, the separation is done in PHP and depends on the driver quoting
correctly.

</details>

## Listings

1. `handling-a-request-safely-1.php` — validating query input with `filter_var()`, allow-lists,
   and why a password hash is verified rather than compared.
2. `handling-a-request-safely-2.php` — the four output contexts, double escaping, and what invalid
   UTF-8 does to `htmlspecialchars()`.
3. `handling-a-request-safely-3.php` — PDO against SQLite: a concatenated query that returns the
   whole table, the same input bound, and a transaction that rolls back.
