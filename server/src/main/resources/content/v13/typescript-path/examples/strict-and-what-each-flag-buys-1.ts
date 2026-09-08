// strictNullChecks gives null and undefined their own types.
// @ts-expect-error Type 'undefined' is not assignable to type 'string'.
const nope: string = undefined;
void nope;

function findByPrefix(list: string[], prefix: string): string | undefined {
  for (let i = 0; i < list.length; i++) {
    if (list[i].indexOf(prefix) === 0) {
      return list[i];
    }
  }
  return undefined;
}

const names = ["ada", "grace"];
const hit = findByPrefix(names, "ad");
console.log(hit === undefined ? "(none)" : hit.toUpperCase());
console.log(findByPrefix(names, "zz") === undefined);

// Index access is the hole `strict` leaves open: the element type is reported
// without `undefined`, whatever the index turns out to be.
const missing = names[9];
console.log(typeof missing, missing);
try {
  console.log(missing.toUpperCase());
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}
