// process.env is a string map with a coercion of its own. Only variables this
// file sets are read, because the real environment differs on every machine.

'use strict';

console.log(`an unset variable reads as: ${process.env.NODEJS_PATH_ABSENT}`);
console.log(`'in' answers for an unset variable: ${'NODEJS_PATH_ABSENT' in process.env}`);

process.env.NODEJS_PATH_PORT = 8080;
console.log(`assigned the number 8080, typeof is: ${typeof process.env.NODEJS_PATH_PORT}`);
console.log(`its value is: ${JSON.stringify(process.env.NODEJS_PATH_PORT)}`);

process.env.NODEJS_PATH_DEBUG = false;
console.log(`assigned the boolean false, value is: ${JSON.stringify(process.env.NODEJS_PATH_DEBUG)}`);
console.log(`and it is truthy: ${Boolean(process.env.NODEJS_PATH_DEBUG)}`);

process.env.NODEJS_PATH_EMPTY = '';
console.log(`an empty string is present: ${'NODEJS_PATH_EMPTY' in process.env}`);
console.log(`but a ?? default will not replace it: ${JSON.stringify(process.env.NODEJS_PATH_EMPTY ?? 'fallback')}`);
console.log(`a || default will: ${JSON.stringify(process.env.NODEJS_PATH_EMPTY || 'fallback')}`);

process.env.NODEJS_PATH_UNDEFINED = undefined;
console.log(`assigning undefined stores the text: ${JSON.stringify(process.env.NODEJS_PATH_UNDEFINED)}`);

delete process.env.NODEJS_PATH_UNDEFINED;
console.log(`after delete, 'in' answers: ${'NODEJS_PATH_UNDEFINED' in process.env}`);

// Coercion has to be written out, because there is nothing in the environment
// to write it for you.
function readInteger(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer, got ${JSON.stringify(raw)}`);
  return value;
}
function readBoolean(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  if (raw === 'true' || raw === '1') return true;
  if (raw === 'false' || raw === '0') return false;
  throw new Error(`${name} must be true or false, got ${JSON.stringify(raw)}`);
}

console.log(`readInteger gives a number: ${typeof readInteger('NODEJS_PATH_PORT', 3000)}`);
console.log(`readBoolean gives a boolean: ${readBoolean('NODEJS_PATH_DEBUG', true)}`);

process.env.NODEJS_PATH_PORT = 'eighty-eighty';
try {
  readInteger('NODEJS_PATH_PORT', 3000);
} catch (error) {
  console.log(`a bad value is refused: ${error.message}`);
}
