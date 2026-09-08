// Nothing inside this function is checked. `any` switches the checker off for
// every expression the value flows into.
function lengthOfAny(value: any): number {
  return value.length;
}

console.log(lengthOfAny("hello"));
console.log(lengthOfAny([1, 2, 3]));
console.log(lengthOfAny({ length: 7 }));

try {
  console.log(lengthOfAny(null));
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}
