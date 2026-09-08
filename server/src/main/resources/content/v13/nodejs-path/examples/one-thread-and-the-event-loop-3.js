// One thread: a scheduled callback cannot interleave with synchronous code.
//
// The busy section below is bounded by an iteration count rather than a clock,
// so what is printed is an ordering property and not a duration.

'use strict';

let microtaskRan = false;
let timerRan = false;

queueMicrotask(() => {
  microtaskRan = true;
});
setTimeout(() => {
  timerRan = true;
  console.log(`timer ran after the synchronous section: ${timerRan}`);
}, 0);

let checksum = 0;
for (let i = 0; i < 5_000_000; i += 1) {
  checksum = (checksum + i) % 1_000_003;
}

console.log(`checksum computed: ${checksum}`);
console.log(`microtask ran during the synchronous section: ${microtaskRan}`);
console.log(`timer ran during the synchronous section: ${timerRan}`);

async function afterAwait() {
  console.log('inside the async function, before await');
  await null;
  console.log(`after await, microtask queue had already drained: ${microtaskRan}`);
}

afterAwait();
console.log('the caller continued while the async function was suspended');
