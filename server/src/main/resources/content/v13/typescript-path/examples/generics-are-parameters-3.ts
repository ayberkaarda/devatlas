// Two blanks, filled independently, and both survive into the result type.
function zip<A, B>(as: A[], bs: B[]): Array<[A, B]> {
  const out: Array<[A, B]> = [];
  const n = Math.min(as.length, bs.length);
  for (let i = 0; i < n; i++) {
    out.push([as[i], bs[i]]);
  }
  return out;
}

const pairs = zip(["a", "b"], [1, 2, 3]);
console.log(JSON.stringify(pairs));
console.log(pairs[0][0].toUpperCase(), pairs[0][1].toFixed(1));

// One blank in two argument positions: the compiler must choose a single type
// that both arguments satisfy.
function pairSame<T>(x: T, y: T): T[] {
  return [x, y];
}

console.log(JSON.stringify(pairSame("a", "b")));
console.log(JSON.stringify(pairSame<string | number>("a", 1)));

function pluck<T, K extends keyof T>(items: T[], key: K): Array<T[K]> {
  return items.map(function (item) {
    return item[key];
  });
}

const people = [
  { name: "ada", age: 36 },
  { name: "grace", age: 45 },
];
console.log(pluck(people, "name").join(","));
console.log(
  pluck(people, "age").reduce(function (a, b) {
    return a + b;
  }, 0),
);
