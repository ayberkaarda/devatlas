## Why this exists

A Django project has no separate schema file. The class you write in `models.py`
is the description of the table, the description of the Python object you work
with, and the input to the tool that changes the database. One source of truth,
three jobs. That is what saves a project from the usual drift: a column nobody's
code knows about, or an attribute no column backs.

It only works if you can tell which of the three jobs a given line is doing.
`max_length` on a `CharField` becomes part of the column type *and* a validation
rule. `blank` is only ever a validation rule. `default` is applied by Python
when an object is built, and separately used to fill existing rows when a
migration adds the column. Confuse them and you get data you did not expect,
with the complaint arriving somewhere far from the cause.

## The idea

A model class is a mould, and the table is the casting. You do not carve the
casting. You change the mould and let the migration system pour again. Every
column exists because a field put it there, and the column's shape is the
field's shape.

### Where the analogy breaks

A casting can be melted down and poured again. A table holding rows cannot.
Once data exists, changing the mould becomes a negotiation with what is already
in the table, which is the subject of lesson 6 in this track.

The mould also does not shape everything. Django 6.1 splits enforcement in two:
some declarations become constraints the database applies to every write,
whatever writes it, and some are checked only when something calls
`full_clean()`. `save()` does not call it. A mould that held its shape only when
asked would be a bad mould; a model that behaves this way is Django keeping two
different guarantees apart on purpose.

## How it works

Each field becomes a column, and Django names both unless you say otherwise.
The table name defaults to the app label and the model name joined by an
underscore, and `Meta.db_table` overrides it.

```python
class Speaker(models.Model):
    name = models.CharField(max_length=10)
    website = models.URLField(null=True)
    talks = models.IntegerField(default=0)
```

`null` and `blank` answer different questions and are not a pair. `null=True`
says the column accepts `NULL`. `blank=True` says validation will accept an
empty value. Listing 1 prints both for every field; `website` above is
`null=True, blank=False`, so the database will store nothing there quite
happily while `full_clean()` insists on a value.

Constraints are where the model reaches all the way down. A `unique=True` field
or a `CheckConstraint` in `Meta.constraints` becomes something the database
itself refuses to violate, so it holds even against a hand-written `INSERT`.

```python
class Meta:
    constraints = [
        models.CheckConstraint(condition=models.Q(seats__gt=0),
                               name="ticket_seats_positive")
    ]
```

In Django 6.1 the argument is named `condition`. Listing 3 inserts a negative
value through a raw cursor, bypassing the ORM entirely, and the database still
refuses it.

Validators are the other half, and they are opt-in:

```python
speaker = Speaker(name="x" * 40)
speaker.save()          # stored: SQLite does not police varchar length
speaker.full_clean()    # ValidationError on 'name'
```

## Common mistakes

**Expecting `save()` to validate.** It does not. Listing 2 saves a forty
character value into a `max_length=10` field and reads it back at length forty.
Forms call `full_clean()` for you; a management command or a shell session does
not.

**Reading `null=True` as "optional".** It makes the column nullable. Listing 2
shows `full_clean()` raising `ValidationError` for a field that is `null=True`
and `blank=False` — the database would have taken it.

**Writing a rule only in Python.** A uniqueness check done with `filter().exists()`
before saving loses to a concurrent request. A `UniqueConstraint` raises
`IntegrityError` instead, and it cannot be raced.

**Editing a column by hand.** The mould still says what it said, so the next
`makemigrations` proposes a change nobody wanted.

## Check yourself

<details><summary>Where does <code>max_length</code> actually apply?</summary>

In two places: it shapes the column the migration creates, and it is checked by
validation. Whether the column itself rejects longer data is up to the backend —
listing 2 stores an over-long value on SQLite and `full_clean()` is what catches
it.

</details>

<details><summary>Why does a raw <code>INSERT</code> still hit a <code>CheckConstraint</code>?</summary>

Because the constraint lives in the schema, not in the Python layer. Listing 3
writes through a cursor with no model involved and the database refuses the row.

</details>

<details><summary>You add <code>blank=True</code> to a field. Do you need a migration?</summary>

Yes — Django tracks the field's full state and records the change. The resulting
operation alters nothing about the column on most backends, but the migration
exists so that the recorded state and the model never disagree.

</details>

## Listings

1. `a-model-is-the-schema-1.py` — fields to columns, and the database asked
   whether it agrees.
2. `a-model-is-the-schema-2.py` — what `save()` skips and `full_clean()` catches.
3. `a-model-is-the-schema-3.py` — constraints, tested from both sides.
