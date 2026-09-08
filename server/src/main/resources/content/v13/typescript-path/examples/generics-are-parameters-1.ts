// The type parameter is filled in at the call site, and the result repeats it.
function firstElement<T>(arr: T[]): T | undefined {
  return arr.length > 0 ? arr[0] : undefined;
}

const names = ["ada", "grace"];
const first = firstElement(names); // string | undefined
console.log(first === undefined ? "(empty)" : first.toUpperCase());
console.log(firstElement<number>([]) === undefined);

// Constraining the parameter to the array instead of using it as the element
// type moves the blank to the wrong place: the element type is lost.
function firstElementLoose<T extends any[]>(arr: T) {
  return arr[0]; // inferred as any
}

const wrong: number = firstElementLoose(names);
console.log(typeof wrong, wrong);
