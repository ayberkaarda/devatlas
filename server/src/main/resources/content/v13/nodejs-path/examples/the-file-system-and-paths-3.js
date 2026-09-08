// The await you forgot. Every function in node:fs/promises returns a promise
// immediately; dropping it does not make the work synchronous, it makes the
// failure invisible and the ordering wrong.
//
// Error messages from fs carry the offending path, which differs per machine,
// so only error.code is printed.

'use strict';

const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

// Without this listener Node.js 22 ends the process on the first dropped
// rejection below. The listener is what makes this file printable, and its
// existence is the point: something always notices.
const unhandled = [];
process.on('unhandledRejection', (reason) => {
  unhandled.push(reason.code);
});

async function main() {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'nodejs-path-'));
  const missing = path.join(dir, 'absent.txt');

  // A try/catch cannot catch a rejection that nothing awaited: readFile returns
  // before it fails, so the try block is already over.
  let caught = null;
  try {
    fsp.readFile(missing, 'utf8');
  } catch (error) {
    caught = error.code;
  }
  console.log(`try/catch without await caught something: ${caught !== null}`);

  // With the await in place the rejection becomes a throw at that line.
  try {
    await fsp.readFile(missing, 'utf8');
  } catch (error) {
    console.log(`try/catch with await caught code: ${error.code}`);
  }

  // Give the dropped rejection a turn of the loop to be reported.
  await new Promise((resolve) => setImmediate(resolve));
  console.log(`rejections reported as unhandled: ${unhandled.length}`);
  console.log(`reported code: ${unhandled[0]}`);

  // Ordering. An async function returns at its first await, so a caller that
  // does not await continues before the callee has finished.
  const order = [];
  async function writeNote() {
    await fsp.writeFile(path.join(dir, 'note.txt'), 'x', 'utf8');
    order.push('write finished');
  }
  const pending = writeNote();
  order.push('caller continued');
  // Read the log at the moment the call returned. How long the write then takes
  // is a property of the disk, so it is not printed.
  console.log(`log when the un-awaited call returned: ${order.join(' -> ')}`);
  await pending;
  console.log(`log once the promise was awaited: ${order.join(' -> ')}`);

  const ordered = [];
  async function writeNoteAgain() {
    await fsp.writeFile(path.join(dir, 'note.txt'), 'y', 'utf8');
    ordered.push('write finished');
  }
  await writeNoteAgain();
  ordered.push('caller continued');
  console.log(`order with await: ${ordered.join(' -> ')}`);

  // Array.prototype.forEach ignores the promise its callback returns, so the
  // line after the loop runs before any of the work does.
  const names = ['a.txt', 'b.txt', 'c.txt'];
  const withForEach = [];
  names.forEach(async (name) => {
    await fsp.writeFile(path.join(dir, name), name, 'utf8');
    withForEach.push(name);
  });
  console.log(`written when forEach returned: ${withForEach.length}`);

  const withForOf = [];
  for (const name of names) {
    await fsp.writeFile(path.join(dir, name), name, 'utf8');
    withForOf.push(name);
  }
  console.log(`written when the for..of loop returned: ${withForOf.length}`);

  const contents = await Promise.all(
    names.map((name) => fsp.readFile(path.join(dir, name), 'utf8'))
  );
  console.log(`Promise.all over map awaited all of them: ${contents.join(', ')}`);

  await fsp.rm(dir, { recursive: true, force: true });
}

main();
