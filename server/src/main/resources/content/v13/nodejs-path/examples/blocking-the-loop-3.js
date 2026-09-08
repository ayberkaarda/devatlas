// Work that has no asynchronous form goes on a worker thread. The worker has
// its own event loop and its own heap; the two threads exchange copies of data
// through messages, not shared objects.
//
// The worker source is given as a string with { eval: true } so that this file
// needs no companion on disk.

'use strict';

const { Worker } = require('node:worker_threads');
const crypto = require('node:crypto');

const WORKER_SOURCE = `
  const { parentPort, workerData } = require('node:worker_threads');
  const crypto = require('node:crypto');
  const key = crypto.pbkdf2Sync(
    workerData.password,
    workerData.salt,
    workerData.iterations,
    workerData.keyLength,
    'sha256'
  );
  workerData.mutated = true;
  parentPort.postMessage({ key: key.toString('hex') });
`;

const job = {
  password: 'correct horse battery staple',
  salt: 'nodejs-path',
  iterations: 2_000_000,
  keyLength: 16,
  mutated: false
};

let heartbeats = 0;
let beating = true;
function beat() {
  if (!beating) return;
  heartbeats += 1;
  setImmediate(beat);
}

function runOnWorker() {
  return new Promise((resolve, reject) => {
    const before = heartbeats;
    const worker = new Worker(WORKER_SOURCE, { eval: true, workerData: job });
    worker.once('message', (message) => {
      resolve({ key: message.key, beatsDuring: heartbeats - before });
    });
    worker.once('error', reject);
  });
}

async function main() {
  beat();

  const fromWorker = await runOnWorker();
  beating = false;

  console.log(`the main loop kept turning while the worker computed: ${fromWorker.beatsDuring > 0}`);

  const here = crypto
    .pbkdf2Sync(job.password, job.salt, job.iterations, job.keyLength, 'sha256')
    .toString('hex');
  console.log(`the worker's answer matches this thread's: ${fromWorker.key === here}`);
  console.log(`key: ${fromWorker.key}`);

  // workerData was structurally cloned. The worker set mutated = true on its
  // own copy and this thread never saw it.
  console.log(`the worker's mutation reached this thread: ${job.mutated}`);
}

main();
