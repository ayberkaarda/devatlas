// A round trip through a temporary directory: create, write, read, list,
// remove. The directory name is different on every run, so it is never printed;
// only the contents and the outcomes are.

'use strict';

const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

async function main() {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'nodejs-path-'));

  const notes = path.join(dir, 'notes.txt');
  await fsp.writeFile(notes, 'first line\nsecond line\n', 'utf8');

  const text = await fsp.readFile(notes, 'utf8');
  console.log(`read back ${text.split('\n').filter((l) => l !== '').length} lines`);
  console.log(`first line: ${text.split('\n')[0]}`);

  // Without an encoding, readFile resolves with a Buffer rather than a string.
  const bytes = await fsp.readFile(notes);
  console.log(`without an encoding the result is a Buffer: ${Buffer.isBuffer(bytes)}`);
  console.log(`byte length matches the UTF-8 encoding: ${bytes.length === Buffer.byteLength(text, 'utf8')}`);

  await fsp.mkdir(path.join(dir, 'sub'), { recursive: true });
  await fsp.writeFile(path.join(dir, 'sub', 'inner.txt'), 'inner\n', 'utf8');

  const entries = await fsp.readdir(dir, { withFileTypes: true });
  const described = entries
    .map((e) => `${e.name}:${e.isDirectory() ? 'dir' : 'file'}`)
    .sort();
  console.log(`entries: ${described.join(', ')}`);

  const stats = await fsp.stat(notes);
  console.log(`notes.txt is a file: ${stats.isFile()}`);
  console.log(`size agrees with what was written: ${stats.size === Buffer.byteLength(text, 'utf8')}`);

  // access() answers a question about now, not about the next line. Prefer
  // opening and handling the failure over asking first and opening second.
  await fsp.access(notes);
  console.log('access resolved for a file that exists');
  try {
    await fsp.access(path.join(dir, 'absent.txt'));
  } catch (error) {
    console.log(`access rejected for a missing file with code: ${error.code}`);
  }

  await fsp.rm(dir, { recursive: true, force: true });
  try {
    await fsp.stat(dir);
    console.log('the directory is still there');
  } catch (error) {
    console.log(`after rm, stat rejects with code: ${error.code}`);
  }
}

main();
