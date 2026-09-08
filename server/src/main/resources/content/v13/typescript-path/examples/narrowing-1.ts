// `typeof null` is "object" in JavaScript, and the compiler models that exactly.
function brokenDescribe(strs: string | string[] | null): string {
  if (typeof strs === "object") {
    // @ts-expect-error 'strs' is possibly 'null'.
    return strs.join(", ");
  }
  return strs;
}

function describe(strs: string | string[] | null): string {
  if (strs === null) {
    return "(nothing)";
  }
  if (typeof strs === "object") {
    return strs.join(", ");
  }
  return strs;
}

console.log(describe(["a", "b"]));
console.log(describe("solo"));
console.log(describe(null));

try {
  console.log(brokenDescribe(null));
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}

console.log("typeof null is", typeof null);
