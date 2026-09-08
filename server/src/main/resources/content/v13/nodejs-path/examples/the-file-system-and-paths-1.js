// Path arithmetic. path.win32 and path.posix are used explicitly throughout,
// because the platform-sensitive `path` object would make this file print
// different text depending on where it ran.

'use strict';

const path = require('node:path');

console.log(`posix separator: ${path.posix.sep}`);
console.log(`win32 separator: ${JSON.stringify(path.win32.sep)}`);

const parts = ['data', 'logs', 'app.log'];
console.log(`posix join: ${path.posix.join(...parts)}`);
console.log(`win32 join: ${JSON.stringify(path.win32.join(...parts))}`);

// Windows accepts forward slashes, and join normalises them away.
console.log(`win32 join of a posix-looking segment: ${JSON.stringify(path.win32.join('data', 'logs/app.log'))}`);
console.log(`win32 normalise with a parent segment: ${JSON.stringify(path.win32.normalize('data/logs/../app.log'))}`);

// resolve walks right to left until it has an absolute path.
console.log(`win32 resolve: ${JSON.stringify(path.win32.resolve('C:\\app', 'data', 'x.txt'))}`);
console.log(`posix resolve: ${path.posix.resolve('/app', 'data', 'x.txt')}`);

// A segment that starts with a separator is an ordinary segment to join and an
// absolute path to resolve. That difference is where path traversal lives.
console.log(`win32 join with a rooted segment: ${JSON.stringify(path.win32.join('uploads', '/etc/hosts'))}`);
console.log(`win32 resolve with a rooted segment: ${JSON.stringify(path.win32.resolve('C:\\uploads', '/etc/hosts'))}`);

// The check that actually contains a name supplied by someone else.
function isInside(root, candidate, impl) {
  const resolved = impl.resolve(root, candidate);
  const relative = impl.relative(root, resolved);
  return relative !== '' && !relative.startsWith('..') && !impl.isAbsolute(relative);
}
console.log(`'notes.txt' stays inside: ${isInside('/srv/uploads', 'notes.txt', path.posix)}`);
console.log(`'../../etc/passwd' stays inside: ${isInside('/srv/uploads', '../../etc/passwd', path.posix)}`);
console.log(`'sub/notes.txt' stays inside: ${isInside('/srv/uploads', 'sub/notes.txt', path.posix)}`);

console.log(`basename: ${path.posix.basename('/srv/uploads/notes.txt')}`);
console.log(`extname: ${path.posix.extname('/srv/uploads/notes.txt')}`);
console.log(`win32 treats a leading slash as absolute: ${path.win32.isAbsolute('/etc')}`);
console.log(`win32 treats 'C:notes.txt' as absolute: ${path.win32.isAbsolute('C:notes.txt')}`);
