## Why this exists

Before PHP 8.1 a fixed set of values was spelled as class constants, and every function that took
one had to declare `string` and hope. Nothing stopped a caller passing `'draft '` with a trailing
space, and nothing stopped a `switch` quietly missing a case that was added later. PHP 8.1 added
enumerations, and PHP 8.2 carries them unchanged, so in the version this track teaches a fixed set
can be a type the engine enforces at every call.

An enum is worth reaching for exactly when a value has a small, known, closed set of possibilities
and the code branches on which one it is. It is worth *not* reaching for when the set is open, or
when the cases need per-instance state, because an enum case has neither.

## The idea

An enum is the set of keys cut for one building. There are three of them, they hang on one hook,
and handing someone "the front door key" means handing them that exact key — not a copy, not a
description of it. A door either accepts one of the three or it does not open.

### Where the analogy breaks

Keys can be copied and an enum case cannot. Every reference to `Stage::Draft` is the same object,
so `===` is the correct comparison and there is no second instance to guard against. `new` on an
enum is an `Error`, and so is assigning to a case's `value`, because the property is readonly.

The analogy also suggests a key carries nothing but its shape. An enum case can carry methods,
implement interfaces and hold constants — what it cannot carry is state. There is no constructor
and no writable property, so two cases can never differ in anything except which case they are.

And keys fit locks; enum cases do not fit array keys. An array key is an `int` or a `string`, so
`$counts[Priority::High]` is a `TypeError`. The backing value works, and `SplObjectStorage`
accepts the case itself.

## How it works

A pure enum has cases and nothing else attached to them. A backed enum gives each case one scalar,
for crossing a boundary — a column, a query string, a JSON document.

```php
enum Currency: string
{
    case Euro = 'EUR';
    case Pound = 'GBP';
    case Yen = 'JPY';

    public const Default = self::Euro;

    public function minorUnits(): int { return $this === self::Yen ? 0 : 2; }
}
```

`from()` and `tryFrom()` are the boundary. They differ in what an unrecognised value means: a bug,
or an input.

```
ValueError: "CHF" is not a valid backing value for enum Currency
```

`tryFrom()` returns `null` instead, which composes with `??` into a default. Going the other way,
a backed enum serialises to its value with no extra work, and `json_decode()` gives back the
scalar rather than the case — the conversion back is `from()` again.

```
{"currency":"JPY","minor":0}
```

`cases()` returns every case in declaration order, which is specified rather than incidental, so
iterating it is reproducible. Combined with `match`, that is where the type actually pays: a
`match` over an enum with no `default` arm throws `UnhandledMatchError` the first time a newly
added case reaches it, instead of falling through to something that looked safe.

## Common mistakes

**Using an enum case as an array key.** The key has to be `int` or `string`:

```
TypeError: Illegal offset type
```

`Priority::High->value` is the usual fix. `SplObjectStorage` is the other.

**Trying to give a case state.** There is no constructor, and the backing value is readonly:

```
Error: Cannot modify readonly property Priority::$value
```

**Reaching for `from()` on user input.** An unknown value from a request is not a programming
error; `tryFrom()` with a fallback is the honest handling.

**Assuming two enums with the same backing value are interchangeable.** `Priority::Low` and
`Level::Low` may both be backed by `1`, and they are different types. Only the `->value`
comparison is true.

**Expecting `from()` on a pure enum.** A pure enum has no backing value, so `BackedEnum` is not
among its interfaces and neither method exists.

## Check yourself

<details><summary>When should an enum be backed?</summary>

When a case has to cross a boundary PHP does not control — persisted in a column, sent in JSON,
read from a query string. If nothing outside the process needs to name a case, a pure enum is
simpler and cannot be constructed from a wrong scalar.

</details>

<details><summary>Why is <code>===</code> the right comparison for enum cases?</summary>

Each case is a single object, so identity is the same question as equality and there is no second
instance to worry about. `==` gives the same answer here, and `===` says what is meant.

</details>

<details><summary>A <code>match</code> over an enum has an arm for every case and no <code>default</code>. What happens when a tenth case is added?</summary>

That case reaches no arm and PHP throws `UnhandledMatchError` when it is passed in. Adding a
`default` arm would swallow it silently, which is why leaving it out is usually the point.

</details>

## Listings

1. `enums-a-fixed-set-1.php` — a pure enum with methods, `cases()`, and what `new` on it does.
2. `enums-a-fixed-set-2.php` — a backed enum at a boundary: `from()`, `tryFrom()`, JSON in both
   directions.
3. `enums-a-fixed-set-3.php` — enums implementing an interface, and the three things a case is
   not: an array key, a mutable object, or interchangeable with another enum's case.
