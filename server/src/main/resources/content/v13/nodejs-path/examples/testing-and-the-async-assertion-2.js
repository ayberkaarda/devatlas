// Assertions about asynchronous code: the one Node.js refuses outright, and the
// one that reports success without having checked anything.

'use strict';

const assert = require('node:assert/strict');

const dropped = [];
process.on('unhandledRejection', (reason) => {
  dropped.push(reason.code);
});

async function loadUser(id) {
  await null;
  if (id <= 0) {
    const error = new Error(`no user with id ${id}`);
    error.code = 'USER_NOT_FOUND';
    throw error;
  }
  return { id, name: 'ada', roles: ['reader'] };
}

async function main() {
  // assert.throws only sees a synchronous throw. An async function returns a
  // rejected promise instead, so this fails loudly rather than passing.
  let pending;
  try {
    assert.throws(() => {
      pending = loadUser(-1);
      return pending;
    });
    console.log('assert.throws passed over an async call');
  } catch (error) {
    console.log(`assert.throws refused an async call: ${error.code}`);
  }
  await pending.catch(() => {});

  // assert.rejects is the asynchronous form. Awaited, it does its job.
  await assert.rejects(() => loadUser(-2), { code: 'USER_NOT_FOUND' });
  console.log('awaited assert.rejects matched the error code');

  try {
    await assert.rejects(() => loadUser(1));
    console.log('awaited assert.rejects passed for a resolving call');
  } catch (error) {
    console.log(`awaited assert.rejects refused a resolving call: ${error.code}`);
  }

  // Not awaited, the same assertion over the same resolving call reports
  // nothing at all: the failure is inside a promise the test threw away.
  assert.rejects(() => loadUser(1));
  console.log('un-awaited assert.rejects returned without complaining');
  await new Promise((resolve) => setImmediate(resolve));
  console.log(`assertion failures that escaped instead: ${dropped.length}`);
  console.log(`escaped failure code: ${dropped[0]}`);

  // deepEqual compares structure; equal compares identity for objects.
  const user = await loadUser(7);
  assert.deepEqual(user, { id: 7, name: 'ada', roles: ['reader'] });
  console.log('deepEqual matched the whole record');
  try {
    assert.equal(user, { id: 7, name: 'ada', roles: ['reader'] });
  } catch (error) {
    console.log(`equal on two distinct objects failed with: ${error.code}`);
  }

  // The other shape that tests nothing: an assertion in a callback the test
  // never waited for. Counting the assertions that ran is what catches it.
  let ranInsideAwaitedCallback = 0;
  await new Promise((resolve) => {
    setImmediate(() => {
      assert.equal(1, 1);
      ranInsideAwaitedCallback += 1;
      resolve();
    });
  });
  console.log(`assertions run when the test waited: ${ranInsideAwaitedCallback}`);

  let ranInsideDroppedCallback = 0;
  setImmediate(() => {
    ranInsideDroppedCallback += 1;
  });
  console.log(`assertions run when the test did not wait: ${ranInsideDroppedCallback}`);
}

main();
