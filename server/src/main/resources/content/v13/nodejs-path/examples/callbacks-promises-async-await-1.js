// One operation written three ways. The callback version is the primitive; the
// other two are built from it and produce the same value in the same order.

'use strict';

const { promisify } = require('node:util');

// The error-first callback convention: (error, value).
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

function callbackStyle() {
  return new Promise((resolve) => {
    loadRecordCallback(1, (error, record) => {
      console.log(`callback style: ${record.name}`);
      resolve();
    });
  });
}

function promiseStyle() {
  return loadRecordPromise(2).then((record) => {
    console.log(`promise style: ${record.name}`);
  });
}

async function awaitStyle() {
  const record = await loadRecordPromise(3);
  console.log(`await style: ${record.name}`);
}

async function main() {
  await callbackStyle();
  await promiseStyle();
  await awaitStyle();
  console.log(`promisify returned a function: ${typeof loadRecordPromise === 'function'}`);

  // An async function returns a promise even when its body never awaits.
  const plain = async () => 7;
  console.log(`an async function always returns a promise: ${plain() instanceof Promise}`);
  console.log(`awaiting it yields the returned value: ${await plain()}`);
}

main();
