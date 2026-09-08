// What you may hand watch(), and the shape that silently does nothing.
//
// Vue 3.5's watchers guide shows the mistake directly -- watch(obj.count, ...)
// with the comment "this won't work because we are passing a number to watch()"
// -- and the fix, watch(() => obj.count, ...). It also warns that when the
// source is a reactive object, "newValue will equal oldValue ... because they
// are the same object".
//
// The model below reproduces both from the documented mechanism.

const buckets = new WeakMap();
let activeEffect = null;

function track(target, key) {
  if (!activeEffect) return;
  let keys = buckets.get(target);
  if (!keys) buckets.set(target, (keys = new Map()));
  let subscribers = keys.get(key);
  if (!subscribers) keys.set(key, (subscribers = new Set()));
  subscribers.add(activeEffect);
  activeEffect.subscriptions.push(subscribers);
}

function trigger(target, key) {
  const subscribers = buckets.get(target)?.get(key);
  if (!subscribers) return;
  [...subscribers].forEach((run) => (run.scheduler ? run.scheduler() : run()));
}

function effect(update, options = {}) {
  const run = () => {
    for (const subscribers of run.subscriptions) subscribers.delete(run);
    run.subscriptions = [];
    const previous = activeEffect;
    activeEffect = run;
    try {
      return update();
    } finally {
      activeEffect = previous;
    }
  };
  run.subscriptions = [];
  run.scheduler = options.scheduler;
  if (!options.lazy) run();
  return run;
}

const REACTIVE = Symbol("isReactive");

function reactive(object) {
  return new Proxy(object, {
    get(target, key) {
      if (key === REACTIVE) return true;
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

// Touching every nested property is how a deep watcher subscribes to all of them.
function traverse(value, seen = new Set()) {
  if (typeof value !== "object" || value === null || seen.has(value)) return value;
  seen.add(value);
  for (const key of Object.keys(value)) traverse(value[key], seen);
  return value;
}

function watch(source, callback) {
  let getter;
  let deep = false;
  if (typeof source === "function") {
    getter = source;
  } else if (source && source[REACTIVE]) {
    getter = () => traverse(source); // a reactive object source watches deeply
    deep = true;
  } else if (source && typeof source === "object" && "value" in source) {
    getter = () => source.value;
  } else {
    getter = () => source; // a plain value: nothing to subscribe to
  }

  let oldValue;
  const runner = effect(getter, {
    lazy: true,
    scheduler: () => {
      const newValue = runner();
      if (deep || newValue !== oldValue) {
        const previous = oldValue;
        oldValue = newValue;
        callback(newValue, previous);
      }
    },
  });
  oldValue = runner();
}

const obj = reactive({ count: 0, nested: { flag: false } });

let wrong = 0;
watch(obj.count, () => wrong++); // a number was passed, not a source

let right = 0;
watch(
  () => obj.count,
  () => right++,
);

let deepFires = 0;
let sameObject = null;
watch(obj, (newValue, oldValue) => {
  deepFires++;
  sameObject = newValue === oldValue;
});

obj.count = 1;
console.log(`watch(obj.count, ...) fired: ${wrong}`);
console.log(`watch(() => obj.count, ...) fired: ${right}`);
console.log(`watch(obj, ...) fired: ${deepFires}`);

obj.nested.flag = true;
console.log(`after a nested mutation, the getter watcher fired: ${right}`);
console.log(`after a nested mutation, the deep watcher fired: ${deepFires}`);
console.log(`deep watcher saw newValue === oldValue: ${sameObject}`);
console.log(`passing a plain value subscribes to nothing: ${wrong === 0}`);
