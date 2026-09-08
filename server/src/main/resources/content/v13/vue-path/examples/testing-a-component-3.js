// Some composables are testable as functions. Some need a component around them.
//
// Vue 3.5's testing guide draws the line: composables "that don't rely on
// lifecycle hooks or Provide/Inject can be tested by directly calling it and
// asserting its returned state/methods", while "composables that rely on
// lifecycle hooks or Provide/Inject need to be wrapped in a host component to be
// tested".
//
// The model below has a current-instance register, because that is what makes a
// lifecycle hook work at all: onMounted looks for an instance to attach to, and
// outside one there is nothing to attach to.

const assert = require("node:assert/strict");

let currentInstance = null;
const warnings = [];

function onMounted(hook) {
  if (!currentInstance) {
    warnings.push("onMounted is called when there is no active component instance.");
    return;
  }
  currentInstance.mountedHooks.push(hook);
}

function ref(initial) {
  return { value: initial };
}

// No lifecycle hook: a plain function with state.
function useCounter(start = 0) {
  const count = ref(start);
  return { count, increment: () => (count.value += 1) };
}

// With a lifecycle hook: nothing happens until something mounts it.
function useLoadedFlag() {
  const loaded = ref(false);
  onMounted(() => {
    loaded.value = true;
  });
  return { loaded };
}

// The host component a test wraps the second composable in.
function mountWith(setup) {
  const instance = { mountedHooks: [] };
  const previous = currentInstance;
  currentInstance = instance;
  let exposed;
  try {
    exposed = setup();
  } finally {
    currentInstance = previous;
  }
  instance.mountedHooks.forEach((hook) => hook());
  return exposed;
}

// Only the first line: an assertion library's diff formatting is its own.
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

test("useCounter can be called directly", () => {
  const { count, increment } = useCounter();
  assert.equal(count.value, 0, "did not start at zero");
  increment();
  increment();
  assert.equal(count.value, 2, "did not count up");
});

test("useCounter honours its starting value", () => {
  const { count } = useCounter(41);
  assert.equal(count.value, 41, "ignored the argument");
});

test("useLoadedFlag called directly never runs its hook", () => {
  const { loaded } = useLoadedFlag();
  assert.equal(loaded.value, true, "loaded was still false");
});

test("useLoadedFlag inside a host component does", () => {
  const { loaded } = mountWith(() => useLoadedFlag());
  assert.equal(loaded.value, true, "loaded was still false");
});

console.log(`tests run: ${run}, failures: ${failed}`);
console.log(`warnings raised while running: ${warnings.length}`);
console.log(`first warning: ${warnings[0]}`);
console.log(`a lifecycle hook needs an instance to attach to: ${warnings.length === 1}`);
