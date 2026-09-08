// Sequential await against concurrent await, decided by where the call happens
// rather than by where the await does.
//
// Settling is driven by explicit resolve functions rather than timers, so the
// interleaving printed below is fixed by the program and not by the machine.

'use strict';

function deferred(name) {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return {
    name,
    promise,
    settle(value) {
      console.log(`settling ${name}`);
      resolve(value);
    }
  };
}

async function main() {
  const a = deferred('a');
  const b = deferred('b');

  // Both operations are started here, before anything is awaited.
  const all = Promise.all([a.promise, b.promise]);

  // b settles first; the results array is still ordered by input position.
  b.settle('value-of-b');
  a.settle('value-of-a');

  const results = await all;
  console.log(`Promise.all results: ${JSON.stringify(results)}`);
  console.log(`results are ordered by input, not by settling order: ${results[0] === 'value-of-a'}`);

  // Promise.allSettled reports every outcome instead of failing at the first.
  const mixed = await Promise.allSettled([
    Promise.resolve('ok'),
    Promise.reject(new Error('refused'))
  ]);
  console.log(`allSettled statuses: ${mixed.map((r) => r.status).join(', ')}`);
  console.log(`allSettled kept the reason: ${mixed[1].reason.message}`);

  // Promise.all rejects as soon as one input rejects.
  const failed = await Promise.all([
    Promise.resolve('ok'),
    Promise.reject(new Error('refused'))
  ]).then(() => 'resolved', (error) => `rejected with ${error.message}`);
  console.log(`Promise.all outcome: ${failed}`);
}

main();
