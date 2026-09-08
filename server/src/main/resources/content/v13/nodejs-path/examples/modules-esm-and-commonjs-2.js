// ES modules reached from a CommonJS file. import() is an expression, it works
// in both module systems, and it always produces a promise.
//
// The module source is a data: URL so that this file needs no companion on
// disk; Node.js 22 treats a data:text/javascript URL as an ES module.

'use strict';

const source =
  'data:text/javascript,' +
  encodeURIComponent(`
    export let counter = 0;
    export function increment() { counter += 1; }
    export const label = 'module scope';
    export default 'the default export';
  `);

async function main() {
  const namespace = await import(source);

  console.log(`import() produced a promise: ${import(source) instanceof Promise}`);
  console.log(`named export: ${namespace.label}`);
  console.log(`default export: ${namespace.default}`);
  console.log(`namespace tag: ${namespace[Symbol.toStringTag]}`);

  // A module namespace object is sealed: exports are a fixed list decided when
  // the module was parsed, not properties you can add to later.
  console.log(`namespace is sealed: ${Object.isSealed(namespace)}`);
  console.log(`exported names: ${Object.keys(namespace).join(', ')}`);

  // Exported bindings are live. The namespace reads the variable, it does not
  // hold a copy of the value it had at import time.
  console.log(`counter before increment: ${namespace.counter}`);
  namespace.increment();
  console.log(`counter after increment: ${namespace.counter}`);

  // A copy taken out of the namespace is exactly that: a copy.
  const { counter } = namespace;
  namespace.increment();
  console.log(`destructured copy after a further increment: ${counter}`);
  console.log(`namespace after the same increment: ${namespace.counter}`);

  // The namespace is not writable from outside.
  try {
    namespace.label = 'reassigned';
  } catch (error) {
    console.log(`assigning to a namespace property throws: ${error.constructor.name}`);
  }
}

main();
