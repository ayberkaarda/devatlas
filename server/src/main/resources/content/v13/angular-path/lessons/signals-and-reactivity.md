## Why this exists

A user interface is a picture of some data, and the hard part has never been
drawing it once. The hard part is that the data changes and the picture must
change with it. A plain field cannot help with that: it holds a value and says
nothing when the value is replaced, so something else has to work out what on
screen is now out of date. Frameworks used to answer this by guessing — after
any event that might have changed something, walk the whole component tree and
compare. Angular 22 answers it differently. A signal is a value that records
who read it, so the framework is told rather than having to look.

## The idea

A signal is a value with a visitors' book. Reading it signs your name. When the
value is replaced, everyone in the book is told that what they worked out from
it is no longer good. Nobody has to declare an interest in advance and nobody
has to remember to cancel one: the act of reading is the registration.

### Where the analogy breaks

A visitors' book accumulates. A signal's does not — the dependencies of a
derivation are collected afresh every time it runs, and Angular's documentation
is explicit that "only the signals actually read during the derivation are
tracked". A branch that stops reading a signal stops depending on it, which is
what makes a conditional read behave the way you would want and not the way a
static subscription list would.

The second leak is the word "told". Nothing is pushed eagerly. A derived value
is marked out of date and recomputed when somebody next reads it, so a signal
nobody is reading costs nothing at all when it changes.

The third is that signing the book is not free of rules. A read inside a
reactive context registers a dependency; the same read in a plain callback that
ran outside one does not, and that is why writing a value into a field is not
the same as reading the signal where it is used.

## How it works

`signal(initial)` creates a writable signal. Calling it reads the value —
Angular's documentation puts it as "signals are getter functions - calling them
reads their value" — and `set` and `update` replace it.

```ts
const count = signal(0);
count.set(3);
count.update((value) => value + 1);
console.log(count()); // 4
```

Whether a write counts as a change is decided by an equality function, and
"by default, signals use referential equality (`Object.is()` comparison)". That
one sentence explains the most common surprise in this area: pushing onto an
array held in a signal leaves the reference identical, so the write notifies
nobody. Replacing the array does.

```ts
private readonly entries = signal<readonly QueueEntry[]>([]);

add(entry: QueueEntry): void {
  this.entries.update((current) => [...current, entry]);
}
```

The pattern this application uses everywhere is a private writable signal and a
public read-only view of it, so that every write goes through a method the
class owns.

```ts
private readonly currentUser = signal<SessionUser | null>(null);
readonly user = this.currentUser.asReadonly();
```

A template that calls `user()` is a reader like any other. There is no
subscription to create and none to tear down, because the read is the
subscription and the view's dependencies are dropped when the view is.

## Common mistakes

**Mutating the value instead of replacing it.** With the default equality,
`items().push(x)` changes what the array holds and changes no reference, so
nothing that read the signal is notified. The listing that runs shows the same
failure without a framework: two entries in the queue and one notification.

**Copying a signal's value into a field.** `this.count = this.entries().length`
reads once and stores the answer. The stored number is a photograph; the first
listing prints `copied: 1` beside `actual: 2` to make the gap concrete.

**Handing out the writable signal.** Returning `entries` rather than
`entries.asReadonly()` gives every caller a `set`, and the next wrong value on
screen has as many possible authors as there are callers.

## Check yourself

<details><summary>Why does <code>push</code> on an array in a signal notify nobody?</summary>
Because the default equality is <code>Object.is</code> on the value the signal
holds, and the value is the array reference. Mutating the array leaves that
reference identical. Setting a new array changes it.
</details>

<details><summary>What registers a dependency?</summary>
Reading the signal inside a reactive context — a computed, an effect, or a
template expression. Dependencies are re-collected on every run, so a read that
does not happen this time is not a dependency this time.
</details>

<details><summary>Why is there nothing to unsubscribe?</summary>
Because no subscription was created by hand. The reader is registered by the
read and forgotten when the reader is destroyed.
</details>

## Full listings

1. The problem without signals: a stale copy, and a notification somebody forgot to send.
2. A store built from a writable signal, a read-only view and a derived count.
3. A component that reads the store and holds no state of its own.
