## Why this exists

Rows have to point at each other, and something has to decide what a row points *with*.
Pick a value that means something to a person — an address, an employee number — and
sooner or later someone changes it, or two people turn out to share it, or it gets
reissued. Every row that pointed at it must then be found and rewritten, and the ones you
miss are silently wrong. This lesson is about which value to point with, and what
PostgreSQL 16 does to keep those pointers honest.

## The idea

A primary key is a seat number and a natural key is a passenger's name. The seat number
was invented by the airline, means nothing outside the aircraft, and is the reason the
catering list, the seat-belt check and the lost-property tag can all refer to the same
person without ever agreeing on how her name is spelled. Nobody minds that seat 14C is
arbitrary. That is the property being bought.

### Where the analogy breaks

The seat number is reused on the next flight and a primary key is not: it identifies one
row for as long as that row exists, and a deleted row does not hand its key on.

The place the picture misleads most is what a foreign key does. A seat number written on
a luggage tag is inert paper; nothing stops you writing 99Z on it. A foreign key is
checked by the database on the way in and on the way out: PostgreSQL 16 refuses a child
row that names a parent that does not exist, and refuses to delete a parent while a child
still names it, unless the constraint was declared to say otherwise.

## How it works

`PRIMARY KEY` is `UNIQUE` plus `NOT NULL`, and it creates the index that enforces it.
`REFERENCES` makes the second promise, and the referential action is a decision you make
once per relationship. The default is `NO ACTION`: the delete is refused.

```sql
CONSTRAINT fk_module_track  FOREIGN KEY (track_id)  REFERENCES track (id),
CONSTRAINT fk_lesson_module FOREIGN KEY (module_id) REFERENCES module (id) ON DELETE CASCADE
```

`CASCADE` says the child has no meaning without its parent; the default says the opposite,
and it is the safer default precisely because it is loud. The first listing shows both.

A natural key fails in a specific, mechanical way. Making an address the primary key means
every child row stores that address, so when the address changes the parent cannot be
updated at all — `23503`, "still referenced from table". The fix is either
`ON UPDATE CASCADE`, which rewrites every child row and every index entry mentioning it, or
a surrogate key, where the same change touches one row and no child at all.

```sql
UPDATE account_natural   SET email = 'ada@new.example' WHERE email = 'ada@old.example';
-- ERROR: update or delete on table "account_natural" violates foreign key constraint

UPDATE account_surrogate SET email = 'ada@new.example' WHERE id = 1;   -- one row
```

That is the pattern the schema this application runs on uses: `users` has a `uuid`
primary key supplied by the service and a separate `uq_users_email` unique constraint.
The address is still unique and still what people log in with; it is simply not the thing
rows point at.

Uniqueness has one more edge worth knowing before the next lesson. A `UNIQUE` constraint
in PostgreSQL 16 treats two absent values as different, so a nullable unique column
accepts any number of rows with nothing in it. Since PostgreSQL 15 you can ask for the
other reading with `NULLS NOT DISTINCT`. And uniqueness can be scoped: a partial unique
index enforces the rule over the rows that satisfy a predicate and ignores the rest.

```sql
CREATE UNIQUE INDEX ux_lesson_slug ON lesson (slug) WHERE deleted_at IS NULL;
```

That index is taken from this application's own schema, and its reason is worth keeping.
Under a plain unique index every soft-deleted row would hold its slug forever, so an
editor could never recreate a lesson at the same address, and the conflict they would be
shown names a row no read endpoint can find.

## Common mistakes

**Assuming a natural key never changes.** Addresses change and registration numbers get
reissued, and the identifier promised to be permanent turns out to have duplicates.

**Reaching for `ON DELETE CASCADE` to make an error go away.** The error was the database
telling you rows depend on this one. Cascading is a decision that the child is worthless
alone, not a way to silence a `23503`.

**Forgetting that a foreign key column is not indexed for you.** PostgreSQL 16 indexes the
referenced side, because that is what enforces uniqueness. The referencing side gets
nothing, and every parent delete then scans the child table.

**Believing a `UNIQUE` column cannot repeat.** It can, for every row that leaves it empty.

## Check yourself

<details><summary>Why did the address change fail against the natural key but not against the surrogate?</summary>

Because the child rows stored the address itself. Changing the parent would leave two
payments pointing at a value that no longer exists, so PostgreSQL refuses it. With a
surrogate key the children store an identifier that did not change, so nothing about them
had to move.

</details>

<details><summary>A nullable column has a <code>UNIQUE</code> constraint. How many rows can leave it empty?</summary>

As many as you like. Two unknowns are not equal to each other, so they do not collide.
`UNIQUE NULLS NOT DISTINCT`, available from PostgreSQL 15, asks for the other behaviour.

</details>

<details><summary>Why scope a unique index with <code>WHERE deleted_at IS NULL</code>?</summary>

So that uniqueness is a rule about live rows. Otherwise a deleted row keeps its slug for
ever and the address can never be reused, which shows up as a conflict against a row the
application can no longer display.

</details>

## Listings

1. `keys-1.sql` — an orphan refused, a parent held, and a cascade that fires.
2. `keys-2.sql` — the address change that a natural key cannot survive.
3. `keys-3.sql` — unique with unknowns, `NULLS NOT DISTINCT`, and a partial unique index.
