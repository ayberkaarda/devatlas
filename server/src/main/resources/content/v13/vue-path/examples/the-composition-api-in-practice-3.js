// Accepting an argument that might be a value, a ref, or a getter.
//
// Vue 3.5's composables guide: "If you are writing a composable that may be used
// by other developers, it's a good idea to handle the case of input arguments
// being refs or getters instead of raw values", using the toValue() utility.
//
// The point is not the one-line helper. It is that a composable which normalises
// its input keeps working when the caller's value changes, and one that reads a
// raw number once is frozen at the moment it was called.

const buckets = new WeakMap();
let activeEffect = null;

function track(target, key) {
  if (!activeEffect) return;
  let keys = buckets.get(target);
  if (!keys) buckets.set(target, (keys = new Map()));
  let subscribers = keys.get(key);
  if (!subscribers) keys.set(key, (subscribers = new Set()));
  subscribers.add(activeEffect);
}

function trigger(target, key) {
  const subscribers = buckets.get(target)?.get(key);
  if (subscribers) [...subscribers].forEach((run) => run());
}

function effect(update) {
  const run = () => {
    const previous = activeEffect;
    activeEffect = run;
    try {
      update();
    } finally {
      activeEffect = previous;
    }
  };
  run();
  return run;
}

const IS_REF = Symbol("isRef");

function ref(initial) {
  let stored = initial;
  const box = {
    [IS_REF]: true,
    get value() {
      track(box, "value");
      return stored;
    },
    set value(next) {
      if (next === stored) return;
      stored = next;
      trigger(box, "value");
    },
  };
  return box;
}

function isRef(candidate) {
  return Boolean(candidate && candidate[IS_REF]);
}

function toValue(source) {
  if (typeof source === "function") return source();
  if (isRef(source)) return source.value;
  return source;
}

// Normalising, so the argument is read every time the effect runs.
function usePageTitle(maybeRefOrGetter) {
  const seen = [];
  effect(() => seen.push(`Page ${toValue(maybeRefOrGetter)}`));
  return seen;
}

// Not normalising: the argument is captured once, at call time.
function usePageTitleFrozen(page) {
  const seen = [];
  effect(() => seen.push(`Page ${page}`));
  return seen;
}

console.log(`toValue(3): ${toValue(3)}`);
console.log(`toValue(ref(3)): ${toValue(ref(3))}`);
console.log(`toValue(() => 3): ${toValue(() => 3)}`);

const page = ref(1);

const normalised = usePageTitle(page);
const fromGetter = usePageTitle(() => page.value);
const frozen = usePageTitleFrozen(page.value);

page.value = 2;
page.value = 3;

console.log(`ref argument, titles seen: ${normalised.join(" | ")}`);
console.log(`getter argument, titles seen: ${fromGetter.join(" | ")}`);
console.log(`raw argument, titles seen: ${frozen.join(" | ")}`);
console.log(`normalising kept the composable live: ${normalised.length === 3}`);
console.log(`a getter argument works the same way: ${fromGetter.length === 3}`);
console.log(`a raw number was read once: ${frozen.length === 1}`);
