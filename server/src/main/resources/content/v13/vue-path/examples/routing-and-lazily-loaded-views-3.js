// A lazily loaded view has three states, and two of them are not the component.
//
// Vue 3.5's async components guide: "Asynchronous operations inevitably involve
// loading and error states - defineAsyncComponent() supports handling these
// states via advanced options", naming loadingComponent, delay, errorComponent
// and timeout. A route whose component arrives over the network can be pending
// or failed, and a router that renders nothing in those states shows a blank
// screen for a reason nobody can see.
//
// The loaders below are settled by hand rather than by a timer, so the ordering
// is the same on every machine.

function deferred() {
  let settle;
  const promise = new Promise((resolve, reject) => {
    settle = { resolve, reject };
  });
  return { promise, ...settle };
}

function defineAsyncComponent({ loader, loadingComponent, errorComponent }) {
  let state = "idle";
  let resolved = null;
  let failure = null;
  let calls = 0;

  const start = () => {
    state = "pending";
    calls++;
    return loader().then(
      (module) => {
        resolved = module.default;
        state = "resolved";
      },
      (error) => {
        failure = error;
        state = "failed";
      },
    );
  };

  return {
    load: start,
    get calls() {
      return calls;
    },
    view() {
      if (state === "resolved") return resolved.render();
      if (state === "failed") return errorComponent.render(failure);
      if (state === "pending") return loadingComponent.render();
      return "";
    },
  };
}

const spinner = { render: () => "<p>Loading...</p>" };
const failureView = { render: (error) => `<p class="error">${error.message}</p>` };
const settingsView = { default: { render: () => "<h1>Settings</h1>" } };

async function main() {
  // 1. Pending, then resolved.
  const slow = deferred();
  const pendingRoute = defineAsyncComponent({
    loader: () => slow.promise,
    loadingComponent: spinner,
    errorComponent: failureView,
  });

  console.log(`before navigation, the view is: "${pendingRoute.view()}"`);
  const settled = pendingRoute.load();
  console.log(`while the loader is pending: ${pendingRoute.view()}`);
  slow.resolve(settingsView);
  await settled;
  console.log(`after the loader resolved: ${pendingRoute.view()}`);

  // 2. Failed, then retried.
  let attempt = 0;
  const flaky = defineAsyncComponent({
    loader: () => {
      attempt++;
      return attempt === 1
        ? Promise.reject(new Error("the chunk could not be fetched"))
        : Promise.resolve(settingsView);
    },
    loadingComponent: spinner,
    errorComponent: failureView,
  });

  await flaky.load();
  console.log(`after a failed load: ${flaky.view()}`);
  console.log(`the error component was handed the reason: ${flaky.view().includes("could not be fetched")}`);
  console.log(`loader calls so far: ${flaky.calls}`);

  await flaky.load();
  console.log(`after a retry: ${flaky.view()}`);
  console.log(`loader calls after the retry: ${flaky.calls}`);
  console.log(`a retry replaced the error view: ${flaky.view() === "<h1>Settings</h1>"}`);

  // 3. The same route with neither state declared: a blank screen, twice.
  const bare = deferred();
  const silent = defineAsyncComponent({
    loader: () => bare.promise,
    loadingComponent: { render: () => "" },
    errorComponent: { render: () => "" },
  });
  const silentSettled = silent.load();
  console.log(`with no loading state declared, pending renders: "${silent.view()}"`);
  bare.reject(new Error("gone"));
  await silentSettled;
  console.log(`with no error state declared, failure renders: "${silent.view()}"`);
}

main();
