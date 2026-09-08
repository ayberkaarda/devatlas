## Why this exists

Spring Data JPA 4.1 will build a query from a method name, from a JPQL string, or
from raw SQL, and all three are one line of code. The three are not interchangeable.
Each buys something and gives something up, and the give-up is invisible at the call
site: a derived name that binds to the wrong property, a native query that a paging
call cannot count, an `UPDATE` that runs but leaves the persistence context holding
the old row. "Defensible" here means you can say what the query does, when it is
checked, and what it costs — before someone asks in a review.

## The idea

Three ways to ask a librarian for a book. You can ask in plain words at the desk —
"everything by Le Guin, newest first" — and be understood, because the catalogue is
standardised and your request is ordinary. You can fill in the library's own request
slip, in its vocabulary of authors, titles and shelf marks, when the plain words start
running long. Or you can go into the stacks yourself.

### Where the analogy breaks

A librarian who is unsure asks what you meant. The name parser cannot. It takes the
longest property match first, splitting the remainder from the right, and if the first
match leads somewhere with no continuation it fails — but if it leads somewhere
plausible, it silently answers a different question than the one you asked.

The second break is in your favour. A librarian only discovers the book does not exist
when they go looking. A derived method name is resolved against the entity when the
application starts, so an unresolvable one is a boot failure with a
`PropertyReferenceException` naming the traversed path, not a 500 next Tuesday.

The third is not. Walking into the stacks yourself is harmless in a library. Native SQL
runs past the persistence context, so rows it changes are still held there as they
were, and the next read through the repository can hand back the old values.

## How it works

Reach for the name first. It is short, it is checked at startup, and it is a query
description rather than a query.

```java
Optional<Lesson> findBySlugAndDeletedAtIsNull(String slug);
List<Lesson> findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID moduleId);
```

When the name stops being readable, write JPQL. It names entities and fields, not
tables and columns, so it follows a rename the entity followed — and it can select
less than a whole row, which matters when the row is large:

```java
@Query("SELECT l.id FROM Lesson l WHERE l.id IN :ids AND l.deletedAt IS NULL")
List<UUID> findLiveIds(@Param("ids") Collection<UUID> ids);
```

A write needs `@Modifying`, and usually `clearAutomatically`: without the annotation
the statement is executed as a query and does nothing useful, and without the clear
the context keeps serving copies of rows the statement just changed.

Native SQL is for what the query language cannot express — here, PostgreSQL 16's
own full-text operators. Spring Data can rewrite simple queries for sorting and
paging, but a complex one needs either JSqlParser on the classpath or a `countQuery`
you write yourself.

```java
@NativeQuery(
    value = "SELECT * FROM lessons WHERE to_tsvector('english', body_markdown) "
          + "@@ plainto_tsquery('english', :terms)",
    countQuery = "SELECT count(*) FROM lessons WHERE to_tsvector('english', body_markdown) "
          + "@@ plainto_tsquery('english', :terms)")
Page<Lesson> search(@Param("terms") String terms, Pageable pageable);
```

Parameters are always bound, never concatenated. Spring Data JPA 4 supports parameter
name discovery from the `-parameters` compiler flag, so `@Param` can be omitted when
the build sets it; naming them anyway costs one annotation and survives a build change.

## Common mistakes

**A name that binds to the wrong property.** Add an `addressZip` field to a class that
already has `address.zipCode` and `findByAddressZipCode` stops resolving — the parser
matched `addressZip` first and then looked for `code` on a `String`. The startup
message says exactly that, including the traversed path; the fix is
`findByAddress_ZipCode`.

**`@Query` without `@Modifying` on an update.** The statement is treated as a query.
Nothing is written and nothing complains.

**A paged native query with no count query.** The page's total cannot be derived, and
the call fails rather than guessing.

## Check yourself

<details><summary>When is a derived query method name checked, and against what?</summary>
At application startup, against the entity's properties. An unresolvable path fails the boot with a <code>PropertyReferenceException</code> naming the path it traversed.
</details>

<details><summary>Why does a bulk <code>@Modifying</code> update usually need <code>clearAutomatically</code>?</summary>
The statement changes rows directly. Copies already held in the persistence context are not updated, so a later read can return the values from before the update.
</details>

<details><summary>What does JPQL give you that native SQL does not?</summary>
It is written against entities and fields, so it is portable and follows the mapping; and it is parsed by the provider, so a reference that no longer exists is caught rather than sent to the database.
</details>

## Listings

1. What Spring Data parses out of four real method names.
2. The resolution algorithm choosing the wrong property, and the underscore fix.
3. Derived, JPQL and native side by side in one repository.
