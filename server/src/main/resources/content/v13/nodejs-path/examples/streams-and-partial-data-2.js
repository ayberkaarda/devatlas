// pipeline connects streams and, unlike a chain of .pipe() calls, destroys all
// of them when one fails and tells you which error ended the run.

'use strict';

const { Readable, Transform, Writable } = require('node:stream');
const { pipeline } = require('node:stream/promises');

function lineSplitter() {
  let carry = '';
  return new Transform({
    readableObjectMode: true,
    transform(chunk, encoding, callback) {
      carry += chunk;
      const parts = carry.split('\n');
      carry = parts.pop();
      for (const part of parts) this.push(part);
      callback();
    },
    flush(callback) {
      if (carry !== '') this.push(carry);
      callback();
    }
  });
}

function rejectBlankNames() {
  return new Transform({
    objectMode: true,
    transform(line, encoding, callback) {
      const [id, name] = line.split(',');
      if (name === undefined || name === '') {
        callback(new Error(`row ${id} has no name`));
        return;
      }
      callback(null, { id: Number(id), name });
    }
  });
}

function collect(into) {
  return new Writable({
    objectMode: true,
    write(record, encoding, callback) {
      into.push(record);
      callback();
    }
  });
}

async function run(source) {
  const records = [];
  const splitter = lineSplitter();
  const validator = rejectBlankNames();
  const sink = collect(records);
  try {
    await pipeline(Readable.from(source), splitter, validator, sink);
    return { records, error: null, destroyed: splitter.destroyed };
  } catch (error) {
    return { records, error: error.message, destroyed: splitter.destroyed };
  }
}

async function main() {
  const good = await run(['1,alice\n2,bo', 'b\n3,carol\n']);
  console.log(`records: ${JSON.stringify(good.records)}`);
  console.log(`error: ${good.error}`);

  const bad = await run(['1,alice\n2,\n3,carol\n']);
  console.log(`records before the failure: ${JSON.stringify(bad.records)}`);
  console.log(`pipeline rejected with: ${bad.error}`);
  console.log(`the upstream splitter was destroyed too: ${bad.destroyed}`);
}

main();
