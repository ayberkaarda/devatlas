// What "lazily loaded" buys you, watched rather than asserted.
//
// Vue 3.5's async components guide: "ES module dynamic import also returns a
// Promise, so most of the time we will use it in combination with
// defineAsyncComponent. Bundlers like Vite and webpack also support the syntax
// (and will use it as bundle split points)".
//
// The two modules below are real ES modules given to Node as data: URLs, so the
// import is a real dynamic import and the moment each module body evaluates is
// observable. Each module announces itself when it is evaluated.

const evaluations = [];
globalThis.__record = (name) => evaluations.push(name);

const source = (name) =>
  "data:text/javascript," +
  encodeURIComponent(
    `globalThis.__record(${JSON.stringify(name)});
     export default { name: ${JSON.stringify(name)}, render: () => "<h1>${name}</h1>" };`,
  );

let loaderCalls = 0;

function defineAsyncComponent(loader) {
  let resolved = null;
  let pending = null;
  return {
    isAsync: true,
    load() {
      if (resolved) return Promise.resolve(resolved);
      pending ??= loader().then((module) => {
        resolved = module.default;
        return resolved;
      });
      return pending;
    },
    get isResolved() {
      return resolved !== null;
    },
  };
}

const routes = [
  {
    path: "/",
    component: defineAsyncComponent(() => {
      loaderCalls++;
      return import(source("HomeView"));
    }),
  },
  {
    path: "/reports",
    component: defineAsyncComponent(() => {
      loaderCalls++;
      return import(source("ReportsView"));
    }),
  },
];

const find = (path) => routes.find((route) => route.path === path);

async function navigate(path) {
  const route = find(path);
  const component = await route.component.load();
  return component.render();
}

async function main() {
  console.log(`routes registered: ${routes.length}`);
  console.log(`modules evaluated before any navigation: ${evaluations.length}`);
  console.log(`loader calls before any navigation: ${loaderCalls}`);

  console.log(`navigate("/") rendered: ${await navigate("/")}`);
  console.log(`modules evaluated: ${evaluations.join(", ")}`);
  console.log(`the other view is still unloaded: ${!find("/reports").component.isResolved}`);

  console.log(`navigate("/reports") rendered: ${await navigate("/reports")}`);
  console.log(`modules evaluated: ${evaluations.join(", ")}`);

  const callsAfterFirstVisits = loaderCalls;
  await navigate("/");
  await navigate("/reports");
  console.log(`loader calls after visiting each view twice: ${loaderCalls}`);
  console.log(`a resolved component is not fetched again: ${loaderCalls === callsAfterFirstVisits}`);
  console.log(`each module body evaluated once: ${evaluations.length === 2}`);
}

main();
