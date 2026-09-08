// Props down, events up, in the smallest system that has both.
//
// Vue 3.5's props guide: "All props form a one-way-down binding between the
// child property and the parent one: when the parent property updates, it will
// flow down to the child, but not the other way around." The events guide adds
// the return path: a child calls emit, and "the parent can then listen to it
// using v-on".
//
// A component here is a function of its props that returns a string, and a
// parent is a function that renders a child. That is enough to watch the two
// directions separately. It models the documented contract, not Vue's runtime.

function mount(component, props, listeners = {}) {
  const emit = (event, ...payload) => {
    const handler = listeners[event];
    if (handler) handler(...payload);
  };
  return component(props, { emit });
}

// The child. It owns no state; it renders what it was given and asks for changes.
function CounterButton(props, { emit }) {
  return {
    html: `<button>${props.label}: ${props.count}</button>`,
    click: () => emit("increment", 1),
  };
}

// The parent. It owns the state and decides what an event does.
function ShoppingRow() {
  let count = 0;
  const accepted = [];

  const render = () =>
    mount(
      CounterButton,
      { label: "Apples", count },
      {
        increment: (by) => {
          accepted.push(by);
          count += by;
        },
      },
    );

  return { render, state: () => count, accepted: () => accepted.length };
}

const row = ShoppingRow();

let view = row.render();
console.log(`first render:  ${view.html}`);
console.log(`parent state:  ${row.state()}`);

view.click();
console.log(`after the child emitted, parent state: ${row.state()}`);
console.log(`the parent applied the change itself: ${row.accepted() === 1}`);

view = row.render();
console.log(`second render: ${view.html}`);

view.click();
view.click();
view = row.render();
console.log(`third render:  ${view.html}`);
console.log(`the child never held the count: ${!("count" in CounterButton)}`);
console.log(`every change went through the parent: ${row.accepted() === 3}`);
