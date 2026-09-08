## Why this exists

The two sides look alike, and that is the trap. A table has columns; a class has
fields; the mapping is one line per column and it reads as a rename. But a row is
identified by a primary key and lives in a database; an object is identified by its
address in a heap and lives until nobody points at it. Everything that goes wrong
here goes wrong at the seam between those two ideas — identity, nullability, the
meaning of a stored value, when a change is written. Spring Boot 4.1 uses Jakarta
Persistence 3.2 with Hibernate as its provider, and none of that machinery removes
the seam. It only makes it easy to forget where it is.

## The idea

The persistence context is a lending desk. You ask for a book by its shelf mark; the
desk fetches it, notes that you have it, and if you ask again for the same shelf mark
during the same visit you get the very same copy back, not a second one. Anything you
pencil in is written back to the master record when you leave.

### Where the analogy breaks

The desk lends you the actual book. The persistence context lends you a *copy*, and
the write-back happens at commit rather than at the moment you change a field — so a
field you set and then decide against still reaches the database unless you undo it.

The "same copy within one visit" guarantee is per visit. Two transactions that load
the same row hand you two objects; `==` between them is false and always will be,
which is why entity equality has to be defined rather than inherited.

And the desk only knows about books borrowed through it. A bulk `UPDATE` issued as a
query changes rows the desk is still holding stale copies of, and it will happily
serve you the stale one afterwards.

## How it works

The schema is owned by migrations. The entity is checked against it at startup and
never allowed to change it:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

`validate` fails startup when a mapped field disagrees with the actual column, which
turns a whole class of drift into a boot failure instead of a runtime surprise. In
Spring Boot 4.1, schema generation is off by default for a non-embedded database
anyway; `validate` makes the intent explicit and adds the check.

Then say what each column is, rather than letting a naming strategy decide:

```java
@Column(name = "body_markdown", nullable = false, columnDefinition = "text")
private String bodyMarkdown;

@Enumerated(EnumType.STRING)
@Column(name = "difficulty", nullable = false, length = 16)
private Difficulty difficulty;

@Column(name = "estimated_minutes")
private Integer estimatedMinutes;

@Version
@Column(name = "version", nullable = false)
private long version;
```

Four decisions worth defending. `text` because a lesson body is not bounded by the
default `varchar` length. `EnumType.STRING` because `ORDINAL` stores `Enum.ordinal()`,
a position — insert a constant at the front of the enum and every stored number now
means something else, with no error anywhere. `Integer` rather than `int` because the
column is nullable and a primitive turns "not recorded" into zero. `@Version` because
two transactions that read a row and both write it should not silently overwrite one
another; the second one fails instead.

Identity is the remaining decision. `Object`'s contract requires that an object's hash
code not change while it is a key in a hash-based collection. An entity whose
`hashCode` reads a generated identifier violates that the moment the identifier is
assigned: the object is in the set, and the set can no longer find it.

## Common mistakes

**`equals`/`hashCode` on a generated id.** The object is added to a `HashSet` before
the insert, gets its key, and `contains` returns `false` while `size()` still says one.
No exception is thrown. Key on a value the object has from birth instead.

**`EnumType.ORDINAL`.** Silent, permanent data corruption on the next enum edit.
`@Enumerated` defaults to `ORDINAL`, so omitting the annotation entirely is the same
mistake.

**A primitive for a nullable column.** Reading is fine until a row has `NULL` and the
provider must put it somewhere; the field cannot hold it.

## Check yourself

<details><summary>Two transactions load the same row. Are the two objects <code>==</code>?</summary>
No. Identity within a persistence context is per context, so each transaction gets its own instance for the same row.
</details>

<details><summary>Why is <code>@Enumerated(EnumType.ORDINAL)</code> dangerous even when the code is correct today?</summary>
It stores a position, not a name. Reordering or inserting a constant changes what every already-stored number means, and nothing in the database or the application can detect it.
</details>

<details><summary>What does <code>ddl-auto: validate</code> buy that <code>none</code> does not?</summary>
It compares the mapping against the real schema at startup, so a field that no longer matches its column fails the boot rather than the first query that touches it.
</details>

## Listings

1. Entity identity keyed on the generated id, and the set that loses the object.
2. A mapping that states its columns, with the reason for each choice.
3. What `ORDINAL` stores, and what a later enum edit does to it.
