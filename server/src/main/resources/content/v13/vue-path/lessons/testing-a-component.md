## Why this exists

A component test is supposed to tell you that a change was safe. Most of them
eventually tell you something else: that a change happened. A suite that fails
every time somebody renames an internal variable has stopped being a safety net
and become a tax, and the usual response — deleting the tests — throws away the
part that worked.

Vue 3.5's testing guide draws the line in one sentence: "Test what a component
does, not how it does it." What it does is visible from outside — the guide
names "events emitted, props, and slots" as the public interface, and asks for
assertions on "correct render output based on inputted props and slots" and on
"correct render updates or emitted events in response to user input events".
What it does *not* want is equally explicit: "Don't assert the private state of a
component instance or test the private methods of a component."

## The idea

Testing a component is reviewing a vending machine. You put a coin in, press a
button, and see what falls out. You do not open the back panel to check that the
coin sits in the third slot before deciding the machine works.

### Where the analogy breaks

A vending machine's interface really is only its buttons. A component's is
wider than what a person sees on screen: the props it accepts, the events it
emits, the slots it renders. An event a user never notices is still public,
because a parent depends on it — so "assert what a user would see" undercounts
the contract by exactly the events.

The analogy leaks a second time on what you can operate. Anyone can walk up to a
machine and use it. Some code cannot be operated in isolation: the guide says
composables "that don't rely on lifecycle hooks or Provide/Inject can be tested
by directly calling it and asserting its returned state/methods", while ones
that do "need to be wrapped in a host component to be tested". Calling those
directly does not fail loudly; it produces a value that never updates.

## How it works

Mount with props, drive it the way a parent or a user would, assert on the
rendered output and on what it emitted. The guide's recommended tools are Vitest
as the runner, with `@vue/test-utils` as the official low-level library or
`@testing-library/vue` for an interface deliberately harder to point at
internals with.

```js
import { mount } from '@vue/test-utils'
import QuantityStepper from './QuantityStepper.vue'

test('emits the next value and never writes its own props', async () => {
  const wrapper = mount(QuantityStepper, { props: { label: 'Apples', count: 2, min: 0 } })
  await wrapper.find('button.increase').trigger('click')
  expect(wrapper.emitted('update:count')).toEqual([[3]])
  expect(wrapper.props('count')).toBe(2)
})
```

The first listing runs that suite against a stand-in harness with the same
shape — props in, `emitted()` out, `setProps` for a parent update — so the
assertions can be watched passing rather than described.

A composable without lifecycle hooks needs none of that ceremony:

```js
test('useCounter counts up', () => {
  const { count, increment } = useCounter()
  increment()
  expect(count.value).toBe(1)
})
```

## Common mistakes

**Asserting on internal state.** The second listing refactors a component so
that its rendered output and emitted events are identical before and after, and
only an internal field disappears. One suite survives; the other does not:

```
rendered output identical across the refactor: true
emitted events identical across the refactor: true
the behaviour suite survived: true
the internals suite did not: true
```

That failure is a false alarm, and false alarms are what teach a team to ignore
a red build.

**Testing a composable with lifecycle hooks by calling it.** The hook has no
instance to attach to, so it never runs and the value under assertion stays at
its initial state:

```
FAIL  useLoadedFlag called directly never runs its hook: loaded was still false
PASS  useLoadedFlag inside a host component does
first warning: onMounted is called when there is no active component instance.
```

**Leaning on snapshots.** The guide's advice is not to "rely exclusively on
snapshot tests", because a snapshot asserts everything equally, including the
whitespace nobody meant to promise.

**Asserting on visible text and nothing else.** It is the right instinct and it
misses the events. A component whose emitted payload is wrong can render
perfectly.

## Check yourself

<details><summary>Which of a component's surfaces belong in a test?</summary>
Props in, rendered output and emitted events out, and slot content. Not fields,
not private methods, not the shape of internal state.
</details>

<details><summary>A refactor that changed no behaviour broke six tests. What does that tell you?</summary>
That the six were asserting on implementation. Rewrite them against rendered
output and emitted events, which the refactor did not change.
</details>

<details><summary>When does a composable need a host component to be tested?</summary>
When it registers lifecycle hooks or uses provide/inject. Without an active
instance those registrations go nowhere, and the test asserts on state that
never advanced.
</details>

## Full listings

1. A component test suite against props, rendered output and emitted events.
2. The same refactor under two suites: one survives, one does not.
3. Composables tested directly, and the one that needed a component around it.
