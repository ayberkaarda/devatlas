## Why this exists

A test suite that goes red every time somebody renames a CSS class is worse than
no suite at all: it costs the same to maintain and teaches the team that red
means "someone touched the file". The cause is almost never carelessness. It is
that the easiest handle to grab in a test is the one the implementation happens
to expose — a class name, a child index, a state variable — and every one of
those is a detail the component is free to change without changing anything a
reader can perceive.

## The idea

A test is a fire drill. What it establishes is that the building empties, not
that anyone used the north stairwell. Move a door, add a corridor, repaint the
signage: the drill still passes, because the thing it measured was the outcome.
A drill written as "everyone reaches the car park via the north stairwell" fails
the day the stairwell is renovated, and it fails while the building is
demonstrably still safe.

### Where the analogy breaks

A drill happens in the actual building with actual people. A component test runs
in a simulated document with no layout and no paint, so a whole class of things
a reader perceives — that the button is on screen, that it is not covered, that
the contrast is legible — is not observable at all. The green tick is narrower
than "a user could do this", and a suite that forgets the difference grows
confident about the wrong things.

The second break: a drill is watched by a person exercising judgement, who can
see that the evacuation worked. A test needs the definition written down and
mechanical. Choosing that definition — the control's role and its accessible
name rather than its class — is not a detail of the exercise, it is the whole
skill the lesson is about.

## How it works

Find elements the way the reader identifies them, not the way the file happens
to be arranged.

```js
container.querySelector('[role="status"]');              // what the reader has
container.querySelector(".ProductPanel_body__7c2e > button"); // what the file has
```

The first listing holds a
rendered panel, applies a refactor that renames every class and wraps the
content in a layout element, and asks three questions of both versions. Asking
for the control with role `button` named "Add to cart" reports `true` before and
after. Asking for the class `btn-primary` reports `true` then `false`. Asking
for the second child of the root reports `true` then `false`. Nothing a reader
could notice changed.

React 19's own release notes make this argument about React's testing tools
rather than yours. The `react-dom/test-utils` helpers other than `act` were
removed because they "made it too easy to depend on low level implementation
details of your components and React", and `react-test-renderer` is deprecated
because it "promotes testing implementation details, and relies on introspection
of React's internals".

The one piece of machinery a behavioural test cannot do without is the flush
boundary. Clicking queues an update; React applies the queue and renders; only
then does the output say anything new. `act` closes that gap, and React 19
imports it from `react` itself.

```js
await act(async () => {
  button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
});
```

The reference recommends the awaited form, because "the sync version works in
many cases, it doesn't work in all cases". It also requires
`global.IS_REACT_ACT_ENVIRONMENT = true`, and notes that testing libraries set
it for you.

The second listing models the gap without React: a click that queues an updater,
an assertion made before the queue is applied, and the same assertion after. It
reports `without a flush, the screen says: clicked 0 times` and then
`after a flush, … clicked 1 times`, and finishes with the failure mode worth
recognising — an assertion that passes both before and after the click, and only
starts telling the truth once the queue is applied.

## Common mistakes

**Querying by class or by position.** Measured above: both break on a refactor
that changes nothing observable.

**Asserting on state rather than output.** The component's internals are not
what the reader has; a rename of the variable is a red suite and a working
screen.

**Interacting without a flush boundary.** The assertion reads the previous
frame. Worse, an assertion about the *starting* value passes either way, so the
test is green and proves nothing.

**Mocking the component under test.** Every seam replaced is a claim the suite
stops checking.

## Check yourself

<details><summary>Why is a query by role and accessible name better than one by class?</summary>
Because the role and the accessible name are what a reader — including one using
a screen reader — has to work with, and a restyle does not change them. The
listing finds the button before and after a refactor that renames every class.
</details>

<details><summary>What does a passing component test not prove?</summary>
Anything about layout, paint, or real input. The document is simulated, so
visibility, overlap and contrast are outside what the assertion saw.
</details>

<details><summary>A test asserts the count is 0 and passes, but the click never registered. How would you notice?</summary>
Assert the value after the interaction, not only before it. The second listing
shows the assertion passing on both sides of an unflushed click, which is the
signature of a test that never observed anything.
</details>

## Full listings

1. Two queries over the same refactor: role and name, class, and position.
2. The flush boundary, and the assertion that passed for the wrong reason.
3. A React 19 test with `act` and `createRoot` — written, not executed.
