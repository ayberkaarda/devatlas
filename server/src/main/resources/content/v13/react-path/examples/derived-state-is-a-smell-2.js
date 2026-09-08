// The stored value that points at something which no longer exists. A list
// screen keeps the selected item in state; the list changes underneath it; the
// selection is now a reference to a row that is gone. Storing the identifier
// and looking the row up during render cannot go stale, because there is
// nothing left to go stale.

const version1 = [
  { id: 1, title: "Bread" },
  { id: 2, title: "Cheese" },
  { id: 3, title: "Olives" },
];
const version2 = [
  { id: 1, title: "Bread" },
  { id: 3, title: "Olives" },
];

// Storing the item itself.
const selectedItem = version1[1]; // the reader picked Cheese
const items = version2; // the list reloaded and Cheese is no longer stocked
console.log(`stored item still reads: ${selectedItem.title}`);
console.log(`stored item is still in the list: ${items.includes(selectedItem)}`);

// Storing the identifier and resolving during render.
const selectedId = 2;
const resolved = version2.find((item) => item.id === selectedId) ?? null;
console.log(`resolved during render: ${resolved === null ? "nothing selected" : resolved.title}`);

// If the screen genuinely has to clear the selection when the list changes,
// React 19 allows setting state during render from the component that owns it.
// The pattern is to keep the previous props alongside and compare.
function render({ items: currentItems, previousItems, selection }) {
  const frames = [];
  let prev = previousItems;
  let selected = selection;
  if (currentItems !== prev) {
    prev = currentItems;
    selected = null;
    frames.push("discarded render: selection cleared, render again immediately");
  }
  frames.push(`committed render: selection is ${selected === null ? "empty" : selected}`);
  return frames;
}

for (const line of render({ items: version2, previousItems: version1, selection: 2 })) {
  console.log(line);
}
for (const line of render({ items: version2, previousItems: version2, selection: 3 })) {
  console.log(line);
}
