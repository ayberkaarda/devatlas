## Why this exists

Everything in the previous three lessons rests on one question: how does Vue
know that this piece of state is used by that piece of the screen? Nobody
declares it. There is no dependency list to keep in step, which is exactly why
it feels like magic, and exactly why the failures are baffling when they come.

They are not baffling once you have seen the bookkeeping. Vue 3.5's
reactivity-in-depth guide gives it as pseudo-code — a proxy whose getter calls
`track(target, key)` and whose setter calls `trigger(target, key)`, and an
effect that "sets itself as the current active effect before running the actual
update", so that "`track()` calls during the update" have something to record.
The first listing builds that and prints the map. Once you can see what the map
contains, "reactivity broke" becomes "nothing wrote that entry, and here is
why".

## The idea

Every property is a mailing list, and reading a property during an effect signs
that effect up. Writing to the property posts to its list. Nobody maintains the
lists by hand: they are written by the act of reading.

### Where the analogy breaks

A mailing list keeps you subscribed until you leave. This one does not. An
effect's subscriptions are rebuilt from scratch on every run, because the set of
properties it reads can differ from run to run — an effect that skipped a branch
last time never read what the branch reads, and one that took the branch and now
skips it must be removed from those lists or it will keep waking for state it no
longer looks at. The second listing runs the same effect with and without that
removal step, and only one of them stops waking up.

The analogy leaks again on timing. You can join a mailing list at any hour. You
can join a dependency only while an effect is running, synchronously. Vue 3.5's
watchers guide says `watchEffect` "automatically tracks every reactive property
accessed during its synchronous execution"; after an `await`, the effect is no
longer the active one and reads are simply reads.

## How it works

The map is three levels deep: target object, then property key, then the set of
effects that read it. A write looks up one set and runs it.

```js
const buckets = new WeakMap() // target -> key -> Set(effects)

function track(target, key) {
  if (!activeEffect) return
  buckets.get(target).get(key).add(activeEffect)
}

function trigger(target, key) {
  buckets.get(target)?.get(key)?.forEach((run) => run())
}
```

An effect whose body branches subscribes to a different set on each run, and
that is the whole of it:

```js
watchEffect(() => {
  render(user.firstName)
  if (ui.showAge) render(user.age) // read, and therefore tracked, only here
})
```

The first listing renders a greeting that reads `user.firstName` and, only when
a flag is set, `user.age`. What it tracked:

```
tracked after run 1: ui.showAge, user.firstName
after user.lastName changed, runs: 1
after user.firstName changed, runs: 2
after user.age changed, runs: 2
```

`user.lastName` exists, it is on the same reactive object, and it is invisible —
because nothing read it. That granularity is the point. Vue 3.5's template
syntax guide claims the framework "can intelligently figure out the minimal
number of components to re-render and apply the minimal amount of DOM
manipulations when the app state changes"; the map above is where that
minimality comes from, and an effect that reads more than it needs enlarges it.

## Common mistakes

**Assuming the whole object is watched.** It is per property, per run. A
component reading `user.firstName` will not re-render when `user.lastName`
changes, and that is the system working.

**Expecting a read after `await` to count.** In the third listing, an effect
reads one property before a suspension point and one after; changing the first
wakes it, changing the second does not:

```
changing the property read after await woke the effect: false
changing the property read before await woke the effect: true
```

Read what you depend on before the first `await`, or use `watch` with an
explicit source.

**Believing a property must exist to be tracked.** Vue 3.5 uses a `Proxy`, so
the trap fires for an absent key too. The same listing reads `settings.fontSize`
when it is `undefined`, then adds it, then deletes it, and the effect runs each
time:

```
effect read a property that does not exist, runs: 1
after the property was added, runs: 2
after the property was deleted, runs: 3
```

**Reading state outside the effect and closing over the result.** A value pulled
out before the effect body starts was read with no active effect. It is a
number, and it subscribes to nothing.

## Check yourself

<details><summary>An effect reads a property only inside an <code>if</code>. What happens when the branch is not taken?</summary>
The property is not read, so the effect does not subscribe to it. If the branch
is taken later, the re-run collects the dependency then.
</details>

<details><summary>Why must an effect drop its old subscriptions before re-running?</summary>
Because the set of properties it reads can shrink. Without the drop it stays on
lists for state it no longer reads and re-runs for changes that cannot affect
its output.
</details>

<details><summary>Your effect fetches, awaits, then reads <code>filters.page</code>, and changing the page does nothing. Why?</summary>
Tracking is synchronous. By the time the code after the <code>await</code> runs,
no effect is active. Read <code>filters.page</code> before the await, or make it
the explicit source of a watcher.
</details>

## Full listings

1. The dependency map, printed after each run.
2. The branch not taken, with and without dropping stale subscriptions.
3. The absent property, the deleted one, and the read after `await`.
