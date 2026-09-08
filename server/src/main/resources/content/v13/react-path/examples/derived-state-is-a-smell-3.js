// Computing during render is the default, and useMemo is the answer when that
// computation is genuinely expensive. This is a model of the cache useMemo
// keeps: one value, one dependency array, compared with Object.is. It is not a
// place to store derived state -- React 19 may discard a cached value -- so the
// calculation has to remain correct when it runs again.

function memoSlot() {
  let deps = null;
  let value;
  return (compute, nextDeps) => {
    const hit =
      deps !== null &&
      deps.length === nextDeps.length &&
      deps.every((d, i) => Object.is(d, nextDeps[i]));
    if (!hit) {
      value = compute();
      deps = nextDeps;
    }
    return value;
  };
}

const todos = [
  { text: "Bread", done: false },
  { text: "Cheese", done: true },
  { text: "Olives", done: false },
];

let calls = 0;
function filterTodos(list, filter) {
  calls += 1;
  return filter === "all" ? list : list.filter((todo) => todo.done === (filter === "done"));
}

// Three renders. The filter changes once; the todos never do.
const renders = [
  { filter: "all", theme: "light" },
  { filter: "all", theme: "dark" },
  { filter: "done", theme: "dark" },
];

calls = 0;
for (const render of renders) {
  filterTodos(todos, render.filter);
}
console.log(`without a cache, filterTodos ran: ${calls}`);

calls = 0;
const memo = memoSlot();
let last;
for (const render of renders) {
  last = memo(() => filterTodos(todos, render.filter), [todos, render.filter]);
}
console.log(`with a cache, filterTodos ran: ${calls}`);
console.log(`result of the last render: ${last.map((todo) => todo.text).join(", ")}`);

// The cache is keyed on the array, so a list rebuilt during render defeats it.
calls = 0;
const defeated = memoSlot();
for (const render of renders) {
  defeated(() => filterTodos([...todos], render.filter), [[...todos], render.filter]);
}
console.log(`with a cache and a rebuilt list, filterTodos ran: ${calls}`);
