## Why this exists

Two components need the same behaviour: the same fetch with the same loading
flag, the same debounce, the same pagination arithmetic. Copying it is the
obvious move and the one that quietly diverges. Moving it into a helper module
does not work either, because the thing being shared is not a calculation — it
is state that changes over time, plus the code that changes it.

Vue 3.5's guide gives that arrangement a name: "A composable is a function that
leverages Vue's Composition API to encapsulate and reuse stateful logic." The
important word is stateful. A composable is not a utility function; it is a
piece of a component that happens to live in its own file.

## The idea

A composable is a recipe, and each call is a separate batch. The recipe is
written once; every kitchen that follows it ends up with its own pot, its own
ingredients and its own result. The guide says the same thing without the pots:
"each component instance calling `useMouse()` will create its own copies of `x`
and `y` state so they won't interfere with each other".

### Where the analogy breaks

A recipe leaves nothing behind between batches. A composable can. Move the
`ref` outside the function — up to module scope — and every caller closes over
the same pot. That is sometimes exactly right, and it is never an accident you
want. The first listing runs both shapes side by side: two calls to the isolated
one give two independent counters, two calls to the shared one hand back the
same ref.

The second leak is about when you may cook at all. A recipe can be started at
any hour. A composable that registers lifecycle hooks cannot: they "should only
be called in `<script setup>` or the `setup()` hook", and "should also be called
synchronously in these contexts". Called from an event handler or after an
`await`, the hook has no component instance to attach to and silently does
nothing — which the third listing of the testing lesson demonstrates as a
failing test.

## How it works

A composable is a function whose name starts with `use`, which creates some
state and returns it along with the functions that change it.

```js
import { ref, toValue, watchEffect } from 'vue'

export function usePagination(totalOrRef, perPage = 20) {
  const page = ref(1)
  const pages = ref(1)
  watchEffect(() => {
    pages.value = Math.max(1, Math.ceil(toValue(totalOrRef) / perPage))
    if (page.value > pages.value) page.value = pages.value
  })
  return { page, pages, next: () => (page.value = Math.min(page.value + 1, pages.value)) }
}
```

Two conventions in that signature are worth more than they look. The return
value is "a plain object containing refs ... so that it can be destructured in
components while retaining reactivity"; returning a `reactive` object instead
"will cause destructures to lose the reactivity connection to the state inside
the composable, while the refs will retain that connection". The second listing
measures both, and the repair for the object case:

```
destructured ref, effect runs: 2
destructured reactive property, effect runs: 1
after toRefs, effect runs: 2
```

The call site is where the convention pays: two components, two independent
counters, and a destructure that keeps working.

```vue
<script setup>
const total = ref(0)
const { page, pages, next } = usePagination(total, 20)
</script>
```

The argument is normalised with `toValue`, which the guide recommends for any
composable "that may be used by other developers", so that a caller may pass a
raw value, a ref, or a getter. The difference is not cosmetic:

```
ref argument, titles seen: Page 1 | Page 2 | Page 3
raw argument, titles seen: Page 1
```

## Common mistakes

**Returning a `reactive` object because it looks tidier at the call site.** The
caller destructures it, the connection is lost, and nothing reports the loss.
Return refs, or run the object through `toRefs` first.

**Putting state at module scope by accident.** A `ref` outside the function is
shared by every component in the application, including ones on other pages.
When sharing is what you want, say so in the name.

**Accepting a raw value where callers will pass a ref.** The composable reads
the argument once and never reacts again. `toValue` costs one call and removes
the whole class of bug.

**Calling a composable conditionally or after an `await`.** Anything it
registers — hooks, watchers tied to the instance — needs the active instance
that exists only during synchronous setup.

## Check yourself

<details><summary>Why refs rather than a reactive object?</summary>
Because the call site destructures. Refs survive destructuring; a primitive
property copied out of a reactive object is just a value.
</details>

<details><summary>Two components use the same composable and share state. Bug or design?</summary>
Design only if the state lives outside the function on purpose. If the
<code>ref</code> is created inside, each call gets its own; if it is at module
scope, every call gets the same one.
</details>

<details><summary>What does <code>toValue</code> buy a composable?</summary>
Callers may pass a plain value, a ref or a getter, and the composable keeps
reading through whichever it got — so a changing argument keeps working.
</details>

## Full listings

1. State per call and state per module, counted.
2. Refs versus a reactive object through a destructure, and the repair.
3. `toValue`, and the composable that read its argument once.
