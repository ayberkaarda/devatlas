## Why this exists

Computed properties derive values. Sometimes what you need on a change is not a
value at all: save a draft, cancel an in-flight request, write to storage, ask
the server for a new page. Vue 3.5's watchers guide describes exactly that job —
performing "side effects in reaction to state changes".

The trouble is that a watcher can also *look* like a way to derive a value. You
watch two refs, and in the callback you write a third. It works, roughly, and it
brings along a stale window, an ordering question and a second source of truth
that the computed version does not have. Most watchers in a young codebase are
computed properties that took the long way round.

## The idea

A watcher is a smoke alarm. It does not make the room safer and it does not
change what is in the room; it notices a change and does something *outside* —
rings a bell, calls somebody. A computed property is not an alarm at all. It is
an arithmetic identity: the total simply is price times quantity, and there is
no moment at which it needs to be told.

### Where the analogy breaks

A smoke alarm goes off the instant it detects smoke. A watcher, by default, does
not. Only `flush: 'sync'` fires "synchronously, before any Vue-managed updates",
and the guide warns that sync watchers "do not have batching and trigger every
time a reactive mutation is detected". The default is batched, and batching
means there is a stretch of code after your write during which the callback has
not run yet. The third listing measures that stretch: two writes, then an
immediate read.

```
straight after two writes, computed: Grace Hopper
straight after two writes, mirror:   Ada Lovelace
watcher callback runs for two writes: 1
```

A second leak: an alarm reacts to everything in the room. `watch` does not. It
"only tracks the explicitly watched source. It won't track anything accessed
inside the callback." Reading another ref inside the callback creates no
dependency at all — which is deliberate, and is the difference from
`watchEffect`, which "automatically tracks every reactive property accessed
during its synchronous execution".

## How it works

`watch` takes a source and a callback. The source may be a ref, a getter
function, a reactive object, or an array of those. The callback receives the new
and old values and does not run on creation.

```js
import { reactive, watch, watchEffect } from 'vue'

const invoice = reactive({ price: 100, taxRate: 0.2 })

watch(() => invoice.price, (price, oldPrice) => save(price, oldPrice))
watchEffect(() => save(invoice.price, invoice.taxRate))
```

The first listing counts both against the same two writes:

```
on creation: watchEffect runs 1, watch callbacks 0
price changed: watchEffect runs 2, watch callbacks 1
taxRate changed: watchEffect runs 3, watch callbacks 1
```

`watchEffect` runs once immediately, to collect what it reads. `watch` does not
run its callback until the source changes, which is why the two counts start one
apart.

## Common mistakes

The two spellings differ by five characters and by everything else:

```js
watch(invoice.price, onChange)        // a number was passed; nothing subscribes
watch(() => invoice.price, onChange)  // a getter; the read happens under watch
```

**Passing a property instead of a getter.** The guide shows the mistake with the
comment attached: `watch(obj.count, …)` "won't work because we are passing a
number to `watch()`". A number is not a source; nothing is subscribed, no
callback ever fires, and nothing warns you at the point of the bug. The second
listing runs both spellings side by side:

```
watch(obj.count, ...) fired: 0
watch(() => obj.count, ...) fired: 1
```

**Expecting `newValue` and `oldValue` to differ on a deep watch.** Watching a
reactive object watches it deeply, and the guide notes that `newValue` "will be
equal to `oldValue` ... because they are the same object". Comparing them to
decide whether to act will decide never.

**Mirroring derived state into a ref.** A watcher that writes `fullName.value`
whenever the parts change has two problems the computed version does not: the
mirror is stale until the queue drains, and something else can now write to it.
The guide's framing of a computed is the test — it "declaratively describes how
to derive a value based on other values".

**Reading a value in the callback and expecting it to trigger the watcher.**
Only the source is a dependency. If the callback needs to re-run when a second
value changes, put that value in the source array.

## Check yourself

<details><summary>When is <code>watchEffect</code> the wrong tool?</summary>
When you need to know what changed, or when the callback reads more state than
it should depend on. <code>watchEffect</code> subscribes to everything it
touches synchronously; <code>watch</code> subscribes to what you named.
</details>

<details><summary>Your watcher never fires and there is no error. What do you check first?</summary>
The source. If it is a property access rather than a getter, a plain value was
handed to <code>watch</code> and there is nothing to subscribe to.
</details>

<details><summary>Watcher or computed: converting a list of orders into a total?</summary>
Computed. Nothing outside the component needs to happen; the total is a function
of the orders. Keep watchers for work that leaves the component.
</details>

## Full listings

1. `watch` and `watchEffect`, counted against the same writes.
2. Every source shape, including the one that subscribes to nothing.
3. A derived value by watcher and by computed, with the stale window measured.
