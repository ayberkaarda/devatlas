// Deriving a value with a watcher, and deriving it with a computed.
//
// Vue 3.5's computed guide describes a computed as "declaratively describing how
// to derive a value based on other values". Its watchers guide describes watch as
// the tool for "side effects in reaction to state changes", and notes that only
// a sync watcher fires "before any Vue-managed updates" -- the default flush is
// batched. Batching is what leaves the window this listing measures.
//
// A model of the documented behaviour: watcher callbacks go through a queue
// drained on a microtask, computed values recompute on read.

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

// The batching a default-flush watcher goes through.
const queue = new Set();
let flushing = null;
function schedule(job) {
  queue.add(job);
  flushing ??= Promise.resolve().then(() => {
    const jobs = [...queue];
    queue.clear();
    flushing = null;
    jobs.forEach((run) => run());
  });
  return flushing;
}

function watch(source, callback) {
  let oldValue;
  const runner = effect(source, {
    lazy: true,
    scheduler: () =>
      schedule(() => {
        const newValue = runner();
        if (newValue !== oldValue) {
          const previous = oldValue;
          oldValue = newValue;
          callback(newValue, previous);
        }
      }),
  });
  oldValue = runner();
}

async function main() {
  const first = ref("Ada");
  const last = ref("Lovelace");

  let getterRuns = 0;
  const fullName = computed(() => {
    getterRuns++;
    return `${first.value} ${last.value}`;
  });

  let callbackRuns = 0;
  const mirrored = ref(`${first.value} ${last.value}`);
  watch(
    () => `${first.value} ${last.value}`,
    (next) => {
      callbackRuns++;
      mirrored.value = next;
    },
  );

  console.log(`initial computed: ${fullName.value}`);
  console.log(`initial mirror:   ${mirrored.value}`);

  first.value = "Grace";
  last.value = "Hopper";

  console.log(`straight after two writes, computed: ${fullName.value}`);
  console.log(`straight after two writes, mirror:   ${mirrored.value}`);
  console.log(`the computed was already right: ${fullName.value === "Grace Hopper"}`);
  console.log(`the mirror was still stale: ${mirrored.value === "Ada Lovelace"}`);

  await Promise.resolve();
  await Promise.resolve();

  console.log(`after the queue drained, mirror: ${mirrored.value}`);
  console.log(`watcher callback runs for two writes: ${callbackRuns}`);
  console.log(`computed getter runs: ${getterRuns}`);
}

main();
