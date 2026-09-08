## Why this exists

An Angular 22 application is assembled from components, and a component is two
things joined by a rule. There is a TypeScript class that holds state and
behaviour, and there is a template that describes what the browser should show.
The rule is that the template reads the class, and the class never reaches into
the elements the template produced. Most people arrive with the opposite
instinct — find the element, change its text — and that instinct produces code
that is right once and then drifts, because the page and the data behind it are
now two separate records of the same fact. Learning where the boundary sits is
cheaper before you write the first screen than after.

## The idea

A component is a kitchen and a menu. The class is the kitchen: it holds the
ingredients, decides what can be made, and knows nothing about how a dish will
be described to anyone. The template is the menu: it names what is on offer,
and a customer reads it rather than walking in to look at the pans. When the
kitchen runs out of something it does not send somebody out with a pen to cross
a line off the menu. It changes what it has, and the menu says what the kitchen
holds.

### Where the analogy breaks

A menu is a document, printed once. A template is not. It is compiled into the
component's definition, and its expressions are evaluated again whenever the
data behind them changes — closer to a function of the class than to a printed
page.

The second leak matters more. A menu can promise a dish the kitchen cannot
make; the two are only loosely coupled. A template cannot. Angular type-checks
a template against its own component class, so a name the template uses and the
class does not declare fails the build rather than rendering as a blank. And a
menu is read-only, while a template hands work back: an event binding calls a
method on the class, so traffic runs in both directions across the boundary.

## How it works

The `@Component` decorator attaches a template to a class and gives it a CSS
selector. Angular's own description of the parts is short: every component has
"a TypeScript class with behaviors", "an HTML template that controls what
renders into the DOM", and "a CSS selector that defines how the component is
used in HTML".

```ts
@Component({
  selector: 'app-notice',
  template: `<p>{{ message }}</p>`,
})
export class Notice {
  protected readonly message = 'Your progress is saved on this device.';
}
```

Components are standalone by default from Angular 19.0.0 onward; before that
release the `standalone` option defaulted to `false`. A standalone component
lists what its template needs in `imports`, so the dependencies of a screen are
readable in the file that renders the screen.

```ts
@Component({
  selector: 'app-settings-panel',
  imports: [Notice],
  template: `<app-notice />`,
})
export class SettingsPanel {}
```

The decorator's argument is a typed object rather than a free-form bag, which
is why the boundary is enforced at compile time in both directions: the
metadata is checked against the `Component` type, and the template is checked
against the class.

## Common mistakes

**Misspelling a metadata key and expecting it to be ignored.** Writing
`templateUrls` for `templateUrl` does not silently fall back to a default; the
compiler reports `error TS2561: Object literal may only specify known
properties, but 'templateUrls' does not exist in type 'Component'. Did you mean
to write 'templateUrl'?`

**Marking a member `private` and then naming it in the template.** The
compiled template code resolves names against the class, so a private member
is not reachable from it. Class members a template reads are `protected` — the
convention used throughout this application — or public.

**Using a component without importing it.** A standalone component whose tag
appears in another component's template but not in that component's `imports`
array is not resolved as a component. Angular reports it as an unknown element
rather than rendering anything, because nothing in that template's scope claims
the selector.

## Check yourself

<details><summary>Why can a template not read a <code>private</code> field?</summary>
Because the template is compiled into code that resolves names against the
component class rather than into a closure that shares the class body's scope.
A member the template reads is <code>protected</code> or public.
</details>

<details><summary>What does the <code>imports</code> array on a component decide?</summary>
Which components, directives and pipes that component's own template may use.
It is per component rather than per application, so the dependencies of a
screen are visible in the file that renders it.
</details>

<details><summary>Where does the traffic across the boundary run both ways?</summary>
An event binding. The template reads the class for its interpolations, and an
event binding calls back into a class method, which is how a click changes
state that the template then re-reads.
</details>

## Full listings

1. A leaf component and a component that uses it through `imports`.
2. A misspelled metadata key, kept next to the correct version.
