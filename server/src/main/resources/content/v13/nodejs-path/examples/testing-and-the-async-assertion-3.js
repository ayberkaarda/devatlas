// The runner that ships with Node.js 22, exercised from the outside. Test files
// are written to a temporary directory and run in a child process; only the
// child's exit status is printed, because the reporter's own text carries
// durations and file paths that differ on every machine and every run.

'use strict';

const { spawnSync } = require('node:child_process');
const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

const PASSING = `
  const test = require('node:test');
  const assert = require('node:assert/strict');
  test('an async test that is correct', async () => {
    const total = await Promise.resolve(5);
    assert.equal(total, 5);
  });
`;

// The test function returns a promise, so the runner waits for it.
const RETURNED_PROMISE = `
  const test = require('node:test');
  const assert = require('node:assert/strict');
  test('an async test that is wrong', async () => {
    const total = await Promise.resolve(5);
    assert.equal(total, 6);
  });
`;

// The test returns immediately and the assertion throws afterwards. The runner
// reports the test itself as passing and then fails the file, because activity
// after a test has ended is itself a failure.
const LATE_CALLBACK = `
  const test = require('node:test');
  const assert = require('node:assert/strict');
  test('an assertion that arrives late', () => {
    setTimeout(() => { assert.equal(5, 6); }, 0);
  });
`;

// The assertion is never reached at all: nothing emits the event. There is no
// failure to notice, and the run is green.
const NEVER_RAN = `
  const test = require('node:test');
  const { EventEmitter } = require('node:events');
  test('an assertion that never ran', (t) => {
    const events = new EventEmitter();
    events.on('done', () => { t.assert.equal(5, 5); });
  });
`;

// The same test with a declared count. The runner compares the count against
// the assertions it actually saw.
const NEVER_RAN_WITH_PLAN = `
  const test = require('node:test');
  const { EventEmitter } = require('node:events');
  test('an assertion that never ran, with a plan', (t) => {
    t.plan(1);
    const events = new EventEmitter();
    events.on('done', () => { t.assert.equal(5, 5); });
  });
`;

async function runTestFile(dir, name, source) {
  const file = path.join(dir, name);
  await fsp.writeFile(file, source, 'utf8');
  const result = spawnSync(process.execPath, ['--test', file], { stdio: 'pipe', encoding: 'utf8' });
  return { passed: result.status === 0, sawNotOk: result.stdout.includes('not ok') };
}

async function main() {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'nodejs-path-'));

  const good = await runTestFile(dir, 'good.test.js', PASSING);
  console.log(`correct async test passed: ${good.passed}`);

  const returned = await runTestFile(dir, 'returned.test.js', RETURNED_PROMISE);
  console.log(`wrong async test, promise returned to the runner, passed: ${returned.passed}`);
  console.log(`the report contained a failure line: ${returned.sawNotOk}`);

  const late = await runTestFile(dir, 'late.test.js', LATE_CALLBACK);
  console.log(`assertion thrown after the test ended, run passed: ${late.passed}`);

  const never = await runTestFile(dir, 'never.test.js', NEVER_RAN);
  console.log(`assertion that never ran, run passed: ${never.passed}`);

  const planned = await runTestFile(dir, 'planned.test.js', NEVER_RAN_WITH_PLAN);
  console.log(`the same test with a declared plan, run passed: ${planned.passed}`);

  await fsp.rm(dir, { recursive: true, force: true });
}

main();
