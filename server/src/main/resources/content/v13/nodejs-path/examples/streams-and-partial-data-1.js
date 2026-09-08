// A stream hands you chunks, and a chunk boundary has nothing to do with the
// boundaries in your data. The chunk sizes here are fixed by the program, so
// what is printed is the shape of the problem rather than one run's accident.

'use strict';

const { Readable } = require('node:stream');

const chunks = ['id,name\n1,al', 'ice\n2,bo', 'b\n3,carol\n'];

async function naive() {
  const rows = [];
  for await (const chunk of Readable.from(chunks)) {
    for (const line of chunk.split('\n')) {
      if (line !== '') rows.push(line);
    }
  }
  return rows;
}

async function buffered() {
  const rows = [];
  let carry = '';
  for await (const chunk of Readable.from(chunks)) {
    carry += chunk;
    const parts = carry.split('\n');
    carry = parts.pop(); // the last part may be half a line
    rows.push(...parts);
  }
  if (carry !== '') rows.push(carry);
  return rows;
}

async function main() {
  const naiveRows = await naive();
  const bufferedRows = await buffered();

  console.log(`chunks delivered: ${chunks.length}`);
  console.log(`splitting each chunk on its own gives ${naiveRows.length} rows:`);
  console.log(JSON.stringify(naiveRows));
  console.log(`carrying the remainder gives ${bufferedRows.length} rows:`);
  console.log(JSON.stringify(bufferedRows));
  console.log(`the naive reader split a name in half: ${naiveRows.includes('1,al')}`);

  // Reading the whole thing first is correct and is also the thing streams
  // exist to avoid: it needs room for every byte at once.
  let whole = '';
  for await (const chunk of Readable.from(chunks)) whole += chunk;
  console.log(`buffering everything agrees with the streaming reader: ${
    JSON.stringify(whole.split('\n').filter((l) => l !== '')) === JSON.stringify(bufferedRows)
  }`);
}

main();
