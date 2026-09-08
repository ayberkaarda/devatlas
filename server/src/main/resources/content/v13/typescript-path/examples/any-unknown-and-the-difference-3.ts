// JSON.parse is declared to return `any`, so the annotation below is accepted
// without anyone checking whether it is true.
const payload: any = JSON.parse('{"id": 7}');

const id: string = payload.id;
console.log(typeof id, id);
console.log("ID-" + id);

try {
  const age: number = payload.profile.age;
  console.log(age);
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}

// The same value crossing the same boundary as `unknown` cannot be used until
// something checks it.
function safeParse(text: string): unknown {
  return JSON.parse(text);
}

const parsed = safeParse('{"id": 7}');
// @ts-expect-error 'parsed' is of type 'unknown'.
console.log(parsed.id);

if (typeof parsed === "object" && parsed !== null && "id" in parsed) {
  console.log("checked:", (parsed as { id: unknown }).id);
}
