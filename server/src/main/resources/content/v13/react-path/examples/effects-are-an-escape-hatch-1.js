// A model of the effect lifecycle React 19 runs: after a commit, the previous
// dependency array is compared member by member with Object.is. If every member
// matches, nothing happens. If any differs, the previous cleanup runs and then
// the new setup runs. Unmounting runs the last cleanup.

function effectSlot() {
  let previousDeps = null;
  let cleanup = null;
  return {
    commit(setup, deps) {
      if (previousDeps && previousDeps.length === deps.length && previousDeps.every((value, i) => Object.is(value, deps[i]))) {
        return;
      }
      if (cleanup) {
        cleanup();
      }
      cleanup = setup() ?? null;
      previousDeps = deps;
    },
    unmount() {
      if (cleanup) {
        cleanup();
        cleanup = null;
      }
    },
  };
}

// The external system this component synchronises with.
const server = { open: [] };
function connectTo(roomId) {
  server.open.push(roomId);
  console.log(`connect ${roomId}`);
  return () => {
    server.open = server.open.filter((room) => room !== roomId);
    console.log(`disconnect ${roomId}`);
  };
}

const slot = effectSlot();

// Render one: the component mounts showing the general room.
slot.commit(() => connectTo("general"), ["general"]);

// Render two: unrelated state changed, the room did not. Nothing should happen.
slot.commit(() => connectTo("general"), ["general"]);

// Render three: the reader picked a different room.
slot.commit(() => connectTo("music"), ["music"]);

// The component leaves the screen.
slot.unmount();

console.log(`connections still open: ${server.open.length}`);
