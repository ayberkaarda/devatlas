// Backpressure. write() returns false when the destination's buffer is at or
// past its high water mark; ignoring that return value is how a program that
// reads fast and writes slowly runs out of memory.
//
// highWaterMark is set explicitly so the return values below are decided by the
// program rather than by a default that could change.

'use strict';

const { Writable } = require('node:stream');
const { once } = require('node:events');

function slowSink(accepted) {
  return new Writable({
    objectMode: true,
    highWaterMark: 2,
    write(chunk, encoding, callback) {
      accepted.push(chunk);
      setImmediate(callback);
    }
  });
}

async function ignoringBackpressure() {
  const accepted = [];
  const sink = slowSink(accepted);
  const returns = [];
  for (const n of [1, 2, 3, 4, 5]) {
    returns.push(sink.write(n));
  }
  sink.end();
  await once(sink, 'finish');
  return { returns, accepted, buffered: sink.writableLength };
}

async function respectingBackpressure() {
  const accepted = [];
  const sink = slowSink(accepted);
  let waits = 0;
  for (const n of [1, 2, 3, 4, 5]) {
    if (!sink.write(n)) {
      waits += 1;
      await once(sink, 'drain');
    }
  }
  sink.end();
  await once(sink, 'finish');
  return { waits, accepted };
}

async function main() {
  const ignored = await ignoringBackpressure();
  console.log(`write() return values: ${JSON.stringify(ignored.returns)}`);
  console.log(`first write accepted more: ${ignored.returns[0]}`);
  console.log(`every later write said stop: ${ignored.returns.slice(1).every((v) => v === false)}`);
  console.log(`all writes were still queued in memory: ${ignored.accepted.length === 5}`);

  const respected = await respectingBackpressure();
  console.log(`the producer had to wait for drain: ${respected.waits > 0}`);
  console.log(`same data arrived: ${JSON.stringify(respected.accepted)}`);
}

main();
