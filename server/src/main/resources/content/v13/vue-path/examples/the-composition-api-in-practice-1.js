// A composable is a function, so its state is whatever a function's state is.
//
// Vue 3.5's composables guide: "A composable is a function that leverages Vue's
// Composition API to encapsulate and reuse stateful logic", and "each component
// instance calling useMouse() will create its own copies of x and y state so
// they won't interfere with each other".
//
// That isolation is not framework magic; it is a closure per call. The contrast
// below is with a composable that reaches for module-level state instead, which
// shares -- sometimes on purpose, often by accident.

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

// State per call: a new ref every time the function runs.
function useCounter(start = 0) {
  const count = ref(start);
  const increment = () => (count.value += 1);
  const reset = () => (count.value = start);
  return { count, increment, reset };
}

// State per module: one ref, closed over by every caller.
const sharedCount = ref(0);
function useSharedCounter() {
  const increment = () => (sharedCount.value += 1);
  return { count: sharedCount, increment };
}

const cart = useCounter();
const wishlist = useCounter(10);

console.log(`cart starts at ${cart.count.value}, wishlist at ${wishlist.count.value}`);

cart.increment();
cart.increment();
console.log(`after two increments on the cart: cart ${cart.count.value}, wishlist ${wishlist.count.value}`);
console.log(`each call built its own state: ${cart.count !== wishlist.count}`);

wishlist.reset();
console.log(`resetting the wishlist left the cart alone: ${cart.count.value === 2}`);

const headerBadge = useSharedCounter();
const footerBadge = useSharedCounter();
headerBadge.increment();
console.log(`shared composable: header ${headerBadge.count.value}, footer ${footerBadge.count.value}`);
console.log(`the shared one handed back the same ref: ${headerBadge.count === footerBadge.count}`);

let runs = 0;
effect(() => {
  runs++;
  void cart.count.value;
});
cart.increment();
console.log(`a ref returned from a composable is still reactive: ${runs === 2}`);
