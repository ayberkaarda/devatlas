// Rendering is recursive: React 19 calls the component whose state changed and
// then calls the components it returns, down to the leaves. Committing is not
// recursive in the same way -- only the parts of the output that differ from
// the previous render reach the DOM.

const tree = {
  name: "App",
  children: [
    {
      name: "Header",
      children: [
        { name: "Logo", children: [] },
        { name: "Nav", children: [] },
      ],
    },
    {
      name: "Feed",
      children: [
        { name: "Post", children: [{ name: "Comments", children: [] }] },
        { name: "Sidebar", children: [] },
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

function renderedFrom(node) {
  return [node.name, ...node.children.flatMap(renderedFrom)];
}

console.log(`state changes in Feed, components rendered: ${renderedFrom(find(tree, "Feed")).join(", ")}`);
console.log(`state changes in App, components rendered: ${renderedFrom(find(tree, "App")).join(", ")}`);
console.log(`state changes in Nav, components rendered: ${renderedFrom(find(tree, "Nav")).join(", ")}`);

// The commit step. Two renders of the same three nodes, one of which changed.
const previousOutput = { Post: "3 comments", Sidebar: "Trending", Comments: "" };
const nextOutput = { Post: "4 comments", Sidebar: "Trending", Comments: "" };

const touched = Object.keys(nextOutput).filter((name) => previousOutput[name] !== nextOutput[name]);
console.log(`components rendered in that update: ${Object.keys(nextOutput).length}`);
console.log(`DOM nodes the commit had to touch: ${touched.length} (${touched.join(", ")})`);
