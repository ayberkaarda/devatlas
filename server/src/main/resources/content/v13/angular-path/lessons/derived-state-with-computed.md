## Why this exists

Most of what a screen shows is not stored anywhere. A badge counting unfinished
downloads, a percentage, an "is this person allowed to edit" flag — each of
them is a question about state rather than a piece of it. You can answer such a
question once and keep the answer in a field, and then you own a second copy of
the truth and every write to the first one has to remember the second. Angular
22 offers the alternative: declare the question, and let the framework decide
when it needs a fresh answer.

## The idea

A computed signal is a recipe, not a portion. You do not put the finished dish
in a container and hope it is still what you want tomorrow; you write down how
it is made and make it when it is wanted. The recipe names its ingredients, and
that is the whole of its dependency list.

### Where the analogy breaks

A recipe is re-cooked from scratch every time. A computed signal is not: it
recomputes when an ingredient has changed and otherwise hands back what it made
last time. Angular's documentation puts it plainly — "the calculated value is
then cached, and if you read `doubleCount` again, it will return the cached
value without recalculating" — which is why "you can safely perform
computationally expensive derivations in computed signals".

A recipe also cannot notice that an ingredient went off in the night. A
computed signal can, because the ingredients are signals and reading them is
what records the dependency.

The last leak is the one that decides how you use it. A recipe may say "go to
the shop"; a derivation may not. A computed is evaluated lazily and only when
something reads it, so a fetch, a write to another signal or a console message
placed inside one runs at a moment nobody chose, or never. Derivations are
pure; the outside world belongs in an effect.

## How it works

`computed` takes a function and returns a read-only signal. Angular's API
reference describes it as creating "a computed `Signal` which derives a
reactive value from an expression".

```ts
readonly role = computed<Role | null>(() => this.currentUser()?.role ?? null);
readonly isAdmin = computed(() => this.role() === 'ADMIN');
readonly canAdminister = computed(() => this.isAdmin() || this.isEditor());
```

Only `currentUser` is stored. Everything below it is a question, and questions
cannot disagree with each other: there is no sequence of writes that leaves
`canAdminister` saying one thing while `role` says another, because there is
nothing to write.

Dependencies are collected while the derivation runs, and only those actually
read are tracked. That is what makes a conditional derivation behave sensibly.

```ts
readonly summary = computed(() =>
  this.compact() ? this.count() : this.entries().map((e) => e.id).join(', '),
);
```

In compact mode this reads `compact` and `count`; a change to `entries` alone
does not invalidate it, because `entries` was not an ingredient on that run.

A computed also chains without cost. `canAdminister` above depends on two other
computed signals, each of which caches, so a template that reads it repeatedly
during one render pays for the derivation at most once per change.

## Common mistakes

**Storing the derived value in a field.** The first listing shows what that
costs when the cache key does not mention everything the derivation reads: it
prints `scaled after the multiplier changed: 35` beside `the honest answer: 70`.
A computed cannot make that mistake, because its dependency list is not written
by hand — it is whatever the derivation read.

**Doing work in a derivation.** A fetch, a `set` on another signal, or a
counter incremented inside `computed` runs on the framework's schedule, not
yours: memoisation means it may not run when the inputs change, and laziness
means it does not run at all until somebody reads the result.

**Reaching for an effect to keep two signals in step.** If one value can be
worked out from another, the second one is a computed. Angular's guidance is
direct about the alternative: "avoid using effects for propagation of state
changes."

## Check yourself

<details><summary>Why is a computed cheaper than it looks in a template that reads it five times?</summary>
Because it caches. The derivation runs when a dependency has changed since the
last read; the other four reads return the memoised value.
</details>

<details><summary>A derivation reads <code>b</code> only inside an <code>if</code>. Does a change to <code>b</code> invalidate it?</summary>
Only if the branch that reads <code>b</code> ran. Dependencies are collected
per run, so a signal not read on the last run is not a dependency now.
</details>

<details><summary>Why does a <code>console.log</code> inside a computed mislead you?</summary>
Because it reports the derivation's schedule rather than the data's. It is
skipped when the cached value is returned, and it does not run at all until
something reads the signal.
</details>

## Full listings

1. Memoisation written by hand, and the stale answer a hand-written cache key produces.
2. A chain of derivations over a single stored value.
3. A presentational component whose entire behaviour is two derivations.
