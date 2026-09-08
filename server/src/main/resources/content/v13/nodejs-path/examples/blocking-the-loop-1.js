// The same amount of work, done in one synchronous run and then in chunks that
// give the loop a turn between them.
//
// The work is bounded by an iteration count and no duration is printed. What is
// printed is whether a callback scheduled *before* the work started got to run
// *while* the work was still outstanding.

'use strict';

const TOTAL = 4_000_000;
const CHUNK = 500_000;

function step(acc, i) {
  return (acc + i * i) % 1_000_003;
}

function blocking() {
  return new Promise((resolve) => {
    let markerRan = false;
    let workFinished = false;

    // Scheduled first, so under FIFO it is the next check-phase callback.
    setImmediate(() => {
      markerRan = true;
      resolve({ markerRanBeforeWorkFinished: markerRan && !workFinished, checksum });
    });

    let checksum = 0;
    for (let i = 0; i < TOTAL; i += 1) checksum = step(checksum, i);
    workFinished = true;
    console.log(`blocking run: marker had run by the time the loop ended: ${markerRan}`);
  });
}

function chunked() {
  return new Promise((resolve) => {
    let markerRanWithWorkOutstanding = false;
    let checksum = 0;
    let i = 0;

    setImmediate(() => {
      markerRanWithWorkOutstanding = i < TOTAL;
    });

    function nextChunk() {
      const end = Math.min(i + CHUNK, TOTAL);
      for (; i < end; i += 1) checksum = step(checksum, i);
      if (i < TOTAL) {
        setImmediate(nextChunk);
      } else {
        resolve({ markerRanBeforeWorkFinished: markerRanWithWorkOutstanding, checksum });
      }
    }
    setImmediate(nextChunk);
  });
}

async function main() {
  const a = await blocking();
  console.log(`blocking run: a callback ran before the work finished: ${a.markerRanBeforeWorkFinished}`);

  const b = await chunked();
  console.log(`chunked run: a callback ran before the work finished: ${b.markerRanBeforeWorkFinished}`);

  console.log(`both runs computed the same answer: ${a.checksum === b.checksum}`);
  console.log(`checksum: ${a.checksum}`);
}

main();
