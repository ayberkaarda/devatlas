// Not destructure-friendly: what a local variable keeps and what it drops.
//
// Vue 3.5's reactivity fundamentals guide lists this as the third limitation of
// reactive(): "when we destructure a reactive object's primitive type property
// into local variables, or when we pass that property into a function, we will
// lose the reactivity connection." The reactivity-in-depth guide adds the half
// people miss: "if the variable points to a non-primitive value such as an
// object, mutating the object would still be reactive."
//
// The core below is the mechanism those sentences describe, made runnable.

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

const state = reactive({ temperature: 20, sensor: { label: "roof" } });

// A primitive property copied out, and an object property copied out.
const { temperature, sensor } = state;

let runsOnCopy = 0;
effect(() => {
  runsOnCopy++;
  void temperature; // a plain number; there is no proxy left to ask
});

let runsOnProperty = 0;
effect(() => {
  runsOnProperty++;
  void state.temperature; // the property access the proxy can see
});

let runsOnNested = 0;
effect(() => {
  runsOnNested++;
  void sensor.label; // still a proxy, so still tracked
});

state.temperature = 21;
sensor.label = "basement";

console.log(`copied primitive: effect runs stayed at ${runsOnCopy}`);
console.log(`property access: effect runs reached ${runsOnProperty}`);
console.log(`copied object: effect runs reached ${runsOnNested}`);
console.log(`the copy still reads the old value: ${temperature === 20}`);
console.log(`the source moved on: ${state.temperature === 21}`);
console.log(`mutating a copied object is still seen: ${runsOnNested === 2}`);
