interface Options {
  width: number;
  colour?: string;
}

function area(o: Options): number {
  return o.width * o.width;
}

// A fresh object literal is checked for properties the target does not declare.
// @ts-expect-error Object literal may only specify known properties.
console.log(area({ width: 3, colur: "red" }));

// The identical object reaches the identical parameter without a complaint once
// it has been assigned to a variable: the literal is no longer fresh, and plain
// structural compatibility only requires that every declared member is present.
const opts = { width: 3, colur: "red" };
console.log(area(opts));
