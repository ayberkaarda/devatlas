// The prop you cannot assign to, and the object inside it that you can.
//
// Vue 3.5's props guide: "you should not attempt to mutate a prop inside a child
// component. If you do, Vue will warn you in the console." And the part that
// catches people: "while the child component cannot mutate the prop binding, it
// will be able to mutate the object or array's nested properties ... because in
// JavaScript objects and arrays are passed by reference, and it is unreasonably
// expensive for Vue to prevent such mutations."
//
// The read-only wrapper below models the binding; the reference semantics are
// JavaScript's own, so the second half is not a model at all.

const warnings = [];

function readonlyProps(source) {
  return new Proxy(source, {
    set(_target, key) {
      warnings.push(`Set operation on key "${String(key)}" failed: target is readonly.`);
      return true; // refused, but without throwing -- as a dev-mode warning is
    },
    deleteProperty(_target, key) {
      warnings.push(`Delete operation on key "${String(key)}" failed: target is readonly.`);
      return true;
    },
  });
}

// The parent's state. Only the parent is supposed to change it.
const parentState = {
  title: "Invoice",
  customer: { name: "Ada", city: "Delft" },
  applied: [],
};

function ChildComponent(props, { emit }) {
  // 1. Assigning to the prop binding itself.
  props.title = "Renamed by the child";

  // 2. Reaching through the prop into the object it points at.
  props.customer.city = "Utrecht";

  // 3. The way the guide recommends instead.
  emit("rename", "Renamed by request");

  return `<h1>${props.title} for ${props.customer.name}</h1>`;
}

const html = ChildComponent(readonlyProps(parentState), {
  emit: (event, payload) => {
    if (event === "rename") {
      parentState.applied.push(payload);
      parentState.title = payload;
    }
  },
});

console.log(`rendered: ${html}`);
console.log(`warnings raised: ${warnings.length}`);
console.log(`first warning: ${warnings[0]}`);
console.log(`the prop binding resisted assignment: ${parentState.title !== "Renamed by the child"}`);
console.log(`the nested object did not: ${parentState.customer.city === "Utrecht"}`);
console.log(`the parent recorded the nested change: ${parentState.applied.includes("Utrecht")}`);
console.log(`the parent recorded the emitted change: ${parentState.applied.includes("Renamed by request")}`);
console.log(`title after the parent applied the event: ${parentState.title}`);
