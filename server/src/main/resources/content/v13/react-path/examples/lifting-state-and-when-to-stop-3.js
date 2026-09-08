// Where to stop. Rendering is recursive, so the component that holds a piece of
// state decides how much of the tree runs again when that state changes. Moving
// a search box's text to the root because "everything might need it one day"
// buys a re-render of everything, every keystroke.

const tree = {
  name: "App",
  children: [
    {
      name: "Sidebar",
      children: [
        { name: "SearchBox", children: [] },
        { name: "Filters", children: [] },
      ],
    },
    {
      name: "Results",
      children: [
        { name: "ResultRow", children: [] },
        { name: "ResultRow", children: [] },
        { name: "ResultRow", children: [] },
        { name: "Pagination", children: [] },
      ],
    },
  ],
};

function find(node, name) {
  if (node.name === name) {
    return node;
  }
  for (const child of node.children) {
    const hit = find(child, name);
    if (hit) {
      return hit;
    }
  }
  return null;
}

const size = (node) => 1 + node.children.reduce((total, child) => total + size(child), 0);

console.log(`components in the tree: ${size(tree)}`);
console.log(`draft text held in App, components rendered per keystroke: ${size(find(tree, "App"))}`);
console.log(`draft text held in Sidebar, components rendered per keystroke: ${size(find(tree, "Sidebar"))}`);
console.log(`draft text held in SearchBox, components rendered per keystroke: ${size(find(tree, "SearchBox"))}`);

// The question that decides where it belongs is not performance but ownership:
// which components read this value? A value read by one component belongs to it.
const readers = {
  draftText: ["SearchBox"],
  submittedQuery: ["SearchBox", "Results", "Pagination"],
};

// The lowest node that still contains every reader is where the value belongs.
function lowestOwner(node, names) {
  const child = node.children.find((candidate) => names.every((name) => find(candidate, name)));
  return child ? lowestOwner(child, names) : node;
}

for (const [value, names] of Object.entries(readers)) {
  const owner = lowestOwner(tree, names);
  console.log(`${value} is read by ${names.length} component(s); it belongs in ${owner.name}`);
  console.log(`  components rendered when it changes: ${size(owner)}`);
}
