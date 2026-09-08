// setImmediate against setTimeout: what is specified and what is not.
//
// Scheduled from the top level of a module, the order of setTimeout(fn, 0) and
// setImmediate(fn) is not specified and varies between runs, so this file does
// not print it. Scheduled from inside an I/O callback, setImmediate always runs
// first, because the check phase follows the poll phase in the same turn of the
// loop. That is the ordering worth recording.

'use strict';

const fs = require('node:fs');

fs.readFile(__filename, () => {
  const order = [];

  setTimeout(() => {
    order.push('setTimeout');
    if (order.length === 2) report(order);
  }, 0);

  setImmediate(() => {
    order.push('setImmediate');
    if (order.length === 2) report(order);
  });
});

function report(order) {
  console.log(`scheduled inside an I/O callback, first to run: ${order[0]}`);
  console.log(`setImmediate ran before setTimeout: ${order[0] === 'setImmediate'}`);
}
