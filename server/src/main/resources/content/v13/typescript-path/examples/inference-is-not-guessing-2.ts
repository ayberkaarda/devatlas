// Best common type: no single candidate covers the others, so the result is a union.
const mixed = [0, 1, null]; // type (number | null)[]
console.log("length:", mixed.length);

let total = 0;
for (const n of mixed) {
  if (n !== null) {
    total += n;
  }
}
console.log("total:", total);

// @ts-expect-error Operator '+' cannot be applied to a possibly null operand.
console.log(mixed.reduce((a, n) => a + n, 0));

class Rhino {
  readonly kind = "rhino";
}
class Snake {
  readonly kind = "snake";
}

const zoo = [new Rhino(), new Snake()]; // type (Rhino | Snake)[]
console.log("zoo:", zoo.map((a) => a.kind).join(","));
