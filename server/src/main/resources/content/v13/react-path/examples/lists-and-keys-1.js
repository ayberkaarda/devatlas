// A model of the matching React 19 performs on a keyed list: an item in the
// next render is paired with the item in the previous render that carries the
// same key. Where a key is found, the instance is reused; where none is found,
// a new one is created.

function reconcile(previous, next) {
  const byKey = new Map(previous.map((child) => [child.key, child]));
  const reused = [];
  const created = [];
  for (const child of next) {
    const match = byKey.get(child.key);
    if (match) {
      reused.push({ key: child.key, was: match.text, now: child.text });
    } else {
      created.push(child.key);
    }
  }
  const rewritten = reused.filter((pair) => pair.was !== pair.now);
  return { reused, created, rewritten };
}

const people = [
  { id: "ada", text: "Ada Lovelace" },
  { id: "grace", text: "Grace Hopper" },
];
const withNewcomer = [{ id: "linus", text: "Linus Pauling" }, ...people];

const keyedById = (items) => items.map((item) => ({ key: item.id, text: item.text }));
const keyedByIndex = (items) => items.map((item, i) => ({ key: String(i), text: item.text }));

const byId = reconcile(keyedById(people), keyedById(withNewcomer));
const byIndex = reconcile(keyedByIndex(people), keyedByIndex(withNewcomer));

console.log(`id keys, instances reused: ${byId.reused.length}`);
console.log(`id keys, instances created: ${byId.created.length}`);
console.log(`id keys, reused instances handed different data: ${byId.rewritten.length}`);

console.log(`index keys, instances reused: ${byIndex.reused.length}`);
console.log(`index keys, instances created: ${byIndex.created.length}`);
console.log(`index keys, reused instances handed different data: ${byIndex.rewritten.length}`);

for (const pair of byIndex.rewritten) {
  console.log(`  key ${pair.key} held "${pair.was}" and now holds "${pair.now}"`);
}
