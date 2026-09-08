// The race, run rather than described. A reader types "wo", then "wolf". Two
// requests are in flight; the server answers the second one first. The effect
// for "wo" is already stale when its answer arrives, and without the cleanup
// documented for this case it writes that stale answer into state.
//
// Nothing here is timed. The two answers are resolved by hand, in the order
// that produces the bug, so the result is the same on every machine.

function deferred() {
  let resolve;
  const promise = new Promise((settle) => {
    resolve = settle;
  });
  return { promise, resolve };
}

async function search({ withCleanup }) {
  let displayed = null;
  const inFlight = [];

  // This stands in for one run of an effect with [query] as its dependency.
  function runEffectFor(query, answer) {
    let ignore = false;
    answer.promise.then((results) => {
      if (ignore) {
        return;
      }
      displayed = results;
    });
    inFlight.push(answer.promise);
    return () => {
      ignore = withCleanup;
    };
  }

  const slow = deferred();
  const fast = deferred();

  const cancelPrevious = runEffectFor("wo", slow);
  cancelPrevious(); // the query changed, so React runs the previous cleanup
  runEffectFor("wolf", fast);

  fast.resolve("results for wolf");
  slow.resolve("results for wo");
  await Promise.all(inFlight);

  return displayed;
}

async function main() {
  console.log(`without cleanup, the screen ends up showing: ${await search({ withCleanup: false })}`);
  console.log(`with cleanup, the screen ends up showing: ${await search({ withCleanup: true })}`);
}

main();
