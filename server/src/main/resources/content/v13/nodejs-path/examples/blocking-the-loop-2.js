// The same key derivation, twice: once on the calling thread and once on the
// libuv thread pool. The derived key is the same either way, which is why the
// synchronous version looks harmless until something else needs the loop.
//
// A self-rescheduling setImmediate acts as a heartbeat. Its count is compared
// only against zero, never printed as a number: how many turns fit inside a
// derivation is a property of the machine.

'use strict';

const crypto = require('node:crypto');

const PASSWORD = 'correct horse battery staple';
const SALT = 'nodejs-path';
const ITERATIONS = 2_000_000;
const KEY_LENGTH = 16;

let heartbeats = 0;
let beating = true;

function beat() {
  if (!beating) return;
  heartbeats += 1;
  setImmediate(beat);
}

function deriveOnThreadPool() {
  return new Promise((resolve, reject) => {
    const before = heartbeats;
    crypto.pbkdf2(PASSWORD, SALT, ITERATIONS, KEY_LENGTH, 'sha256', (error, key) => {
      if (error) reject(error);
      else resolve({ key: key.toString('hex'), beatsDuring: heartbeats - before });
    });
  });
}

function deriveOnThisThread() {
  const before = heartbeats;
  const key = crypto.pbkdf2Sync(PASSWORD, SALT, ITERATIONS, KEY_LENGTH, 'sha256');
  return { key: key.toString('hex'), beatsDuring: heartbeats - before };
}

async function main() {
  beat();

  const pooled = await deriveOnThreadPool();
  console.log(`thread pool: the loop kept turning during the derivation: ${pooled.beatsDuring > 0}`);

  const inline = deriveOnThisThread();
  console.log(`same thread: the loop kept turning during the derivation: ${inline.beatsDuring > 0}`);

  beating = false;

  console.log(`both produced the same key: ${pooled.key === inline.key}`);
  console.log(`key: ${pooled.key}`);
}

main();
