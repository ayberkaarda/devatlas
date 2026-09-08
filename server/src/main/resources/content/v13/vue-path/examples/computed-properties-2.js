// A computed and a method give the same answer. They do not do the same work.
//
// Vue 3.5's guide puts the difference plainly: "the two approaches are indeed
// exactly the same" in result, but "computed properties are cached based on
// their reactive dependencies", whereas "a method invocation will always run
// the function whenever a re-render happens".
//
// The counter below is the whole argument: it counts the work each does across
// four renders of the same screen.

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

const library = reactive({ books: ["Vue", "Proxy", "Reflect"], filter: "" });

let computedWork = 0;
const visibleComputed = computed(() => {
  computedWork++;
  return library.books.filter((title) => title.includes(library.filter));
});

let methodWork = 0;
function visibleMethod() {
  methodWork++;
  return library.books.filter((title) => title.includes(library.filter));
}

// A render reads both, and something unrelated re-renders the screen twice.
function render(label) {
  const fromComputed = visibleComputed.value.join(",");
  const fromMethod = visibleMethod().join(",");
  console.log(
    `${label}: computed getter calls ${computedWork}, method calls ${methodWork}` +
      `, same answer ${fromComputed === fromMethod}`,
  );
}

render("render 1");
render("render 2 (nothing changed)");
render("render 3 (nothing changed)");

library.filter = "e";
render("render 4 (filter changed)");

console.log(`renders: 4, computed getter calls: ${computedWork}`);
console.log(`renders: 4, method calls: ${methodWork}`);
console.log(`the method ran once per render: ${methodWork === 4}`);
console.log(`the computed ran once per change: ${computedWork === 2}`);
