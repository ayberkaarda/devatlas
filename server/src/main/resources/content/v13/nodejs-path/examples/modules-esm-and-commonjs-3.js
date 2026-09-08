// The interop, in both directions, on modules written to a temporary directory
// so that this file needs no companion in the repository. No path is printed:
// a temporary directory name is different on every machine and every run.

'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nodejs-path-'));

const staticCjs = path.join(dir, 'static-exports.cjs');
fs.writeFileSync(
  staticCjs,
  "exports.counter = 0;\n" +
    "exports.increment = function increment() { exports.counter += 1; };\n"
);

const dynamicCjs = path.join(dir, 'dynamic-exports.cjs');
fs.writeFileSync(
  dynamicCjs,
  "function build() { return { label: 'built at run time' }; }\n" + 'module.exports = build();\n'
);

const esm = path.join(dir, 'greeting.mjs');
fs.writeFileSync(esm, "export const greeting = 'hello from an ES module';\n");

async function main() {
  // CommonJS: destructuring copies the value, the namespace object does not.
  const staticModule = require(staticCjs);
  const { counter } = staticModule;
  staticModule.increment();
  console.log(`destructured CommonJS copy: ${counter}`);
  console.log(`property read through the module object: ${staticModule.counter}`);

  // ESM importing CommonJS: default is module.exports, and named exports are
  // recovered by static analysis of the source.
  const staticNs = await import(pathToFileURL(staticCjs).href);
  console.log(`default is the module.exports object: ${staticNs.default === staticModule}`);
  console.log(`named exports detected: ${Object.keys(staticNs).sort().join(', ')}`);

  // When module.exports is built by a call, static analysis finds nothing, so
  // only default is available and `import { label }` would fail to link.
  const dynamicNs = await import(pathToFileURL(dynamicCjs).href);
  console.log(`dynamic module exported names: ${Object.keys(dynamicNs).sort().join(', ')}`);
  console.log(`its value is reachable through default: ${dynamicNs.default.label}`);

  // CommonJS requiring an ES module: supported in Node.js 22 for a graph with
  // no top-level await. The result is the module namespace object.
  const required = require(esm);
  console.log(`require of an .mjs file returned a namespace: ${required[Symbol.toStringTag]}`);
  console.log(`its named export: ${required.greeting}`);

  fs.rmSync(dir, { recursive: true, force: true });
}

main();
