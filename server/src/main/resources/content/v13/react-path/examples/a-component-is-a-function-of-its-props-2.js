// Rendering is a calculation, so it can be repeated. React 19 Strict Mode
// calls a component twice in development for exactly this reason: a component
// that changes something it did not create gives a different answer the second
// time, and the second answer is the one on screen.

const guestList = ["Alice"]; // existed before either component was called

function ImpureGuests({ guest }) {
  guestList.push(guest); // changes a value it did not create
  return guestList.join(", ");
}

function PureGuests({ guests, guest }) {
  const shown = [...guests, guest]; // local mutation: created during this call
  return shown.join(", ");
}

function callTwice(render) {
  const first = render();
  const second = render();
  return { first, second, stable: first === second };
}

const impure = callTwice(() => ImpureGuests({ guest: "Bob" }));
console.log(`impure, first call:  ${impure.first}`);
console.log(`impure, second call: ${impure.second}`);
console.log(`impure stable across two calls: ${impure.stable}`);

const pure = callTwice(() => PureGuests({ guests: ["Alice"], guest: "Bob" }));
console.log(`pure, first call:  ${pure.first}`);
console.log(`pure, second call: ${pure.second}`);
console.log(`pure stable across two calls: ${pure.stable}`);

// The same rule, seen from the other side: a component may not write to the
// props it was given. React 19 freezes elements and their props in development,
// and what a write to a frozen object does depends on the mode the code runs
// in. This file is a plain script, so the first block is sloppy mode.
const props = Object.freeze({ name: "Taylor" });

try {
  props.name = "Sam";
  console.log(`sloppy mode: assignment ignored, name still reads ${props.name}`);
} catch (error) {
  console.log(`sloppy mode: threw ${error.constructor.name}`);
}

// A function-level directive gives the second block strict mode, which is what
// an ES module gets for free -- and every React component lives in a module.
function writeStrictly(target) {
  "use strict";
  target.name = "Sam";
}

try {
  writeStrictly(props);
  console.log(`strict mode: assignment ignored, name still reads ${props.name}`);
} catch (error) {
  console.log(`strict mode: threw ${error.constructor.name}`);
}
