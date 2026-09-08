// A constraint says what the blank is allowed to be filled with, and one type
// parameter may be constrained by another.
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}

const user = { name: "ada", age: 36 };

const userName = getProperty(user, "name"); // string
const userAge = getProperty(user, "age"); // number
console.log(userName.toUpperCase(), userAge.toFixed(1));

// @ts-expect-error Argument of type '"email"' is not assignable to parameter of type '"name" | "age"'.
console.log(getProperty(user, "email"));

interface HasLength {
  length: number;
}
function longest<T extends HasLength>(a: T, b: T): T {
  return a.length >= b.length ? a : b;
}

console.log(longest("alpha", "bb"));
console.log(JSON.stringify(longest([1, 2, 3], [4])));
// @ts-expect-error Argument of type 'number' does not satisfy the constraint 'HasLength'.
console.log(longest(1, 2));
