// Cannot replace the whole object, and the identity that is not the raw one.
//
// Vue 3.5's reactivity fundamentals guide: "since Vue's reactivity tracking
// works over property access, we must always keep the same reference to the
// reactive object. This means we can't easily 'replace' a reactive object
// because the reactivity connection to the first reference is lost." The
// reactivity-in-depth guide adds that the returned proxy "has a different
// identity if we compare it to the original using the === operator".
//
// Both are consequences of the documented mechanism, modelled here.

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
      return Reflect.get(target, key);
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

const raw = { total: 1 };
let basket = reactive(raw);

let replacedRuns = 0;
effect(() => {
  replacedRuns++;
  void basket.total;
});

const first = basket;

// The rebinding a reader reaches for when a server sends a fresh object.
basket = reactive({ total: 99 });
console.log(`after rebinding the variable, effect runs: ${replacedRuns}`);

basket.total = 100;
console.log(`after mutating the new object, effect runs: ${replacedRuns}`);

first.total = 2;
console.log(`after mutating the first object, effect runs: ${replacedRuns}`);

// The same job done through a ref, which has one property to replace.
const held = ref({ total: 1 });
let heldRuns = 0;
effect(() => {
  heldRuns++;
  void held.value.total;
});

held.value = { total: 99 };
console.log(`after replacing a ref's value, effect runs: ${heldRuns}`);
console.log(`the ref reports the new object: ${held.value.total === 99}`);

console.log(`reactive(raw) === raw: ${reactive(raw) === raw}`);
console.log(`the proxy reads through to the source: ${reactive(raw).total === raw.total}`);
