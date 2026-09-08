// Nothing checks a declaration against the program it claims to describe.
declare const BUILD_ID: string;

console.log("the declaration compiles:", true);

try {
  console.log(BUILD_ID.length);
} catch (error) {
  const e = error as Error;
  console.log("runtime failure:", e.name + ": " + e.message);
}

// A declaration that is wrong about a type is just as quiet. The assertion
// below has exactly the effect a hand-written declaration file has: it tells
// the checker a shape, and no one compares that shape to the value.
interface Clock {
  now(): string;
}

const clock = {
  now: function () {
    return 1700000000000;
  },
} as unknown as Clock;

const stamp: string = clock.now();
console.log("declared string, actually a", typeof stamp);

try {
  console.log(stamp.toUpperCase());
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}
