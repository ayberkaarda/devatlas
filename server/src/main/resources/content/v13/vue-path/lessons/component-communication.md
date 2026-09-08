## Why this exists

Two components need to agree about something, and there are more ways to arrange
that than there are good ones. Vue 3.5 has a default arrangement and it is worth
knowing why it is the default before reaching past it.

The props guide states the rule: "All props form a one-way-down binding between
the child property and the parent one: when the parent property updates, it will
flow down to the child, but not the other way around. This prevents child
components from accidentally mutating the parent's state, which can make your
app's data flow harder to understand." One direction for data, one direction for
requests. Every shortcut around that trades a moment's convenience for a change
whose origin nobody can find later.

## The idea

Props and events are a restaurant order slip. The kitchen receives a written
order and cooks what it says. It does not amend the slip — if the fish is off,
it sends a note back to the waiter, and the waiter decides what to do. Data goes
in on paper; requests come back as notes.

### Where the analogy breaks

An order slip is a copy. An object prop is not. The guide is explicit: "while
the child component cannot mutate the prop binding, it will be able to mutate the
object or array's nested properties ... because in JavaScript objects and arrays
are passed by reference, and it is unreasonably expensive for Vue to prevent
such mutations". The kitchen cannot rewrite the slip, but if the slip names a
bag the customer is still holding, the kitchen can reach into the bag. The
second listing does exactly that: the assignment is refused and the nested write
succeeds, and only one of the two shows up in the parent's own record of what it
agreed to.

The analogy leaks a second time on distance. A note reaches the waiter who took
the order and stops there. "Unlike native DOM events, component emitted events
do not bubble. You can only listen to the events emitted by a direct child
component." A grandparent hears nothing unless the parent chooses to pass the
note along.

## How it works

A child declares what it accepts and what it emits; a parent supplies the first
and listens for the second.

```vue
<script setup>
const props = defineProps({ label: String, count: Number })
const emit = defineEmits({
  'update:count': (value) => Number.isInteger(value)
})
</script>

<template>
  <button @click="emit('update:count', props.count + 1)">
    {{ label }}: {{ count }}
  </button>
</template>
```

Declaring the events is optional and the guide recommends it anyway: it
documents the component, and "it also allows Vue to exclude known listeners from
fallthrough attributes, avoiding edge cases caused by DOM events manually
dispatched by 3rd party code". With the object form, each entry is a validator —
a function that receives the payload and returns whether it is acceptable.

The parent side is the other half of the same contract: it supplies the data and
owns the change.

```vue
<template>
  <QuantityStepper
    label="Apples"
    :count="count"
    @update:count="count = $event"
  />
</template>
```

The first listing runs a full round trip and records who applied each change:

```
after the child emitted, parent state: 1
the parent applied the change itself: true
the child never held the count: true
```

Vue 3.5 also changed what destructuring `defineProps` gives you, and the props
guide states the boundary precisely: "In version 3.4 and below, `foo` is an
actual constant and will never change. In version 3.5 and above, Vue's compiler
automatically prepends `props.` when code in the same `<script setup>` block
accesses variables destructured from `defineProps`." That removes a workaround,
not the rule — the values still flow one way, and watching one still needs a
getter.

## Common mistakes

**Assigning to a prop.** "You should not attempt to mutate a prop inside a child
component. If you do, Vue will warn you in the console." The assignment does not
take, and the next parent update would overwrite it anyway. If the child needs
its own copy, seed a `ref` from the prop; if it needs a transformed version,
derive it with a computed.

**Mutating an object prop's contents instead.** The write lands, the parent's
state changes, and nothing in the parent records that it happened. The listing's
audit log shows the difference:

```
the prop binding resisted assignment: true
the nested object did not: true
the parent recorded the nested change: false
the parent recorded the emitted change: true
```

**Listening for a grandchild's event on the grandparent.** The listener is never
called, and there is no error to read. The third listing shows the same emit
reaching one level and stopping; adding a re-emit in the middle is what carries
it up.

**Reaching for a store because two components are three levels apart.** The
guide points at "an external event bus or a global state management solution"
for genuinely distant components — the point is that distance, not annoyance,
is what justifies it.

## Check yourself

<details><summary>A child needs an editable copy of a prop. What should it do?</summary>
Initialise a local <code>ref</code> from the prop's value, understanding that
the local copy is then disconnected from later parent updates. If it must stay
in step, derive it with a computed instead.
</details>

<details><summary>Why does mutating <code>props.user.name</code> work when <code>props.user = …</code> does not?</summary>
The binding is read-only; the object it points at is not. Preventing deep
mutation would mean wrapping every nested object, which Vue does not do.
</details>

<details><summary>Your grandparent's listener never fires. What is missing?</summary>
A re-emit in the component between them. Emitted events reach the direct parent
only.
</details>

## Full listings

1. Props down and events up, with the parent applying every change.
2. The read-only binding and the object behind it.
3. Three levels, one emit, and the hop that has to be written by hand.
