## Why this exists

Signals and derivations describe values. They cannot focus a text field, write
to local storage, start a network request or tell an analytics service that a
page was opened, and they must not try: a derivation is allowed to be skipped,
cached or never run at all. Angular 22 provides one place for the work that has
to actually happen — `effect` — and the single most common design error in a
signal-based application is putting things in it that are not that work. This
lesson is about the border, and which side of it a piece of code belongs on.

## The idea

An effect is a translator standing at the border. Inside, everything speaks
signals: values, derivations, templates. Outside, nothing does — the DOM, the
network, the file system, another library's imperative API. The translator's
job is to carry a message out when the state inside has changed, and to carry
nothing back.

### Where the analogy breaks

A translator carries messages in both directions, and this one may not. An
effect that writes a signal other things read is propagating state, and
Angular's guidance names the outcomes: "avoid using effects for propagation of
state changes. This can result in `ExpressionChangedAfterItHasBeenChecked`
errors, infinite circular updates, or unnecessary change detection cycles."

A translator also waits to be asked. An effect does not: "effects always run at
least once", and they "always execute asynchronously, during the change
detection process". You do not choose the moment. Code that must run at a
precise point in a sequence does not belong here.

The third leak is memory. A translator remembers the last conversation; an
effect deliberately does not, and the cleanup callback exists so that whatever
the previous run started can be cancelled before the next one begins.

## How it works

`effect` takes a function, runs it, and runs it again whenever a signal that
the function read has changed. It must be created "within an injection
context" unless an injector is passed, which in practice means a component,
directive or service constructor.

```ts
constructor() {
  effect(() => {
    const slug = this.slug();
    void this.load(slug);
  });
}
```

That effect depends on `slug` and on nothing else. The signals `load` writes
are not read inside the effect, so writing them does not schedule another run —
which is the discipline that keeps an effect from feeding itself.

The callback receives a cleanup registration function, "invoked before the next
run of the effect begins, or when the effect is destroyed".

```ts
effect((onCleanup) => {
  const controller = new AbortController();
  onCleanup(() => controller.abort());
  void this.load(this.slug(), controller.signal);
});
```

Without that, a slow request for the page a reader has left can arrive after
the fast request for the page they are on, and win. The first listing runs the
race without any framework: it prints `without cleanup: body of
what-a-component-is` even though `signals-and-reactivity` was requested last.

Effects are destroyed with the thing that created them, so an effect in a
component constructor stops when the component does.

## Common mistakes

**Using an effect to keep one signal in step with another.** The third listing
puts both versions side by side. The mirrored one stores the same fact twice
and is a run behind for as long as the effect has not executed; the derived one
cannot disagree with itself.

**Reading, inside the effect, a signal the effect writes.** That is a
dependency on its own output, and it re-schedules itself for as long as the
value keeps changing.

**Expecting the first run to be skipped.** Effects run at least once, so an
effect written as "when the slug changes, load it" also loads on creation. That
is usually what you wanted; when it is not, the guard belongs inside the effect
and has to be explicit.

**Starting something long-running with no cleanup.** A subscription, a timer or
a request begun in an effect that is never cancelled outlives the run that
started it and, on a destroyed component, outlives the component.

## Check yourself

<details><summary>Why does an effect that writes a signal it also reads misbehave?</summary>
Because the write invalidates a dependency of the effect, which schedules the
effect again. Where the value keeps changing, the cycle does not settle.
</details>

<details><summary>When does the cleanup callback run?</summary>
Before the next run of the same effect, and when the effect is destroyed —
which for an effect created in a component constructor means when that
component is destroyed.
</details>

<details><summary>You want a value that is always <code>a + b</code>. Effect or computed?</summary>
Computed. Nothing outside the reactive system is involved, so there is no
border to cross and no schedule to reason about.
</details>

## Full listings

1. The race an effect's cleanup exists to prevent, run without a framework.
2. A component that loads a post when its route input changes, and aborts the request it replaces.
3. The mirrored store and the derived store, side by side.
