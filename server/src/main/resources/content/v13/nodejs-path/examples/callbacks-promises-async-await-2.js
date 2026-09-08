// Where an error goes in each notation, and the one place try/catch cannot
// reach: a throw from a callback that the event loop invoked later.

'use strict';

const { promisify } = require('node:util');

function loadRecordCallback(id, callback) {
  setTimeout(() => {
    if (id <= 0) {
      callback(new Error(`no record with id ${id}`));
      return;
    }
    callback(null, { id, name: `record-${id}` });
  }, 0);
}

const loadRecordPromise = promisify(loadRecordCallback);

async function main() {
  // Callback: the error is the first argument. Nothing throws.
  await new Promise((resolve) => {
    loadRecordCallback(-1, (error, record) => {
      console.log(`callback received an error object: ${error instanceof Error}`);
      console.log(`callback error message: ${error.message}`);
      console.log(`callback value is undefined: ${record === undefined}`);
      resolve();
    });
  });

  // Promise: the error becomes a rejection.
  await loadRecordPromise(-2).catch((error) => {
    console.log(`rejection message: ${error.message}`);
  });

  // await: the rejection becomes a thrown exception at the await.
  try {
    await loadRecordPromise(-3);
  } catch (error) {
    console.log(`try/catch around await caught: ${error.message}`);
  }

  // The trap. The try block returns long before the callback runs, so by the
  // time the callback throws there is no catch on the stack any more.
  process.once('uncaughtException', (error) => {
    console.log(`escaped to uncaughtException: ${error.message}`);
    console.log('the try/catch around setTimeout never saw it');
  });
  try {
    setTimeout(() => {
      throw new Error('thrown from a timer callback');
    }, 0);
    console.log('the try block finished before the callback ran');
  } catch {
    console.log('unreachable: this line never prints');
  }
}

main();
