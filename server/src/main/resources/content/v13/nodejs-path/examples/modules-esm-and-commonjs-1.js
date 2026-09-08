// What CommonJS hands a file, and the assignment that quietly exports nothing.
//
// __filename and __dirname are machine-specific, so only their types are
// printed here.

'use strict';

console.log(`typeof require: ${typeof require}`);
console.log(`typeof module: ${typeof module}`);
console.log(`typeof __filename: ${typeof __filename}`);
console.log(`typeof __dirname: ${typeof __dirname}`);
console.log(`module.exports === exports at the top of the file: ${module.exports === exports}`);

// The two names start out pointing at the same object, so this works.
exports.first = 'reached the outside';
console.log(`property set through exports is on module.exports: ${module.exports.first}`);

// Reassigning the local name breaks the link. Nothing after this line is
// exported, and no error is reported.
exports = { second: 'never leaves the file' };
console.log(`after reassigning exports, the two are the same object: ${module.exports === exports}`);
console.log(`module.exports has 'second': ${Object.hasOwn(module.exports, 'second')}`);

// Assigning to module.exports is what actually replaces the exported value.
module.exports = { third: 'this one is exported' };
console.log(`module.exports is now: ${JSON.stringify(module.exports)}`);

// require is synchronous and caches by resolved specifier, so two requires of
// the same module give the same object.
const os1 = require('node:os');
const os2 = require('node:os');
console.log(`require returned the same object twice: ${os1 === os2}`);
console.log(`require.resolve of a builtin is its specifier: ${require.resolve('node:os') === 'node:os'}`);
