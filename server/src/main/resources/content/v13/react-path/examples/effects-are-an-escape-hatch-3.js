// The dependency array is compared with Object.is, so a dependency that is a
// fresh object on every render never matches, and the effect runs after every
// render. The fix is to depend on the primitive the effect actually uses, not
// on the object that happens to carry it.

function effectRunner() {
  let previousDeps = null;
  let runs = 0;
  return {
    commit(deps) {
      const unchanged =
        previousDeps !== null &&
        previousDeps.length === deps.length &&
        previousDeps.every((value, i) => Object.is(value, deps[i]));
      if (!unchanged) {
        runs += 1;
      }
      previousDeps = deps;
    },
    runs: () => runs,
  };
}

const roomId = "general";

// An options object built during render. Three renders, three new objects.
const objectDep = effectRunner();
for (let render = 0; render < 3; render += 1) {
  const options = { serverUrl: "https://example.test", roomId };
  objectDep.commit([options]);
}
console.log(`object dependency, effect runs over three renders: ${objectDep.runs()}`);

// The same three renders, depending on the string the effect reads.
const primitiveDep = effectRunner();
for (let render = 0; render < 3; render += 1) {
  primitiveDep.commit([roomId]);
}
console.log(`string dependency, effect runs over three renders: ${primitiveDep.runs()}`);

// A function declared in the component body has the same problem.
const handlerDep = effectRunner();
for (let render = 0; render < 3; render += 1) {
  const onMessage = (text) => text.toUpperCase();
  handlerDep.commit([onMessage]);
}
console.log(`function dependency, effect runs over three renders: ${handlerDep.runs()}`);

// And a changed value is still detected: the point is not to freeze the array.
const changing = effectRunner();
for (const room of ["general", "general", "music"]) {
  changing.commit([room]);
}
console.log(`string dependency across general, general, music: ${changing.runs()}`);
