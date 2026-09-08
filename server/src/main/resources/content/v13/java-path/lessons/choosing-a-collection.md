## Why this exists

Reaching for `HashMap` and `ArrayList` by reflex is right most of the time, which is why the
times it is wrong are hard to notice. A test asserts on the order a `HashMap` iterated in and
passes for a year. A set quietly stops finding an element it still contains. Both are the
same kind of error: taking a guarantee the chosen implementation never made. The way out is
to read what each one promises, because in Java 21 they promise different things on purpose.

## The idea

Maps are filing systems. A `HashMap` is a wall of pigeonholes with a rule for computing which
hole a given key belongs in — you go straight to the hole, and nobody arranged the wall for
reading. A `LinkedHashMap` is the same wall with a thread running through the holes in the
order you filled them. A `TreeMap` is a cabinet where the clerk keeps the folders sorted.

### Where the analogy breaks

A wall of pigeonholes at least stays in one arrangement. The `HashMap` javadoc says the class
"makes no guarantees as to the order of the map; in particular, it does not guarantee that
the order will remain constant over time" — the wall is rebuilt as it grows. Listing 1 prints
one legal ordering for five keys; the lesson is that it differs from insertion order, not
that it is that particular order.

The second break is worse, because a physical filing system tolerates it. Relabel a folder on
a shelf and you can still see it and still pull it out. Relabel a key already in a `HashSet`
and it is filed under a hash nobody will compute again: listing 3 finds the element by
iteration, and `contains` says `false`, and `remove` says `false`, and the size stays 1.

## How it works

Pick on the guarantee you need. `HashMap` gives constant-time lookup and no ordering.
`LinkedHashMap` maintains a doubly linked list through its entries defining an encounter order
that is, as its javadoc puts it, normally the order in which keys were inserted. `TreeMap`
keeps keys sorted and costs a comparison per level.

Java 21 adds a vocabulary for the collections that do have a first and a last.
`SequencedCollection` was introduced in Java 21 and declares `getFirst`, `getLast`,
`addFirst`, `addLast`, `removeFirst`, `removeLast` and `reversed`; `List`, `Deque`,
`SortedSet` and `NavigableSet` extend it, with `SequencedMap` doing the same for maps.

```java
items.getFirst();      // instead of items.get(0)
items.getLast();       // instead of items.get(items.size() - 1)
map.firstEntry();      // on a LinkedHashMap, via SequencedMap
```

`reversed()` returns a **view**, not a copy. Writing through it writes through to the original,
which listing 2 demonstrates by setting element 0 of the reversed list and reading the change
back from the last element of the original.

```java
List<String> backwards = items.reversed();
backwards.set(0, "third-renamed");   // items is now [first, second, third-renamed]
```

The factory methods are a separate axis. `List.of`, `Set.of` and `Map.of` produce unmodifiable
collections that also reject `null`, including in queries.

## Common mistakes

**Asserting on `HashMap` iteration order.** It is unspecified and may change as the map grows.
If you need an order, choose the implementation that promises one.

**Mutating a key after inserting it.** The hash it was filed under no longer matches the hash
that will be computed for the lookup. The entry becomes unreachable by key and undeletable,
while still counting towards `size`.

**Calling `contains(null)` on an immutable collection.** `List.of("a", "b").contains(null)`
throws `NullPointerException`; the same query on an `ArrayList` returns `false`. Listing 3
runs both.

**Treating `Map.of` as a starting point.** It throws `UnsupportedOperationException` on `put`.
Wrap it in a `LinkedHashMap` if you intend to add to it.

**Using `get(0)` where the collection is not a list.** In Java 21 `getFirst()` reads the same
on a `List`, a `Deque` and a `SortedSet`, and works on collections that have no index at all.

## Check yourself

<details><summary>Two maps with the same entries in different iteration orders — are they <code>equals</code>?</summary>

Yes. Map equality is defined on entries, not order, so a `HashMap` and a `LinkedHashMap`
holding the same pairs are equal. Listing 1 checks it.

</details>

<details><summary>A <code>HashSet</code> reports <code>size() == 1</code> but <code>contains(x)</code> is <code>false</code> for the only element. How?</summary>

The element's hash changed after it was added. The set looks in the bucket the new hash
selects and the element is not there; iteration walks every bucket and finds it.

</details>

<details><summary>Does <code>list.reversed()</code> cost a copy?</summary>

No. It is a view over the same elements, so it is cheap, and it is not a snapshot: changes
through either side are visible from the other.

</details>

## Listings

1. `choosing-a-collection-1.java` — three maps, three iteration orders, one insertion order.
2. `choosing-a-collection-2.java` — the sequenced collection methods added in Java 21.
3. `choosing-a-collection-3.java` — a key that moved after it was filed.
