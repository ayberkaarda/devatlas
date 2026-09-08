// Two test runners over the same three tests. They differ in one line: whether
// the value the test function returned is awaited.

'use strict';

const assert = require('node:assert/strict');

// A rejected promise that nothing awaits would end the process. Collecting them
// here is what lets the broken runner finish and print its verdict.
const dropped = [];
// The AssertionError's own wording changes between releases, so its documented
// fields are recorded rather than its message.
process.on('unhandledRejection', (reason) => {
  dropped.push(`${reason.code}: ${reason.actual} ${reason.operator} ${reason.expected}`);
});

async function fetchTotal() {
  await null; // stands in for I/O
  return 5;
}

const tests = [
  ['synchronous, correct', () => assert.equal(2 + 2, 4)],
  ['synchronous, wrong', () => assert.equal(2 + 2, 5)],
  ['asynchronous, wrong', async () => assert.equal(await fetchTotal(), 6)]
];

function runIgnoringTheReturnValue() {
  const lines = [];
  for (const [name, body] of tests) {
    try {
      body();
      lines.push(`ok   ${name}`);
    } catch {
      lines.push(`FAIL ${name}`);
    }
  }
  return lines;
}

async function runAwaitingTheReturnValue() {
  const lines = [];
  for (const [name, body] of tests) {
    try {
      await body();
      lines.push(`ok   ${name}`);
    } catch {
      lines.push(`FAIL ${name}`);
    }
  }
  return lines;
}

async function main() {
  const ignored = runIgnoringTheReturnValue();
  console.log('runner that ignores the return value:');
  for (const line of ignored) console.log(`  ${line}`);
  console.log(`  failures reported: ${ignored.filter((l) => l.startsWith('FAIL')).length}`);

  const awaited = await runAwaitingTheReturnValue();
  console.log('runner that awaits the return value:');
  for (const line of awaited) console.log(`  ${line}`);
  console.log(`  failures reported: ${awaited.filter((l) => l.startsWith('FAIL')).length}`);

  await new Promise((resolve) => setImmediate(resolve));
  console.log(`assertion failures the broken runner let escape: ${dropped.length}`);
  console.log(`the escaped failure was: ${dropped[0]}`);
}

main();
