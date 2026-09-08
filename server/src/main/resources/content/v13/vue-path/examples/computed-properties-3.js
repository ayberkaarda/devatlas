// The cache is only as good as the dependencies the tracker could see.
//
// Vue 3.5's guide warns about exactly this: "the following computed property
// will never update, because Date.now() is not a reactive dependency". The same
// page says a computed's return value "should be treated as read-only and never
// be mutated". Both are demonstrated below with the documented mechanism
// modelled in plain JavaScript.

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
  if (!subscribers) return;
  [...subscribers].forEach((run) => (run.scheduler ? run.scheduler() : run()));
}

function effect(update, options = {}) {
  const run = () => {
    const previous = activeEffect;
    activeEffect = run;
    try {
      return update();
    } finally {
      activeEffect = previous;
    }
  };
  run.scheduler = options.scheduler;
  if (!options.lazy) run();
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

function computed(getter) {
  let value;
  let dirty = true;
  const runner = effect(() => (value = getter()), {
    lazy: true,
    scheduler: () => {
      if (!dirty) {
        dirty = true;
        trigger(box, "value");
      }
    },
  });
  const box = {
    get value() {
      if (dirty) {
        runner();
        dirty = false;
      }
      track(box, "value");
      return value;
    },
  };
  return box;
}

// 1. A source the tracker cannot see never invalidates the cache.
const stamp = computed(() => Date.now());
const firstStamp = stamp.value;
while (Date.now() === firstStamp) {
  // wait for the clock to move, so the comparison below means something
}
console.log(`the clock moved on: ${Date.now() > firstStamp}`);
console.log(`the computed did not: ${stamp.value === firstStamp}`);

// 2. A plain variable is no better: it is not a reactive dependency either.
let plainCounter = 0;
const doubled = computed(() => plainCounter * 2);
console.log(`first read of the plain-variable computed: ${doubled.value}`);
plainCounter = 21;
console.log(`after the variable changed: ${doubled.value}`);
console.log(`a plain variable never invalidates the cache: ${doubled.value === 0}`);

// 3. The returned value is a snapshot, not a place to store anything.
const state = reactive({ tags: ["vue", "proxy"] });
const upper = computed(() => state.tags.map((tag) => tag.toUpperCase()));
const snapshot = upper.value;
snapshot.push("EDITED BY HAND");
console.log(`the snapshot accepted the push: ${snapshot.length === 3}`);
state.tags.push("reflect");
console.log(`after the source changed, the computed holds: ${upper.value.join(",")}`);
console.log(`the hand edit survived: ${upper.value.includes("EDITED BY HAND")}`);
