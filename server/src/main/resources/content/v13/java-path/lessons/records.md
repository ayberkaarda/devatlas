## Why this exists

A large share of any Java codebase is classes whose whole job is to carry a few values from
one place to another. Written by hand, each one costs a constructor, an accessor per field,
an `equals`, a `hashCode` that must agree with that `equals`, and a `toString`. The cost is
not the typing. It is that a hand-written `equals` and `hashCode` can disagree — and when
they do, the object goes into a `HashMap` and never comes out, with no error anywhere.
Java 21 has a declaration form for exactly this shape of class.

## The idea

A record is a filled-in form. The boxes are printed along the top of the form, in order,
with their names; anyone who picks it up can read what each box means without asking. Two
forms with the same boxes filled in the same way are interchangeable for filing purposes —
the clerk does not care which physical sheet you hand over.

### Where the analogy breaks

A form is inert paper and a record is a class. It can declare methods, implement interfaces
and refuse to be created at all: the compact constructor runs before the fields are assigned,
so a record can enforce its own invariants, which no blank form does. It is also stricter
than paper in one direction — a form's boxes can be erased and rewritten, while a record's
components are `private final` and there is no setter to write.

The place the picture misleads is what "the same boxes filled in the same way" covers. If a
box contains a reference to something else — a list, an array, a mutable object — the record
compares and copies the reference, not the thing at the other end. Two records can be
`equals` while the lists they point at are being changed underneath them. That is the whole
subject of the next lesson.

## How it works

The Java 21 language documentation lists what a record class declares automatically: a
`private final` field and a public accessor for each component in the header, a canonical
constructor with the header's signature, and implementations of `equals`, `hashCode` and
`toString`. It also states the two restrictions people meet first — a record class is
implicitly `final`, and you cannot declare instance variables or instance initialisers in one.

```java
record Point(int x, int y) { }
// Point.class.getSuperclass() is java.lang.Record; Point.class.isRecord() is true
```

The compact constructor has no parameter list and no assignments. Its body sees the
constructor parameters, and whatever they hold when the body ends is what gets stored — so
validation and normalisation both live there.

```java
record Money(String currency, long minorUnits) {
    Money {
        if (currency == null || currency.length() != 3) throw new IllegalArgumentException(currency);
        currency = currency.toUpperCase(Locale.ROOT);   // assigns the parameter, not a field
    }
}
```

The generated `equals` compares every component and the generated `hashCode` is derived from
the same components, so the two agree by construction. That is what makes a record safe as a
`HashMap` key, and listing 3 shows the same data as a plain class failing that lookup because
nobody wrote the pair by hand.

```java
map.put(new SeatId("A", 12), "Ada");
map.get(new SeatId("A", 12));   // "Ada" for a record, null for a class with no equals
```

## Common mistakes

**Adding an instance field.** `javac --release 21` answers `error: field declaration must be
static`, with the hint "consider replacing field with record component". Records hold exactly
the components in their header.

**Trying to extend one.** A record is implicitly `final`, so `class E extends R2` fails with
`error: cannot inherit from final R2`. Share behaviour through an interface instead.

**Expecting deep immutability.** A `record Config(List<String> hosts)` hands out the same
list it was given. It is unmodifiable only if what you passed in was.

**Using a record with a mutable component as a key.** The generated `hashCode` reads the
component at the moment it is called; if that component's own hash changes, the entry is
filed under a value the map will no longer compute.

## Check yourself

<details><summary>Why does a record work as a <code>HashMap</code> key when a plain class with the same fields does not?</summary>

The plain class inherits `equals` and `hashCode` from `Object`, which mean identity, so a
freshly built lookup key is a different object and misses. A record's generated pair compares
components, and they are derived from the same components, so they cannot disagree.

</details>

<details><summary>A compact constructor assigns to <code>currency</code>. What is it assigning to?</summary>

The constructor parameter. Fields are assigned from the parameters after the compact body
finishes, so writing to a parameter is how normalisation is expressed. There is no field to
assign to at that point.

</details>

<details><summary>Can a record implement an interface?</summary>

Yes. It cannot extend a class, because it already extends `java.lang.Record` and is
implicitly final, but it may implement any number of interfaces — which is what makes records
the usual members of a sealed hierarchy.

</details>

## Listings

1. `records-1.java` — the members the compiler writes, read back through reflection.
2. `records-2.java` — a compact constructor that validates and normalises.
3. `records-3.java` — a record and a plain class as keys, with the lookup that fails.
