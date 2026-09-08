// Two ways to find the button a test wants to click. One asks the question a
// reader would ask -- which control is called "Add to cart" -- and one describes
// how the component happened to be built. A refactor that changes nothing a
// reader can perceive is applied here, and the two queries are asked again.

const before = {
  tag: "div",
  className: "product-panel",
  children: [
    { tag: "h2", text: "Sourdough loaf" },
    { tag: "button", role: "button", name: "Add to cart", className: "btn btn-primary" },
  ],
};

// Same screen, restyled and wrapped in a layout element. The heading and the
// button still say the same words and still expose the same roles.
const after = {
  tag: "div",
  className: "ProductPanel_root__a91f",
  children: [
    {
      tag: "div",
      className: "ProductPanel_body__7c2e",
      children: [
        { tag: "h2", text: "Sourdough loaf" },
        { tag: "button", role: "button", name: "Add to cart", className: "Button_solid__11ab" },
      ],
    },
  ],
};

function walk(node, visit) {
  visit(node);
  for (const child of node.children ?? []) {
    walk(child, visit);
  }
}

function byRole(tree, role, name) {
  let found = null;
  walk(tree, (node) => {
    if (node.role === role && node.name === name) {
      found = node;
    }
  });
  return found;
}

function byClass(tree, className) {
  let found = null;
  walk(tree, (node) => {
    if ((node.className ?? "").split(" ").includes(className)) {
      found = node;
    }
  });
  return found;
}

function byPath(tree, path) {
  let node = tree;
  for (const index of path) {
    node = node.children?.[index];
    if (!node) {
      return null;
    }
  }
  return node;
}

for (const [label, tree] of [["before", before], ["after ", after]]) {
  console.log(`${label}: role "button" named "Add to cart" found: ${byRole(tree, "button", "Add to cart") !== null}`);
  console.log(`${label}: class "btn-primary" found: ${byClass(tree, "btn-primary") !== null}`);
  console.log(`${label}: second child of the root is the button: ${byPath(tree, [1])?.role === "button"}`);
}
