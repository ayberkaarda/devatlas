// Why a test needs a flush boundary. Clicking queues an update; React 19
// applies the queue and renders, and only then does the screen say anything
// new. A test that asserts between those two moments reads the previous frame
// and is right about nothing. `act` is the helper that closes the gap: it
// processes the pending work before the assertion runs.

function makeScreen() {
  let committed = { count: 0 };
  const queue = [];
  return {
    click() {
      queue.push((n) => n + 1); // an updater, queued rather than applied
    },
    flush() {
      let next = committed.count;
      while (queue.length > 0) {
        next = queue.shift()(next);
      }
      committed = { count: next };
    },
    text: () => `clicked ${committed.count} times`,
    pending: () => queue.length,
  };
}

// The assertion nobody flushed.
const eager = makeScreen();
eager.click();
console.log(`without a flush, updates still queued: ${eager.pending()}`);
console.log(`without a flush, the screen says: ${eager.text()}`);

// The same interaction, with the queue processed first.
const flushed = makeScreen();
flushed.click();
flushed.flush();
console.log(`after a flush, updates still queued: ${flushed.pending()}`);
console.log(`after a flush, the screen says: ${flushed.text()}`);

// The failure mode that makes this worth a lesson: a test that asserts the
// starting value, forgets the flush, and passes for the wrong reason.
const misleading = makeScreen();
const expectedBeforeClick = "clicked 0 times";
console.log(`assertion before the click passes: ${misleading.text() === expectedBeforeClick}`);
misleading.click();
console.log(`same assertion still passes after the click: ${misleading.text() === expectedBeforeClick}`);
misleading.flush();
console.log(`and fails once the queue is applied: ${misleading.text() !== expectedBeforeClick}`);
console.log(`the screen finally says: ${misleading.text()}`);
