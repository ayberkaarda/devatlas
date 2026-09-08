// A computed value is lazy and it caches. Here is the machinery that makes it so.
//
// Vue 3.5's guide: "Computed properties are cached based on their reactive
// dependencies. A computed property will only re-evaluate when some of its
// reactive dependencies have changed." Caching needs three parts -- a stored
// value, a dirty flag, and a subscription that sets the flag -- and all three
// fit in the model below. It models the documented behaviour; it is not Vue's
// shipped implementation.

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
      return Reflect.get(target, key);
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
        trigger(box, "value"); // tell whoever read us that the snapshot is stale
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

const cart = reactive({ price: 2, quantity: 3 });

let getterCalls = 0;
const total = computed(() => {
  getterCalls++;
  return cart.price * cart.quantity;
});

console.log(`getter calls before anyone reads it: ${getterCalls}`);
console.log(`first read: ${total.value} (getter calls: ${getterCalls})`);
console.log(`second read: ${total.value} (getter calls: ${getterCalls})`);
console.log(`third read: ${total.value} (getter calls: ${getterCalls})`);

cart.quantity = 5;
console.log(`after the dependency changed, getter calls: ${getterCalls}`);
console.log(`read after the change: ${total.value} (getter calls: ${getterCalls})`);
console.log(`read again: ${total.value} (getter calls: ${getterCalls})`);

let dependentRuns = 0;
effect(() => {
  dependentRuns++;
  void total.value;
});
cart.price = 4;
console.log(`an effect reading the computed re-ran: ${dependentRuns === 2}`);
console.log(`and sees the new answer: ${total.value === 20}`);
