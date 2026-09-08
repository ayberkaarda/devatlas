// Why a composable returns refs and not a reactive object.
//
// Vue 3.5's composables guide states the convention and the reason together:
// "It is recommended to always return a plain object containing refs from
// composables, so that it can be destructured in components while retaining
// reactivity", and "returning a reactive object from a composable will cause
// destructures to lose the reactivity connection to the state inside the
// composable, while the refs will retain that connection."
//
// Both halves are measured below, plus the escape hatch for the object case.

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

function ref(initial) {
  let stored = initial;
  const box = {
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

function reactive(object) {
  return new Proxy(object, {
    get(target, key) {
      track(target, key);
      return Reflect.get(target, key);
    },
    set(target, key, value) {
      const accepted = Reflect.set(target, key, value);
      trigger(target, key);
      return accepted;
    },
  });
}

// One ref per property, standing in for the source object's properties.
function toRefs(source) {
  const out = {};
  for (const key of Object.keys(source)) {
    out[key] = {
      get value() {
        return source[key];
      },
      set value(next) {
        source[key] = next;
      },
    };
  }
  return out;
}

function useSearchWithRefs() {
  const term = ref("");
  const results = ref(0);
  const search = (text) => {
    term.value = text;
    results.value = text.length;
  };
  return { term, results, search };
}

function useSearchWithReactive() {
  const state = reactive({ term: "", results: 0 });
  const search = (text) => {
    state.term = text;
    state.results = text.length;
  };
  return { state, search };
}

// Destructured from a composable returning refs.
const { term, search: searchRefs } = useSearchWithRefs();
let refRuns = 0;
effect(() => {
  refRuns++;
  void term.value;
});
searchRefs("proxy");
console.log(`destructured ref, effect runs: ${refRuns}`);
console.log(`the destructured ref sees the new value: ${term.value === "proxy"}`);

// Destructured out of a reactive object.
const objectComposable = useSearchWithReactive();
const { term: copiedTerm } = objectComposable.state;
let copyRuns = 0;
effect(() => {
  copyRuns++;
  void copiedTerm;
});
objectComposable.search("reflect");
console.log(`destructured reactive property, effect runs: ${copyRuns}`);
console.log(`the copy still holds the old value: ${copiedTerm === ""}`);

// The same object, converted first.
const { term: convertedTerm } = toRefs(objectComposable.state);
let convertedRuns = 0;
effect(() => {
  convertedRuns++;
  void convertedTerm.value;
});
objectComposable.search("effect");
console.log(`after toRefs, effect runs: ${convertedRuns}`);
console.log(`refs survive destructuring: ${refRuns === 2 && convertedRuns === 2}`);
console.log(`a plain property copy does not: ${copyRuns === 1}`);
