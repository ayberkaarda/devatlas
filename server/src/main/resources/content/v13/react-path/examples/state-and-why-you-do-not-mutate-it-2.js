// State behaves like a snapshot. Within one render the state variable never
// changes, so a handler that reads it three times reads the same number three
// times. React 19 queues what the set calls asked for and applies the queue
// before the next render; an updater function receives the pending state, so
// three updaters compose.

function applyQueue(stateThisRender, queue) {
  let pending = stateThisRender;
  for (const entry of queue) {
    pending = typeof entry === "function" ? entry(pending) : entry;
  }
  return pending;
}

const number = 0; // the value this render was given

// setNumber(number + 1) three times: every call is computed from the snapshot.
const direct = [number + 1, number + 1, number + 1];
console.log(`three plain set calls queue: ${JSON.stringify(direct)}`);
console.log(`next state from plain set calls: ${applyQueue(number, direct)}`);

// setNumber(n => n + 1) three times: each receives what the one before queued.
const updaters = [(n) => n + 1, (n) => n + 1, (n) => n + 1];
console.log(`next state from three updaters: ${applyQueue(number, updaters)}`);

// A mixture, in the documented order: the queue is applied front to back.
const mixed = [(n) => n + 1, 42, (n) => n + 5];
console.log(`next state from a mixed queue: ${applyQueue(number, mixed)}`);

// The variable in the already-running handler is unaffected by any of this.
function handleClick() {
  const next = applyQueue(number, updaters);
  return { queuedResult: next, variableStillReads: number };
}
const result = handleClick();
console.log(`queued result: ${result.queuedResult}`);
console.log(`the handler's own variable still reads: ${result.variableStillReads}`);
