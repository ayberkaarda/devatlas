// The test that broke when nothing a user can see had changed.
//
// Vue 3.5's testing guide is explicit about the failure mode: "Don't assert the
// private state of a component instance or test the private methods of a
// component. Testing implementation details makes the tests brittle, as they are
// more likely to break and require updates when the implementation changes."
//
// Below, one component is refactored so that its rendered output and its emitted
// events are byte-for-byte identical, and only its internals move. Two test
// suites run against both versions.

const assert = require("node:assert/strict");

function mount(component, props) {
  const emitted = [];
  const instance = component(props, {
    emit: (event, ...payload) => emitted.push([event, ...payload]),
  });
  return { instance, emitted, html: instance.html };
}

// Version 1: keeps a filtered copy in a field.
function TagListBefore(props, { emit }) {
  const visibleTags = props.tags.filter((tag) => tag.startsWith(props.prefix));
  return {
    visibleTags, // internal: a field the first test suite reaches into
    html: `<ul>${visibleTags.map((tag) => `<li>${tag}</li>`).join("")}</ul>`,
    select: () => emit("select", visibleTags[0]),
  };
}

// Version 2: computes the same list on demand, keeps no field.
function TagListAfter(props, { emit }) {
  const visible = () => props.tags.filter((tag) => tag.startsWith(props.prefix));
  return {
    html: `<ul>${visible().map((tag) => `<li>${tag}</li>`).join("")}</ul>`,
    select: () => emit("select", visible()[0]),
  };
}

const props = { tags: ["vue-core", "vue-router", "vite"], prefix: "vue" };

function behaviourSuite(component) {
  const wrapper = mount(component, props);
  assert.equal(
    wrapper.html,
    "<ul><li>vue-core</li><li>vue-router</li></ul>",
    "rendered output changed",
  );
  wrapper.instance.select();
  assert.deepEqual(wrapper.emitted, [["select", "vue-core"]], "emitted events changed");
}

function internalsSuite(component) {
  const wrapper = mount(component, props);
  assert.ok(Array.isArray(wrapper.instance.visibleTags), "visibleTags is not an array");
  assert.equal(wrapper.instance.visibleTags.length, 2, "visibleTags has the wrong length");
}

function report(label, suite, component) {
  try {
    suite(component);
    console.log(`${label}: PASS`);
    return true;
  } catch (error) {
    console.log(`${label}: FAIL (${error.message.split(/\r?\n/)[0]})`);
    return false;
  }
}

console.log("--- before the refactor ---");
report("behaviour suite", behaviourSuite, TagListBefore);
report("internals suite", internalsSuite, TagListBefore);

console.log("--- after the refactor ---");
const behaviourAfter = report("behaviour suite", behaviourSuite, TagListAfter);
const internalsAfter = report("internals suite", internalsSuite, TagListAfter);

const before = mount(TagListBefore, props);
const after = mount(TagListAfter, props);
before.instance.select();
after.instance.select();

console.log(`rendered output identical across the refactor: ${before.html === after.html}`);
console.log(
  `emitted events identical across the refactor: ` +
    `${JSON.stringify(before.emitted) === JSON.stringify(after.emitted)}`,
);
console.log(`the behaviour suite survived: ${behaviourAfter}`);
console.log(`the internals suite did not: ${!internalsAfter}`);
