## Why this exists

PHP allows a class exactly one parent. That limit is not negotiable and it is not going to change,
so the language provides two other ways to share: an interface, which shares a *type* and no code,
and a trait, which shares *code* and no type. Knowing which of the three you need is most of
object-oriented design in PHP 8.2, and the usual mistake — reaching for inheritance because it is
the familiar one — produces a hierarchy that cannot absorb the second requirement.

Traits in particular are easy to misread. They are not a second inheritance chain, they are not
mixins with runtime dispatch, and PHP 8.2 extended them with constants, which makes them look even
more like classes than they are.

## The idea

An interface is a job description; a trait is a photocopied page of a procedures manual. Applying
for the job means agreeing to the description, and the engine checks that you can do everything on
it. Copying the page into your own manual means the text is now yours: nobody can tell, later, that
it came from somewhere else.

### Where the analogy breaks

The photocopy is made once, at compile time, and the copy is indistinguishable from text written by
hand. That has consequences a "shared behaviour" reading of traits will get wrong. A trait is not a
type — there is no `instanceof Timestamps`, and a parameter cannot be declared as a trait. A static
property declared in a trait is copied too, so every using class gets its own, and `Article::seen()`
and `Comment::seen()` count separately. And when two traits offer the same method name, nothing
resolves it for you: the class fails to compile until you say which copy to keep.

The job-description half of the analogy also understates what an interface costs, which is nothing.
A class may implement any number of them, and each one is a type that can appear in a declaration,
which is the whole reason to prefer an interface for anything a caller needs to name.

## How it works

Interfaces are the types; the class is the implementation:

```php
interface Priced { public function amountInCents(): int; }

final class Book implements Priced, Describable { /* ... */ }

function total(array $lines): int {
    return array_sum(array_map(static fn (Priced $l): int => $l->amountInCents(), $lines));
}
```

A trait supplies the body. Precedence is fixed and worth memorising: a method in the class body
wins over one from a trait, and a trait's method wins over one inherited from a parent.

```
trait class parent
```

Those are three classes with the same method name coming from three different places. A collision
*between two traits* is not resolved by precedence; it is a compile-time fatal, and the location
suffix is removed here because it names the file it happened in:

```
Fatal error: Trait method B::go has not been applied as C::go, because of collision with A::go
```

`insteadof` chooses the winner and `as` gives the loser a second name, which is how a class can
keep both.

The arrangement that survives contact with a real codebase puts all three together: an interface
for the type, a trait for the body, and an abstract method in the trait for the part only the class
can supply.

```php
interface Auditable { public function auditLine(): string; }

trait AuditsByName
{
    abstract public function name(): string;
    public function auditLine(): string { return static::class . ':' . $this->name(); }
}
```

`static::` in that trait resolves to the class the method was called on, not to where the code was
written; `self::` would resolve to the latter.

## Common mistakes

**Type-hinting a trait.** It is not a type. `trait_exists()` is true and `interface_exists()` is
false for the same name, and a declaration naming it will never match anything.

**Expecting a trait's static property to be shared.** Each using class gets its own copy, so a
counter in a trait counts per class.

**Assuming a trait can override the class that uses it.** It cannot; the class body always wins.
This is the opposite of what a parent class does.

**Reaching for inheritance for the second requirement.** Once a class needs behaviour from two
places, the parent slot is already spent. Diagnostics from the wrong shape arrive at run time and
name the declaring class, not the one you called:

```
Error: Call to protected method Record::checksum() from global scope
Error: Cannot instantiate abstract class Record
```

## Check yourself

<details><summary>A parent, a trait and the class body all declare <code>origin()</code>. Which one runs?</summary>

The class body. Trait beats parent, class beats trait. Removing the class method makes the trait's
version run, not the parent's.

</details>

<details><summary>Two traits both declare <code>speak()</code>. What happens?</summary>

The class does not compile. `insteadof` picks one; `as` aliases the other under a new name, so both
can remain reachable.

</details>

<details><summary>Why declare an interface when the trait already supplies the method?</summary>

Because the trait gives no type. Callers need something to write in a parameter declaration, and
only an interface (or a class) can appear there.

</details>

## Listings

1. `classes-interfaces-and-traits-1.php` — interfaces as types, two of them on one class, and
   `instanceof` narrowing.
2. `classes-interfaces-and-traits-2.php` — traits: per-class statics, PHP 8.2 trait constants,
   conflict resolution and precedence.
3. `classes-interfaces-and-traits-3.php` — the interface, trait and abstract class together, plus
   the visibility errors and `static::` versus `self::`.
