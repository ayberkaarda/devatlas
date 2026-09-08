// This function is never called. It exists to show the diagnostic: without the
// directive above the return, the file does not compile.
function rejected(value: unknown): number {
  // @ts-expect-error 'value' is of type 'unknown'.
  return value.length;
}
void rejected;

function lengthOfUnknown(value: unknown): number {
  if (typeof value === "string") {
    return value.length;
  }
  if (Array.isArray(value)) {
    return value.length;
  }
  if (typeof value === "object" && value !== null && "length" in value) {
    const len = (value as { length: unknown }).length;
    return typeof len === "number" ? len : -1;
  }
  return -1;
}

console.log(lengthOfUnknown("hello"));
console.log(lengthOfUnknown([1, 2, 3]));
console.log(lengthOfUnknown({ length: 7 }));
console.log(lengthOfUnknown(null));
