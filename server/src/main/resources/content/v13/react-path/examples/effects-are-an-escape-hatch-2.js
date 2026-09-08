// Why React 19 Strict Mode mounts a component, unmounts it and mounts it again
// in development. An effect that sets something up and never tears it down
// looks correct on a single mount and leaks on the second one, which is the
// same leak a reader produces by navigating away and back.

function mountCycle(setupEffect, { strict }) {
  let cleanup = setupEffect() ?? null;
  if (strict) {
    // The extra mount Strict Mode performs: cleanup, then setup again.
    if (cleanup) {
      cleanup();
    }
    cleanup = setupEffect() ?? null;
  }
  return () => {
    if (cleanup) {
      cleanup();
    }
  };
}

function makeServer() {
  const open = new Set();
  let nextId = 1;
  return {
    connect() {
      const id = nextId++;
      open.add(id);
      return { id, close: () => open.delete(id) };
    },
    openCount: () => open.size,
  };
}

// An effect that forgets to return a cleanup function.
const leaky = makeServer();
const unmountLeaky = mountCycle(() => {
  leaky.connect();
}, { strict: true });
console.log(`no cleanup, connections open after the double mount: ${leaky.openCount()}`);
unmountLeaky();
console.log(`no cleanup, connections open after unmount: ${leaky.openCount()}`);

// The same effect written with a cleanup function.
const tidy = makeServer();
const unmountTidy = mountCycle(() => {
  const connection = tidy.connect();
  return () => connection.close();
}, { strict: true });
console.log(`with cleanup, connections open after the double mount: ${tidy.openCount()}`);
unmountTidy();
console.log(`with cleanup, connections open after unmount: ${tidy.openCount()}`);

// The leak is not an artefact of the development double mount. It is the same
// count a reader reaches by opening the screen three times in production.
const production = makeServer();
for (let visit = 0; visit < 3; visit += 1) {
  mountCycle(() => {
    production.connect();
  }, { strict: false })();
}
console.log(`no cleanup, connections open after three real visits: ${production.openCount()}`);
