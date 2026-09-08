// Two ways to hold state, built from the mechanism Vue 3.5 documents.
//
// The "Reactivity in Depth" guide gives the shape in pseudo-code: "In Vue 3,
// Proxies are used for reactive objects and getter / setters are used for refs."
// What follows is that pseudo-code made runnable, so the difference between the
// two containers can be watched rather than described. It is a model of the
// documented mechanism, not Vue's shipped implementation.

let activeEffect = null;
const buckets = new WeakMap();

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

function reactive(object) {
  if (object === null || typeof object !== "object") {
    // Vue's documented limitation: reactive() "only works for object types".
    // Handed a primitive it gives the value straight back, unwrapped.
    return object;
  }
  return new Proxy(object, {
    get(target, key) {
      track(target, key);
      const value = Reflect.get(target, key);
      return typeof value === "object" && value !== null ? reactive(value) : value;
    },
    set(target, key, value) {
      const accepted = Reflect.set(target, key, value);
      trigger(target, key);
      return accepted;
    },
  });
}

function ref(initial) {
  let stored = typeof initial === "object" && initial !== null ? reactive(initial) : initial;
  const box = {
    get value() {
      track(box, "value");
      return stored;
    },
    set value(next) {
      stored = typeof next === "object" && next !== null ? reactive(next) : next;
      trigger(box, "value");
    },
  };
  return box;
}

const count = ref(0);
const profile = ref({ address: { city: "Delft" } });
const state = reactive({ temperature: 20 });
const notReactive = reactive(20);

let runs = 0;
effect(() => {
  runs++;
  void count.value;
  void state.temperature;
  void profile.value.address.city;
});

console.log(`effect ran on creation: ${runs}`);
count.value = 1;
console.log(`after count.value = 1, runs: ${runs}`);
state.temperature = 21;
console.log(`after state.temperature = 21, runs: ${runs}`);
profile.value.address.city = "Utrecht";
console.log(`after a nested mutation through a ref, runs: ${runs}`);

console.log(`a ref can hold a primitive: ${count.value === 1}`);
console.log(`reactive() given a number hands it back unwrapped: ${notReactive === 20}`);
console.log(`a ref is deeply reactive: ${profile.value.address.city === "Utrecht"}`);
