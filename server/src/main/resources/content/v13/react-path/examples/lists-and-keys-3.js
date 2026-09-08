// Two ways to write a key that cannot do its job: one that is different on
// every render, and one that repeats among siblings. Both are measured here
// rather than argued about.

function matchByKey(previous, next) {
  const byKey = new Map(previous.map((child) => [child.key, child]));
  let reused = 0;
  for (const child of next) {
    if (byKey.has(child.key)) {
      reused += 1;
    }
  }
  return reused;
}

const rows = [
  { id: "ada", text: "Ada Lovelace" },
  { id: "grace", text: "Grace Hopper" },
  { id: "linus", text: "Linus Pauling" },
];

// A key generated during render. The value is different every time, so nothing
// matches anything and every instance is thrown away and rebuilt.
let counter = 0;
const generated = (items) => items.map((item) => ({ key: `row-${counter++}`, text: item.text }));
const firstPass = generated(rows);
const secondPass = generated(rows);
console.log(`keys generated during render, instances reused: ${matchByKey(firstPass, secondPass)} of ${rows.length}`);

// A stable key derived from the data. The list did not change, so everything
// matches and nothing is rebuilt.
const stable = (items) => items.map((item) => ({ key: item.id, text: item.text }));
console.log(`stable keys, instances reused: ${matchByKey(stable(rows), stable(rows))} of ${rows.length}`);

// Keys must be unique among siblings. A repeated key collapses two rows into
// one slot, so one of them can never be matched.
const duplicated = [
  { key: "person", text: "Ada Lovelace" },
  { key: "person", text: "Grace Hopper" },
];
const slots = new Set(duplicated.map((child) => child.key));
console.log(`rows rendered: ${duplicated.length}, distinct keys: ${slots.size}`);
console.log(`every row has a slot of its own: ${slots.size === duplicated.length}`);

// The same rule read the other way: keys only have to be unique among the
// children of one parent, so two separate lists may reuse the same key.
const menu = stable(rows);
const sidebar = stable(rows);
console.log(`the two lists share every key: ${menu.every((row, i) => row.key === sidebar[i].key)}`);
