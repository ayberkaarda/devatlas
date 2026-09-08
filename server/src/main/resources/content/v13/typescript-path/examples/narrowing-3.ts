interface User {
  name: string;
}

// A type predicate is a promise. The compiler takes it at face value and never
// re-checks it.
function isUser(value: unknown): value is User {
  return typeof value === "object" && value !== null;
}

const candidate: unknown = { nickname: "ada" };
if (isUser(candidate)) {
  console.log("name is", candidate.name);
  try {
    console.log(candidate.name.toUpperCase());
  } catch (error) {
    console.log("runtime failure:", (error as Error).message);
  }
}

// Narrowing of a property is not discarded by an intervening call, even one
// that assigns to that property.
interface Box {
  value: string | null;
}
const box: Box = { value: "held" };
function clear(): void {
  box.value = null;
}

if (box.value !== null) {
  clear();
  try {
    console.log(box.value.toUpperCase());
  } catch (error) {
    console.log("runtime failure:", (error as Error).message);
  }
}
