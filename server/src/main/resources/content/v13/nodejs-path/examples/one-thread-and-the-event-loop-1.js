// The ordering Node.js 22 guarantees between synchronous code, the nextTick
// queue, the promise microtask queue and the timers phase.
//
// Only guaranteed orderings are printed. Two timers with the same delay, or
// setTimeout(0) against setImmediate at the top level of a module, have no
// specified order and are deliberately absent.

'use strict';

const log = [];

function record(label) {
  log.push(label);
}

record('1 synchronous, first statement');

setTimeout(() => {
  record('5 timers phase: setTimeout callback');
  console.log(log.join('\n'));
}, 0);

Promise.resolve().then(() => {
  record('4 promise microtask');
});

process.nextTick(() => {
  record('3 process.nextTick callback');
});

record('2 synchronous, last statement');
