## Why this exists

Vue 3.5 offers two ways to declare state, and a reader's first instinct is that
one of them must be the modern one. That is not what the difference is. The two
containers are built differently, and the way each is built decides which
ordinary JavaScript moves quietly break it. Choosing without knowing that is how
a component ends up with a value that is correct in the debugger and stale on
the screen.

The reactivity fundamentals guide names `ref()` as "the recommended way to
declare reactive state" in the Composition API, and introduces the other with:
"Unlike a ref which wraps the inner value in a special object, `reactive()`
makes an object itself reactive." That sentence is the whole lesson. One holds
your value; the other stands in front of it.

## The idea

A `ref` is a numbered locker. You are given the locker, not the contents. To
read, you open it — that is what `.value` is — and to replace the contents, you
put something else in the same locker. Whoever you handed the locker to still
has the right locker.

`reactive()` is a receptionist placed in front of an office you already own.
Every request for a room goes through the desk, so the desk can write down who
asked for what. The office is unchanged; the desk is new.

### Where the analogy breaks

A receptionist notices you leaving. Vue's does not. Vue 3.5 tracks by
intercepting property access, so once a value has left through the desk it is an
ordinary value with no connection back. The reactivity-in-depth guide puts it
this way: "When you assign or destructure a reactive object's property to a
local variable, accessing or assigning to that variable is non-reactive because
it no longer triggers the get / set proxy traps on the source object."

There is a second leak, and it is the one that produces silent bugs. A
receptionist can sit in front of anything. `reactive()` cannot: the guide lists
"Limited value types" first among its limitations — "it only works for object
types (objects, arrays, and collection types such as `Map` and `Set`). It cannot
hold primitive types such as `string`, `number` or `boolean`." Hand it a number
and there is nowhere to put a desk.

## How it works

`ref()` is a getter and a setter over a captured variable; `reactive()` is a
`Proxy`. That is not an analogy — the reactivity-in-depth guide gives it as
pseudo-code, and the first listing runs that pseudo-code so both can be watched.

```js
import { ref, reactive } from 'vue'

const count = ref(0)          // a locker: count.value
const state = reactive({ temperature: 20 })  // a desk in front of an object

count.value++
state.temperature++
```

A ref is not shallow by default. The same guide: "A ref will make its value
deeply reactive. This means you can expect changes to be detected even when you
mutate nested objects or arrays." Opting out is a separate call, `shallowRef`,
where "only `.value` access is tracked".

The two moves that break a `reactive` object, and the two that do not:

```js
import { reactive, toRefs, ref } from 'vue'

const state = reactive({ temperature: 20 })

const { temperature } = state          // a number; nothing is watching it
const { temperature: live } = toRefs(state)  // a ref onto the same property

const held = ref({ temperature: 20 })
held.value = { temperature: 21 }       // replacement a subscriber survives
```

Three properties from the first listing, all of them consequences of the shapes
above rather than of policy:

```
a ref can hold a primitive: true
reactive() given a number hands it back unwrapped: true
a ref is deeply reactive: true
```

## Common mistakes

**Destructuring a reactive object and wondering why the screen froze.** The
second listing measures it: after `const { temperature } = state`, an effect
reading `temperature` never runs again, while an effect reading
`state.temperature` runs on every change. The copy is a number. Nothing is
watching a number.

Note the half that is not broken. Destructure an *object* property and mutation
still works, because "if the variable points to a non-primitive value such as an
object, mutating the object would still be reactive". A rule remembered as
"destructuring breaks reactivity" will mislead you in both directions.

**Replacing a reactive object by rebinding the variable.** The guide: "we can't
easily 'replace' a reactive object because the reactivity connection to the
first reference is lost." The third listing rebinds and then mutates both
objects; only the original wakes the effect. A ref has one property to replace,
so replacement works there.

**Comparing a reactive object with its source.** `reactive(raw) === raw` is
`false` — the proxy "has a different identity if we compare it to the original
using the `===` operator". An identity check against the raw object fails while
every property still reads through correctly.

## Check yourself

<details><summary>Why does <code>reactive(0)</code> give you nothing useful?</summary>
Because tracking works by intercepting property access, and a number has no
properties to intercept. Use a ref, which owns a <code>value</code> property of
its own.
</details>

<details><summary>You destructured <code>{ user }</code> from a reactive object and mutating <code>user.name</code> still updates the screen. Is that luck?</summary>
No. The binding was copied but it points at an object, and reads and writes on
that object still go through a proxy. Only the primitive case loses the
connection.
</details>

<details><summary>A server sends a whole new settings object. Where should it go?</summary>
Into a ref's <code>.value</code>, or property by property into the existing
reactive object. Rebinding the variable that held the proxy leaves every
subscriber attached to the object you abandoned.
</details>

## Full listings

1. The documented pseudo-code for both containers, made runnable.
2. Destructuring: what the copy keeps and what it drops.
3. Replacement, and the identity that is not the raw object.
