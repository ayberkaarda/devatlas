// Loading, error and data are not three independent facts. Held as three
// booleans they describe eight combinations, most of which cannot happen and
// one of which is the bug people ship: an error left over from the previous
// request, still on screen while the next one succeeds.

const flagStates = [];
for (const isLoading of [false, true]) {
  for (const hasError of [false, true]) {
    for (const hasData of [false, true]) {
      flagStates.push({ isLoading, hasError, hasData });
    }
  }
}
const meaningful = flagStates.filter(
  (s) => !(s.isLoading && s.hasError) && !(s.hasError && s.hasData),
);
console.log(`combinations three booleans can represent: ${flagStates.length}`);
console.log(`combinations that describe a real screen: ${meaningful.length}`);
console.log(`combinations that cannot happen but can be reached: ${flagStates.length - meaningful.length}`);

// A request that fails and is then retried, with the flags updated one at a time.
function withFlags() {
  const state = { isLoading: false, hasError: false, data: null };
  const frames = [];
  const snap = () => frames.push({ ...state });

  state.isLoading = true;
  snap(); // first request starts
  state.isLoading = false;
  state.hasError = true;
  snap(); // it fails
  state.isLoading = true;
  snap(); // retry starts, and nobody cleared hasError
  state.isLoading = false;
  state.data = "3 results";
  snap(); // it succeeds
  return frames;
}

const frames = withFlags();
const contradictory = frames.filter((f) => (f.isLoading && f.hasError) || (f.hasError && f.data !== null));
console.log(`frames committed with three booleans: ${frames.length}`);
console.log(`frames showing a spinner and an error at once, or an error beside results: ${contradictory.length}`);

// One value instead. The states are named, so a frame is exactly one of them.
function withStatus() {
  const frames = [];
  frames.push({ status: "loading" });
  frames.push({ status: "error", message: "network unreachable" });
  frames.push({ status: "loading" });
  frames.push({ status: "success", data: "3 results" });
  return frames;
}

const statusFrames = withStatus();
console.log(`states a status value can represent: ${new Set(["idle", "loading", "success", "error"]).size}`);
console.log(`frames committed with a status value: ${statusFrames.length}`);
console.log(`frames that describe two things at once: ${statusFrames.filter((f) => Object.keys(f).length > 2).length}`);
console.log(`what the reader ends on: ${statusFrames[statusFrames.length - 1].status}`);
