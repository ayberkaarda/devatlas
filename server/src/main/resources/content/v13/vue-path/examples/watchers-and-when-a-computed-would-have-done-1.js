// Two watchers, two different ideas about what a dependency is.
//
// Vue 3.5's watchers guide: watch "only tracks the explicitly watched source. It
// won't track anything accessed inside the callback", while watchEffect
// "automatically tracks every reactive property accessed during its synchronous
// execution". The counters below are that sentence, run.
//
// A model of the documented behaviour, built on the documented mechanism.

const buckets = new WeakMap();
let activeEffect = null;

function track(target, key) {
  if (!activeEffect) return;
  let keys = buckets.get(target);
  if (!keys) buckets.set(target, (keys = new Map()));
  let subscribers = keys.get(key);
  if (!subscribers) keys.set(key, (subscribers = new Set()));
  subscribers.add(activeEffect);
  // Remembered so that a re-run can drop the subscriptions it no longer needs.
  activeEffect.subscriptions.push(subscribers);
}

function trigger(target, key) {
  const subscribers = buckets.get(target)?.get(key);
  if (!subscribers) return;
  [...subscribers].forEach((run) => (run.scheduler ? run.scheduler() : run()));
}

function effect(update, options = {}) {
  const run = () => {
    const previous = activeEffect;
    for (const subscribers of run.subscriptions) subscribers.delete(run);
    run.subscriptions = [];
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

function watchEffect(update) {
  return effect(update);
}

function watch(source, callback) {
  const getter = typeof source === "function" ? source : () => source.value;
  let oldValue;
  const runner = effect(getter, {
    lazy: true,
    scheduler: () => {
      const newValue = runner();
      if (newValue !== oldValue) {
        const previous = oldValue;
        oldValue = newValue;
        callback(newValue, previous); // read outside any active effect
      }
    },
  });
  oldValue = runner();
  return runner;
}

const invoice = reactive({ price: 100, taxRate: 0.2 });

let watchEffectRuns = 0;
watchEffect(() => {
  watchEffectRuns++;
  void invoice.price;
  void invoice.taxRate;
});

let watchCallbacks = 0;
watch(
  () => invoice.price,
  () => {
    watchCallbacks++;
    void invoice.taxRate; // read inside the callback, not part of the source
  },
);

const callbacksAtCreation = watchCallbacks;
console.log(`on creation: watchEffect runs ${watchEffectRuns}, watch callbacks ${watchCallbacks}`);

invoice.price = 120;
console.log(`price changed: watchEffect runs ${watchEffectRuns}, watch callbacks ${watchCallbacks}`);

const callbacksBefore = watchCallbacks;
invoice.taxRate = 0.21;
console.log(`taxRate changed: watchEffect runs ${watchEffectRuns}, watch callbacks ${watchCallbacks}`);

console.log(`watchEffect subscribed to both properties: ${watchEffectRuns === 3}`);
console.log(`watch ignored the value only its callback reads: ${watchCallbacks === callbacksBefore}`);
console.log(`watch did not fire its callback on creation: ${callbacksAtCreation === 0}`);
