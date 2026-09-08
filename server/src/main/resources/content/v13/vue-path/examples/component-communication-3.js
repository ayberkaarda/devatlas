// An emitted event goes one level. That is the whole rule.
//
// Vue 3.5's events guide: "Unlike native DOM events, component emitted events do
// not bubble. You can only listen to the events emitted by a direct child
// component. If there is a need to communicate between sibling or deeply nested
// components, use an external event bus or a global state management solution."
//
// Three levels of component below, and a counter on each listener.

function mount(component, props, listeners = {}) {
  const emit = (event, ...payload) => {
    const handler = listeners[event];
    if (handler) handler(...payload);
  };
  return component(props, { emit });
}

const calls = { grandparent: 0, parent: 0 };

// Level 3: emits and knows nothing about who is listening.
function RowActions(props, { emit }) {
  return {
    html: `<button>delete ${props.id}</button>`,
    click: () => emit("delete", props.id),
  };
}

// Level 2, first version: listens, and keeps the event to itself.
function SilentRow(props) {
  const child = mount(RowActions, { id: props.id }, {
    delete: () => {
      calls.parent++;
    },
  });
  return child;
}

// Level 2, second version: listens, then re-emits so the level above can act.
function ForwardingRow(props, { emit }) {
  const child = mount(RowActions, { id: props.id }, {
    delete: (id) => {
      calls.parent++;
      emit("delete", id);
    },
  });
  return child;
}

const listeners = {
  delete: () => {
    calls.grandparent++;
  },
};

const silent = mount(SilentRow, { id: 7 }, listeners);
console.log(`rendered: ${silent.html}`);
silent.click();
console.log(`after the grandchild emitted: parent ${calls.parent}, grandparent ${calls.grandparent}`);
console.log(`the event skipped a level: ${calls.grandparent === 0}`);

const forwarding = mount(ForwardingRow, { id: 8 }, listeners);
forwarding.click();
console.log(`with a re-emit: parent ${calls.parent}, grandparent ${calls.grandparent}`);
console.log(`re-emitting is what carries it up: ${calls.grandparent === 1}`);
console.log(`each hop is a listener somebody wrote: ${calls.parent === 2}`);
