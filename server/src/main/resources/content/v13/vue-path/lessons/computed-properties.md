## Why this exists

A template needs values you did not store: a total, a filtered list, a formatted
name. You can compute them in the template, in a method, or in a computed
property, and all three produce the same characters on the screen. Vue 3.5's
guide says so directly — for the end result "the two approaches are indeed
exactly the same".

What differs is how often the work happens, and that difference is not visible
until the list is long or the derivation is expensive. A method is re-run
"whenever a re-render happens", however many times that is and whatever caused
it. A computed property is not.

## The idea

A computed property is a receipt pinned to a noticeboard. Somebody worked the
total out once and pinned it up; everyone who walks past reads the pin rather
than doing the arithmetic. When a price changes, the pin is taken down — and it
is only replaced when the next person actually looks.

### Where the analogy breaks

A pinned receipt is a piece of paper you could scribble on. A computed value is
not a place to put anything. The guide is firm: the returned value "is derived
state. Think of it as a temporary snapshot - every time the source state
changes, a new snapshot is created ... a computed return value should be treated
as read-only and never be mutated". The third listing edits a returned array by
hand, changes the source, and the edit is gone: the snapshot was replaced, not
amended.

The analogy leaks in a second place. A noticeboard receipt goes stale silently
and a human eventually notices. A computed property has no such fallback: it is
invalidated only by the reactive dependencies it read while running. Anything
else it read — a clock, a module-level variable, a value that arrived through a
closure — is invisible, and the pin stays up forever.

## How it works

Caching needs three parts: a stored value, a flag saying whether that value is
still good, and a subscription that sets the flag. The first listing builds all
three on the tracking mechanism Vue 3.5 documents, and counts how often the
getter body actually executes.

```js
import { reactive, computed } from 'vue'

const cart = reactive({ price: 2, quantity: 3 })
const total = computed(() => cart.price * cart.quantity)
```

Reading `total.value` three times in a row runs the getter once. Change
`cart.quantity` and the getter still does not run — the flag is set, but nothing
is recomputed until somebody reads. The counter from that run:

```
first read: 6 (getter calls: 1)
second read: 6 (getter calls: 1)
after the dependency changed, getter calls: 1
read after the change: 10 (getter calls: 2)
```

The second listing puts a computed and a method side by side across four
renders of the same screen, and the totals are the argument:

```
renders: 4, computed getter calls: 2
renders: 4, method calls: 4
```

A computed may also be given a setter, which is how a derived value stays
writable without becoming a second copy of the state:

```js
const fullName = computed({
  get: () => `${first.value} ${last.value}`,
  set: (value) => ([first.value, last.value] = value.split(' ')),
})
```

The guide's reason for caring is the compounding one: "Imagine we have an
expensive computed property `list`, which requires looping through a huge array
... Then we may have other computed properties that in turn depend on `list`.
Without caching, we would be executing `list`'s getter many more times than
necessary!"

## Common mistakes

**Depending on something the tracker cannot see.** The guide gives the canonical
case: a computed over `Date.now()` "will never update, because `Date.now()` is
not a reactive dependency". The third listing reads the clock through a
computed, waits for the clock to move, and reads again — the value does not
change. A plain `let` outside any reactive container behaves the same way.

**Doing work in the getter.** "Don't mutate other state, make async requests, or
mutate the DOM inside a computed getter." A getter runs lazily and only when
read, so a side effect inside one happens at a time nobody chose. Effects and
watchers exist for that.

**Mutating what a computed returned.** Sorting a computed array in place appears
to work and is discarded the moment a dependency changes, because the getter
builds a new array. Change the source and let the computed re-derive.

**Reaching for a computed where the value is not derived.** If nothing computes
it — it comes from a fetch, or from a user typing — it is state, and it belongs
in a `ref`.

## Check yourself

<details><summary>A computed and a method both show the right number. When does the choice matter?</summary>
When the derivation is expensive or the screen re-renders for unrelated reasons.
The method runs once per render; the computed runs once per change to something
it read.
</details>

<details><summary>Your computed shows a timestamp that never advances. Why?</summary>
The clock is not a reactive dependency, so nothing ever marks the cached value
stale. Put the time in a <code>ref</code> that something updates, and derive
from that.
</details>

<details><summary>You sorted the array a computed returned and the sort vanished. What happened?</summary>
A dependency changed and the getter produced a fresh array. The returned value
is a snapshot; sort the source, or sort inside the getter.
</details>

## Full listings

1. A computed with its cache, its dirty flag, and a counter on the getter.
2. Four renders, one computed and one method, both counted.
3. Dependencies the tracker cannot see, and the snapshot you cannot edit.
