## Why this exists

For most of Angular's life, a library called zone.js patched the browser's
asynchronous APIs — timers, event handlers, network callbacks — so that the
framework could be told "something happened somewhere" and respond by checking
the whole component tree for changed values. It worked, it was expensive, and
it made the answer to "why did this re-render" nearly unanswerable. Angular 22
does not work that way. "Zoneless is the default in Angular v21+ so you do not
need to do anything to enable it", and the list of things that cause a refresh
is now short enough to hold in your head. This lesson is that list.

## The idea

A zone was a caretaker with a master key. Any noise anywhere in the building
sent them out to walk every corridor and try every door, because they had no
way of knowing which room the noise came from. Zoneless is the opposite
arrangement: each room has a bell, and the caretaker goes only where a bell
rang.

### Where the analogy breaks

A bell is rung by whoever is in the room. A signal is not: it is rung by a
signal being *read in a template* and then changed. A signal that no view reads
can be written a thousand times and schedule nothing, which is a feature — it
is why a store can be updated freely without any rendering cost.

The caretaker also either walks or does not. Angular does something in between:
a refresh is scheduled, not immediate, and when it happens it visits the views
that were marked, not the whole building. Reading a value straight after
writing it does not show you the DOM after the update.

And the caretaker analogy suggests the bell is new. It is not the only
notification: `ChangeDetectorRef.markForCheck` predates all of this and is
still one of the things that marks a view, which is what lets code that is not
signal-based keep working.

## How it works

Angular acts on a fixed set of notifications: `ChangeDetectorRef.markForCheck`
(called automatically by `AsyncPipe`), `ComponentRef.setInput`, updating a
signal that is read in a template, bound host or template listener callbacks,
and attaching a view that was marked dirty by one of those.

Nothing else is on that list — not a `setTimeout`, not a promise resolving, not
a third-party library's callback.

```ts
private readonly timer = setInterval(() => {
  this.plainTicks++;                       // changes, schedules nothing
  this.ticks.update((value) => value + 1); // marks the view that reads it
}, 1000);
```

The second half of the picture is which views get visited. "OnPush is the
default change detection strategy in Angular (since v22)", and it means a
subtree is checked only when its root receives new inputs from a template
binding, or when an event is handled inside it. This application still writes
`changeDetection: ChangeDetectionStrategy.OnPush` on every component; in
Angular 22 that is a statement of intent rather than a change of behaviour.

For a value that arrives from an API you do not own, the escape hatch is
explicit.

```ts
this.message = message;
this.changeDetector.markForCheck();
```

That is the shape to reach for when wrapping the value in a signal is not
possible. Everywhere else, the signal is the notification, and there is nothing
to remember.

## Common mistakes

**Assuming a plain field updates the screen.** The first listing increments a
field and a signal in the same timer callback. Both numbers advance in memory;
only the signal's view is scheduled for refresh, so the two lines on screen
drift apart while the object holding them stays consistent.

**Reading the DOM immediately after a write.** The refresh is scheduled. A test
or a measurement taken on the line after `set` sees the previous DOM, which is
why component tests trigger change detection explicitly before asserting.

**Adding zone.js back to fix a symptom.** A view that does not update has a
missing notification, and the notification is what to find. Reintroducing the
zone hides one bug and reinstates the cost the default removed.

**Mutating an object held in a signal and expecting a refresh.** The
notification comes from the signal being set, not from the object changing;
with the default equality, an in-place mutation notifies nothing.

## Check yourself

<details><summary>A signal is written every second and no template reads it. What renders?</summary>
Nothing. The write marks the views that read the signal, and there are none.
</details>

<details><summary>Which of these schedules a refresh: a resolved promise, a click handler, <code>markForCheck</code>?</summary>
The click handler and <code>markForCheck</code>. A promise resolving is not on
the notification list; whatever the callback does may be.
</details>

<details><summary>Why does <code>ComponentRef.setInput</code> appear on the list?</summary>
Because an input set from outside a template has no binding to mark the view.
The method does it, which is what makes it usable in tests of an OnPush
component.
</details>

## Full listings

1. A plain field and a signal incremented in the same timer callback.
2. A bootstrap configuration that says nothing about change detection, and the explicit escape hatch.
