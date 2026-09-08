// The branch not taken, and why an effect has to forget.
//
// Dependencies are collected per run, not once. An effect that skipped a branch
// last time did not subscribe to what the branch reads; an effect that took a
// branch last time and skips it now must drop those subscriptions or it will
// keep waking up for state it no longer looks at.
//
// Two effect implementations below, identical except that one clears its
// subscriptions before re-running. The counters show what the difference costs.

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
  if (subscribers) [...subscribers].forEach((run) => run());
}

function makeEffect(update, { forgets }) {
  const run = () => {
    if (forgets) {
      for (const subscribers of run.subscriptions) subscribers.delete(run);
      run.subscriptions = [];
    }
    const previous = activeEffect;
    activeEffect = run;
    try {
      update(run);
    } finally {
      activeEffect = previous;
    }
  };
  run.subscriptions = [];
  run.runs = 0;
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

function build(forgets) {
  const state = reactive({ showDetail: false, detail: "hidden" });
  const runner = makeEffect((self) => {
    self.runs++;
    void state.showDetail;
    if (state.showDetail) {
      void state.detail;
    }
  }, { forgets });
  return { state, runner };
}

for (const forgets of [false, true]) {
  const label = forgets ? "with cleanup   " : "without cleanup";
  const { state, runner } = build(forgets);

  console.log(`${label} run 1, effect runs: ${runner.runs}`);

  state.detail = "still hidden";
  console.log(`${label} branch was skipped, detail changed, runs: ${runner.runs}`);

  state.showDetail = true;
  console.log(`${label} branch now taken, runs: ${runner.runs}`);

  state.detail = "now shown";
  console.log(`${label} detail changed while shown, runs: ${runner.runs}`);

  state.showDetail = false;
  const before = runner.runs;
  state.detail = "hidden again";
  console.log(
    `${label} detail changed after the branch closed, woke the effect: ` +
      `${runner.runs > before}`,
  );
}
