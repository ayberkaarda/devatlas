// What the process passes on. A child inherits the whole environment by
// default, which is how a secret held for one purpose ends up inside a tool
// that had no business seeing it.

'use strict';

const { spawnSync } = require('node:child_process');
const fsp = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

const CHILD = `
  const names = ['NODEJS_PATH_TOKEN', 'NODEJS_PATH_LOCALE', 'NODEJS_PATH_FROM_FILE'];
  const seen = names.filter((n) => process.env[n] !== undefined);
  console.log(seen.join(',') || '(none)');
`;

function runChild(env) {
  const result = spawnSync(process.execPath, ['-e', CHILD], { env, encoding: 'utf8' });
  return result.stdout.trim();
}

function runChildWithEnvFile(envFile) {
  const result = spawnSync(process.execPath, [`--env-file=${envFile}`, '-e', CHILD], {
    env: { NODEJS_PATH_LOCALE: 'en-GB' },
    encoding: 'utf8'
  });
  return result.stdout.trim();
}

async function main() {
  process.env.NODEJS_PATH_TOKEN = 'sk-not-a-real-token';
  process.env.NODEJS_PATH_LOCALE = 'en-GB';

  console.log(`inheriting everything, the child sees: ${runChild(process.env)}`);

  // An allow-list is one object literal and it is the whole fix.
  const allowed = { NODEJS_PATH_LOCALE: process.env.NODEJS_PATH_LOCALE };
  console.log(`with an allow-list, the child sees: ${runChild(allowed)}`);

  console.log(`the parent still has the token: ${'NODEJS_PATH_TOKEN' in process.env}`);

  // Node.js 22 can read a key=value file into the environment at startup, which
  // keeps a development secret out of the shell history and out of the process
  // list. It is a convenience for development, not a secret store.
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'nodejs-path-'));
  const envFile = path.join(dir, 'local.env');
  await fsp.writeFile(envFile, 'NODEJS_PATH_FROM_FILE=loaded\n', 'utf8');
  console.log(`with --env-file, the child sees: ${runChildWithEnvFile(envFile)}`);
  await fsp.rm(dir, { recursive: true, force: true });

  // A value read out of the environment is a copy. Changing the copy does not
  // change the variable, and changing the variable does not change the copy.
  const copied = process.env.NODEJS_PATH_LOCALE;
  process.env.NODEJS_PATH_LOCALE = 'tr-TR';
  console.log(`the copy taken earlier: ${copied}`);
  console.log(`the variable now: ${process.env.NODEJS_PATH_LOCALE}`);
}

main();
