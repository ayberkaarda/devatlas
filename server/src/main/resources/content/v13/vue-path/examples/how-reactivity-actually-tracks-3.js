// Two edges: the property that did not exist yet, and the read after `await`.
//
// Vue 3.5 uses a Proxy for reactive objects, so the traps fire for a key that is
// absent as well as one that is present, and for `delete` as well as assignment.
// The other edge is timing: Vue 3.5's watchers guide says watchEffect
// "automatically tracks every reactive property accessed during its synchronous
// execution". Anything read after the function has suspended is read when no
// effect is active, and nothing records it.
//
// Both edges are properties of the documented mechanism, modelled here.

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
      // Restored synchronously. An `await` inside `update` resumes after this.
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
    deleteProperty(target, key) {
      const existed = Reflect.deleteProperty(target, key);
      if (existed) trigger(target, key);
      return existed;
    },
  });
}

async function main() {
  // 1. A key that is not there yet is still a key the trap saw.
  const settings = reactive({ theme: "dark" });
  let absentRuns = 0;
  effect(() => {
    absentRuns++;
    void settings.fontSize; // undefined today
  });
  console.log(`effect read a property that does not exist, runs: ${absentRuns}`);
  settings.fontSize = 16;
  console.log(`after the property was added, runs: ${absentRuns}`);
  delete settings.fontSize;
  console.log(`after the property was deleted, runs: ${absentRuns}`);

  // 2. Reads before and after a suspension point.
  const form = reactive({ before: 1, after: 1 });
  let asyncRuns = 0;
  const finished = new Promise((resolve) => {
    effect(async () => {
      asyncRuns++;
      void form.before;
      await Promise.resolve();
      void form.after; // no effect is active here any more
      resolve();
    });
  });
  await finished;

  const runsAfterSetup = asyncRuns;
  form.after = 2;
  await Promise.resolve();
  console.log(`changing the property read after await woke the effect: ${asyncRuns > runsAfterSetup}`);

  form.before = 2;
  await Promise.resolve();
  console.log(`changing the property read before await woke the effect: ${asyncRuns > runsAfterSetup}`);
}

main();
