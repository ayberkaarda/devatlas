const words = ["alpha", "bb", "c"];

// The callback parameter carries no annotation: its type is read from the
// position the function literal appears in.
console.log("lengths:", words.map((w) => w.length).join(","));

// In a position with no contextual type there is nothing to read, and
// noImplicitAny (part of --strict) refuses to guess.
// @ts-expect-error Parameter 'w' implicitly has an 'any' type.
const lengthOf = (w) => w.length;
console.log("lengthOf:", lengthOf("abcd"));

// A return type is inferred from every return path in the body.
function parseFlag(raw: string) {
  return raw === "1" || raw.toLowerCase() === "true";
}
const flag: boolean = parseFlag("TRUE");
console.log("flag:", flag);
