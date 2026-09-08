// The dependency map, printed.
//
// Vue 3.5's reactivity-in-depth guide gives the mechanism as pseudo-code: a
// proxy whose `get` calls `track(target, key)` and whose `set` calls
// `trigger(target, key)`, with an effect that "sets itself as the current active
// effect before running the actual update" so that "track() calls during the
// update" can find it. Everything the system knows lives in that map. This
// listing builds the map and prints it, so "what it can see" stops being a
// figure of speech. It is a model of the documented mechanism.

let activeEffect = null;
const buckets = new WeakMap();
const targetNames = new WeakMap();

function track(target, key) {
  if (!activeEffect) return;
  let keys = buckets.get(target);
  if (!keys) buckets.set(target, (keys = new Map()));
  let subscribers = keys.get(key);
  if (!subscribers) keys.set(key, (subscribers = new Set()));
  subscribers.add(activeEffect);
  activeEffect.reads.add(`${targetNames.get(target)}.${String(key)}`);
}

function trigger(target, key) {
  const subscribers = buckets.get(target)?.get(key);
  if (subscribers) [...subscribers].forEach((run) => run());
}

function effect(update, label) {
  const run = () => {
    const previous = activeEffect;
    activeEffect = run;
    run.reads = new Set();
    try {
      update();
    } finally {
      activeEffect = previous;
    }
  };
  run.label = label;
  run.reads = new Set();
  run();
  return run;
}

function reactive(object, name) {
  targetNames.set(object, name);
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

const user = reactive({ firstName: "Ada", lastName: "Lovelace", age: 36 }, "user");
const ui = reactive({ showAge: false }, "ui");

let runs = 0;
const greeting = effect(() => {
  runs++;
  const parts = [user.firstName];
  if (ui.showAge) {
    parts.push(String(user.age));
  }
  return parts.join(" ");
}, "greeting");

console.log(`tracked after run 1: ${[...greeting.reads].sort().join(", ")}`);
console.log(`runs: ${runs}`);

user.lastName = "King";
console.log(`after user.lastName changed, runs: ${runs}`);

user.firstName = "Grace";
console.log(`after user.firstName changed, runs: ${runs}`);

user.age = 37;
console.log(`after user.age changed, runs: ${runs}`);

console.log(`a property the effect never read is invisible to it: ${runs === 2}`);
console.log(`tracked after the last run: ${[...greeting.reads].sort().join(", ")}`);
