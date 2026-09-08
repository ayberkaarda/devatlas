## Why this exists

Node.js 22 supports two module systems at once. CommonJS came first and is what a `.js` file is
treated as when no `package.json` says otherwise; ES modules are the standard the language
itself defines. Most working programs contain both, because a dependency chose one and the
application chose the other. The interop between them works, and it works with rules that are
invisible until one of them refuses: an import that resolves to `undefined`, a `require` that throws,
a destructured value that never updates. Every one of those is explainable, and none of them is
explainable from the syntax alone.

## The idea

A CommonJS `require` is a photocopy handed over a counter: you ask for the module, the counter
runs it once, and you get the contents of `module.exports` as it stood at that moment. An `import`
is a window cut into the other room: you look through it and see the variable as it is now.

### Where the analogy breaks

A photocopy sounds like a deep copy and is not. `require` returns the same object every time, so
mutations any holder makes are seen by every other holder; what is copied is the reference, and
only when you destructure a value out of it.

The window is not a door either. A module namespace object is sealed: you cannot add an export,
and assigning to one throws a `TypeError` in strict mode, which is the only mode ES modules have.
You can look through, you cannot reach through.

The two also disagree about time. A photocopy is handed over immediately — `require` is
synchronous. A window has to be cut before you can look — `import()` returns a promise, and the
module graph is resolved and linked before any of its code runs.

## How it works

A CommonJS file is wrapped in a function that receives `exports`, `require`, `module`, `__filename`
and `__dirname`. `exports` starts out as another name for `module.exports`, and the moment you
assign to `exports` itself the two part company and nothing after that is exported.

```js
exports.a = 1;                 // exported
exports = { b: 2 };            // local rebinding; b is not exported
module.exports = { c: 3 };     // this is what replaces the exported value
```

ES modules export bindings rather than values. The namespace object reads the variable each time,
so an exported `let` that the module later changes is seen as changed by everyone importing it.
Take a copy out of the namespace and the copy stops tracking, which is the CommonJS behaviour
people expect and then miss.

Across the boundary the rules are: importing CommonJS gives you `module.exports` as the default
export, with named exports recovered by static analysis of the source where that is possible. When
`module.exports` is built by a function call rather than assigned property by property, the analysis
finds nothing and only `default` is available.

```js
import pkg from 'some-cjs-package';   // always works
import { thing } from 'some-cjs-package';  // only if analysis found `thing`
```

Going the other way, Node.js 22 lets `require()` load an ES module and returns its namespace
object, provided the graph contains no top-level `await`. The feature is documented at release
candidate stability in this version, so it is worth knowing and worth pinning your Node version if
you depend on it.

## Common mistakes

**Assigning to `exports`.** It exports nothing and reports nothing. Assign to `module.exports`.

**Destructuring a counter or a flag out of a module and expecting it to change.** From CommonJS
you took a copy of a value; from an ES module you took a copy of a binding's current value. Read
through the module object or the namespace instead.

**Expecting a named import from every CommonJS package.** If the package builds its exports at
run time, `import { thing }` fails to link. Import the default and destructure from it.

**Assuming `__dirname` exists everywhere.** It is part of the CommonJS wrapper and absent in ES
modules, where the equivalent comes from `import.meta.url`.

## Check yourself

<details><summary>Why does <code>exports = {...}</code> silently export nothing?</summary>

`exports` is a parameter of the module wrapper function. Assigning to it rebinds the parameter and
leaves `module.exports`, which is what the loader reads, untouched.

</details>

<details><summary>Why can a named import from a CommonJS package fail while the default import works?</summary>

The default is always `module.exports`. Named exports are reconstructed by reading the source, and
a module that assembles its exports at run time gives that analysis nothing to find.

</details>

<details><summary>You import a counter and it never seems to change. What did you do?</summary>

Destructured it. That copies the value at import time. Keep the namespace or the module object
and read the property when you need it.

</details>

## Listings

1. `modules-esm-and-commonjs-1.js` — the CommonJS wrapper, and the assignment that exports
   nothing.
2. `modules-esm-and-commonjs-2.js` — an ES module loaded with `import()`, its sealed namespace
   and its live bindings.
3. `modules-esm-and-commonjs-3.js` — the interop in both directions, including the module whose
   named exports cannot be found.
