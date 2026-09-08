// A component is a function of its props: props in, a description out.
// Nothing is painted here. `element` stands in for the object React 19 hands
// back from createElement -- a type, some props, and children -- so that the
// description can be inspected without a renderer.

function element(type, props, children = []) {
  return { type, props, children };
}

function Greeting({ name, punctuation = "!" }) {
  return element("h1", {}, ["Hello, " + name + punctuation]);
}

function Card({ person }) {
  return element("section", { className: "card" }, [
    Greeting({ name: person.name }),
    element("p", {}, ["Age " + person.age]),
  ]);
}

// A description is a tree of plain objects, so anything can walk it.
function toText(node) {
  if (typeof node === "string") {
    return node;
  }
  const inner = node.children.map(toText).join("");
  return "<" + node.type + ">" + inner + "</" + node.type + ">";
}

const taylor = { name: "Taylor", age: 31 };

const first = Card({ person: taylor });
const second = Card({ person: taylor });

console.log(toText(first));
console.log(`same props, same output: ${toText(first) === toText(second)}`);
console.log(`each call built its own description: ${first !== second}`);
console.log(`default prop applied: ${toText(Greeting({ name: "Sam" }))}`);
console.log(`props the child received: ${JSON.stringify(first.props)}`);
