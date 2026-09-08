// Copying one level is not copying. A spread produces a new outer object whose
// members are the same references, so writing through a nested member changes
// the value the previous render is still holding -- and the reference React 19
// compares with Object.is has not moved at that level either.

const original = {
  name: "Niki de Saint Phalle",
  artwork: { title: "Blue Nana", city: "Hamburg" },
};

// A shallow copy, then a write through the shared nested object.
const shallow = { ...original };
shallow.artwork.city = "Nice";

console.log(`outer objects differ: ${!Object.is(original, shallow)}`);
console.log(`nested objects are the same object: ${Object.is(original.artwork, shallow.artwork)}`);
console.log(`the value we meant to leave alone now reads: ${original.artwork.city}`);

// Copying every level that changes leaves the previous value intact.
const before = {
  name: "Niki de Saint Phalle",
  artwork: { title: "Blue Nana", city: "Hamburg" },
};
const after = { ...before, artwork: { ...before.artwork, city: "Nice" } };

console.log(`previous value preserved: ${before.artwork.city}`);
console.log(`next value: ${after.artwork.city}`);
console.log(`nested reference changed: ${!Object.is(before.artwork, after.artwork)}`);

// The untouched branch may still be shared: only the path that changed is new.
const wide = { a: { n: 1 }, b: { n: 2 } };
const narrowed = { ...wide, a: { ...wide.a, n: 9 } };
console.log(`changed branch is new: ${!Object.is(wide.a, narrowed.a)}`);
console.log(`untouched branch is shared: ${Object.is(wide.b, narrowed.b)}`);
