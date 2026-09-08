// What a key really protects. React 19 keeps a component's state for as long as
// the same component is rendered at the same position, and a key is what tells
// it which position an item now occupies. Anything the instance owns and the
// data does not -- a half-typed input, a scroll offset, an open menu -- follows
// the key, so a key that points at a different row moves that state with it.

function renderList(items, keyOf, instanceState) {
  return items.map((item, index) => {
    const key = keyOf(item, index);
    if (!instanceState.has(key)) {
      instanceState.set(key, { draft: "" });
    }
    return { key, label: item.text, instance: instanceState.get(key) };
  });
}

const byId = (item) => item.id;
const byIndex = (_item, index) => String(index);

function run(keyOf, label) {
  const state = new Map();
  const before = [
    { id: "ada", text: "Ada Lovelace" },
    { id: "grace", text: "Grace Hopper" },
  ];

  // The reader types into the row for Grace Hopper.
  const firstPass = renderList(before, keyOf, state);
  firstPass[1].instance.draft = "reply to Grace";

  // A row is inserted at the front and the list renders again.
  const after = [{ id: "linus", text: "Linus Pauling" }, ...before];
  const secondPass = renderList(after, keyOf, state);

  const holder = secondPass.find((row) => row.instance.draft === "reply to Grace");
  console.log(`${label}: the draft is now attached to "${holder.label}"`);
  console.log(`${label}: draft stayed with Grace Hopper: ${holder.label === "Grace Hopper"}`);
}

run(byId, "id keys   ");
run(byIndex, "index keys");
