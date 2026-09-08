## Why this exists

A component is a class and a template, and the interesting failures live
between them: the branch that renders nothing, the alert that appears when it
should not, the input that never arrived. Testing that by opening a browser is
slow, needs a machine with a display, and gives an answer that depends on
timing. Angular 22 ships `TestBed`, which builds the component and its injector
in the test process, and this application runs its component tests under a
plain test runner with no browser at all. What the tests then assert is the
second half of the lesson, and the half people get wrong.

## The idea

`TestBed` is a stage set. The component is the real actor — the same class,
compiled the same way — and everything around it is scenery you built: a fake
gateway instead of a server, a fake platform instead of a desktop. You decide
what is on stage, and nothing that is not on stage can affect the performance.

### Where the analogy breaks

Scenery is decoration and an injector is not. `TestBed.configureTestingModule`
builds a real dependency injection container, so a provider you left out is not
a missing backdrop; it is a component that cannot be constructed, and the test
fails at creation rather than at the assertion you were writing.

The second leak is that nobody is watching, and nothing happens on its own. In
a zoneless application no refresh is scheduled by a test's own writes reaching
the DOM at some later moment of their choosing; the test advances the scene by
calling `detectChanges`, and an assertion made before that call reads the
previous render.

Third, an actor improvises and a component under test must not. Anything
non-deterministic — a timer, a request, a clock — is exactly what the scenery
replaces, and a test that leaves one in place is a test that will fail on a
slow machine for reasons that have nothing to do with the component.

## How it works

`TestBed.createComponent()` "creates an instance of the `Banner` component,
adds a corresponding element to the test-runner DOM, and returns a
`ComponentFixture`". The fixture carries the instance, the element and the
means to render.

```ts
TestBed.configureTestingModule({
  providers: [{ provide: QueueGateway, useValue: new FakeQueueGateway(rows) }],
});
const fixture = TestBed.createComponent(QueuePanel);
fixture.componentRef.setInput('heading', 'Downloads');
fixture.detectChanges();
```

Inputs are set through `componentRef.setInput`, which "will properly mark for
check component using the `OnPush` change detection strategy" — the strategy
every component has by default in Angular 22.

Then the assertions. `fixture.nativeElement` is the host element, and it "has
the `any` type", being a convenience for `fixture.debugElement.nativeElement`.
What you look for through it decides how long the test survives.

```ts
const rows = host.querySelectorAll('[data-testid="queue-entry"]');
expect(rows).toHaveLength(2);
```

That assertion says "one row per entry". An assertion quoting the row's label
says "one row per entry, and the label is exactly this sentence, in English".
The third listing runs both against markup before and after a wording change
and prints `after: by test id true, by text false` — the text assertion fails
for a change that broke nothing.

Roles and ARIA attributes are structure too, and they are worth asserting for
the same reason: `role="alert"` is a promise to a screen reader, and a test
that checks it is checking behaviour rather than decoration.

## Common mistakes

**Assigning to the input property.** `fixture.componentInstance.heading = 'x'`
fails with `error TS2540: Cannot assign to 'heading' because it is a read-only
property.` A signal input is written from outside through `setInput`.

**Asserting before rendering.** Without `detectChanges` after the state
changed, the DOM is the previous one and the failure message describes an empty
element rather than a stale one.

**Quoting visible text.** The assertion breaks when a word changes and when the
locale changes, and it passes while the element is in the wrong place, hidden,
or missing its role.

**Providing the real collaborator.** A test that reaches a network is a test
whose result depends on the network. The double is supplied in exactly the way
the real one would be, so nothing about the component changes for testing.

## Check yourself

<details><summary>Why is <code>setInput</code> preferred over assigning the property?</summary>
Because the assignment does not compile for a signal input, and because the
method marks the view for check, which an assignment could not do.
</details>

<details><summary>Why does a test call <code>detectChanges</code> at all?</summary>
Because rendering is scheduled, not immediate. The call is what turns the
current state into DOM at a point the test controls.
</details>

<details><summary>What does a test id give you that a label does not?</summary>
Independence from wording and from language. It names the element as the
component's own contract with its tests rather than as a sentence a translator
may change.
</details>

## Full listings

1. The component under test, with an injected collaborator and named elements.
2. The test: a fake gateway, an input set through the fixture, three assertions over structure.
3. Finding an element by test id and by text, before and after a wording change.
