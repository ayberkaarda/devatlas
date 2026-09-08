// Mount it, drive it, and assert on what came out.
//
// Vue 3.5's testing guide: "Test what a component does, not how it does it",
// and for behavioural logic "assert correct render updates or emitted events in
// response to user input events". The public interface it names is "events
// emitted, props, and slots".
//
// The mount() below is a stand-in for a component test harness: it renders a
// component with props, records what it emitted, and lets a test push new props
// in from the parent. It is not @vue/test-utils, and it renders to a string
// rather than to a document, but the interface a test should be pushing against
// is the same one.

const assert = require("node:assert/strict");

// Only the first line: an assertion library's diff formatting is its own, and a
// listing that pins it is testing the runtime rather than the component.
const firstLine = (error) => error.message.split(/\r?\n/)[0];

let run = 0;
let failed = 0;
function test(name, body) {
  run++;
  try {
    body();
    console.log(`PASS  ${name}`);
  } catch (error) {
    failed++;
    console.log(`FAIL  ${name}: ${firstLine(error)}`);
  }
}

function mount(component, { props = {} } = {}) {
  const emitted = {};
  let current = { ...props };
  const emit = (event, ...payload) => {
    (emitted[event] ??= []).push(payload);
  };
  let instance = component(current, { emit });
  return {
    html: () => instance.html,
    emitted: (event) => (event ? (emitted[event] ?? []) : emitted),
    props: () => ({ ...current }),
    setProps(next) {
      current = { ...current, ...next };
      instance = component(current, { emit });
    },
    trigger(handler) {
      instance[handler]();
    },
  };
}

// The component under test.
function QuantityStepper(props, { emit }) {
  const disabled = props.count <= props.min;
  return {
    html:
      `<div><span>${props.label}</span>` +
      `<button${disabled ? " disabled" : ""}>-</button>` +
      `<output>${props.count}</output><button>+</button></div>`,
    increase: () => emit("update:count", props.count + 1),
    decrease: () => {
      if (!disabled) emit("update:count", props.count - 1);
    },
  };
}

test("renders the label it was given", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 2, min: 0 } });
  assert.ok(wrapper.html().includes("<span>Apples</span>"), "label missing from the output");
});

test("renders the count it was given", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 2, min: 0 } });
  assert.ok(wrapper.html().includes("<output>2</output>"), "count missing from the output");
});

test("emits update:count with the next value", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 2, min: 0 } });
  wrapper.trigger("increase");
  assert.deepEqual(wrapper.emitted("update:count"), [[3]], "wrong payload emitted");
});

test("refuses to go below the minimum", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 0, min: 0 } });
  wrapper.trigger("decrease");
  assert.equal(wrapper.emitted("update:count").length, 0, "emitted below the minimum");
  assert.ok(wrapper.html().includes("<button disabled>-</button>"), "button not disabled");
});

test("re-renders when the parent sends a new count", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 2, min: 0 } });
  wrapper.setProps({ count: 9 });
  assert.ok(wrapper.html().includes("<output>9</output>"), "output did not follow the prop");
});

test("never writes back into its own props", () => {
  const wrapper = mount(QuantityStepper, { props: { label: "Apples", count: 2, min: 0 } });
  wrapper.trigger("increase");
  assert.equal(wrapper.props().count, 2, "the component changed its own props");
});

console.log(`tests run: ${run}, failures: ${failed}`);
