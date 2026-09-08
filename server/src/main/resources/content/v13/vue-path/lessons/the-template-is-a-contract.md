## Why this exists

Before frameworks, keeping a screen in step with data meant finding the element
that is now wrong and correcting it: set the text, toggle the class, remove the
row. Every one of those edits is a claim about the past — about what the screen
used to show — and the bugs come from claims that disagree with each other.

Vue 3.5 asks for something else. You write a template that says what the screen
looks like for a given state, and the framework works out the edits. The
template is a contract: hand it this state, get that output, with nothing left
over from the previous render.

The contract only holds if a template cannot do anything else. One that could
assign to a variable, call into the page, or read whatever it liked from the
global object would be a program, and a program's output depends on when you ran
it. Vue 3.5 narrows what a template may contain until the contract is
enforceable, and the narrowing is the part worth learning first.

## The idea

A template is a spreadsheet formula, not a recorded macro. A macro is a sequence
of edits: select this cell, type that, move down one. A formula says what the
cell *contains* — `=B2*C2` — and the sheet is responsible for keeping that true.
You never tell a spreadsheet to update a cell.

### Where the analogy breaks

A spreadsheet formula can name any cell in the workbook. A Vue 3.5 template
expression cannot reach nearly so far. The template syntax documentation states
that "template expressions are sandboxed and only have access to a restricted
list of globals", that the list "exposes commonly used built-in globals such as
`Math` and `Date`", and that globals "not explicitly included in the list, for
example user-attached properties on `window`, will not be accessible in template
expressions". A formula's reach is the whole workbook; a template's reach is the
component's own state plus a short list somebody else decided.

The analogy leaks a second time, and this leak costs people an afternoon. A
spreadsheet cell holds one value. A template produces a tree, and the same
string put in two different positions is treated in two different ways. One of
those positions escapes what you give it and one does not.

## How it works

All Vue templates "are syntactically valid HTML that can be parsed by
spec-compliant browsers and HTML parsers", and Vue "compiles the templates into
highly-optimized JavaScript code". Compilation happens once; rendering is that
compiled function applied to state.

```vue
<script setup>
import { ref } from 'vue'

const user = ref({ name: 'Ada' })
const items = ref([1, 2, 3])
</script>

<template>
  <p>Hello, {{ user.name }} — {{ items.length }} items</p>
</template>
```

Between the mustaches goes an expression, and only an expression. The
documentation is exact: "Each binding can only contain one single expression",
and "statements like variable declarations or flow control will NOT work". Both
refusals belong to the JavaScript parser, which is why they arrive before
anything renders. The second listing models the rule and the sandbox together,
and prints what each attempt did:

```
{{ number + 1 }} -> evaluated to 2
{{ var a = 1 }} -> rejected with SyntaxError
{{ if (ok) { return message } }} -> rejected with SyntaxError
{{ appName }} -> rejected with ReferenceError
```

`appName` in that run was a real property on the global object. Nothing exposed
it to the template, so nothing could read it.

The two positions that treat a string differently sit one line apart:

```vue
<template>
  <p>{{ comment.body }}</p>
  <p v-html="comment.body"></p>
</template>
```

## Common mistakes

**Writing a statement where an expression belongs.** `{{ if (loggedIn) { … } }}`
is a `SyntaxError`; the documentation's own advice is to "use ternary
expressions" instead, or move the branch into `v-if`.

**Expecting `{{ }}` to produce markup.** The double mustaches "interpret the
data as plain text, not HTML". A stored `<img …>` renders as visible angle
brackets, which looks like a bug and is the escape doing its job.

**Reaching for `v-html` to make that go away.** The documentation's warning is
blunt: rendering arbitrary HTML "can easily lead to XSS vulnerabilities. Only
use `v-html` on trusted content and **never** on user-provided content." The
third listing puts the same hostile string through both paths; only one of them
produces a tag.

**Assuming a global is in scope.** A helper hung off `window` is not reachable
from a template expression. Expose it deliberately through
`app.config.globalProperties`, or pass it in as state.

## Check yourself

<details><summary>Why can a template not contain an <code>if</code> statement?</summary>
Because a binding holds one expression, and a statement is not an expression.
The parser refuses it before the component renders, so the failure is a build or
compile error rather than a wrong screen.
</details>

<details><summary>The same string renders as text in one place and as a tag in another. Why?</summary>
Text interpolation escapes; <code>v-html</code> does not. The value did not
change — the position it was put in did.
</details>

<details><summary>A helper on <code>window</code> works in the console but not in the template. What is wrong?</summary>
Nothing is wrong. Template expressions see a restricted list of globals plus the
component's own state. Attach it to <code>app.config.globalProperties</code> or
pass it as data.
</details>

## Full listings

1. Compiling a template once, rendering it many times.
2. Expressions only, and the sandbox around them.
3. Text interpolation and raw HTML, given the same hostile string.
