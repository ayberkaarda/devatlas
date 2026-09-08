// Two ways to put a first name and a last name together. One stores the result
// in state and writes it from an effect; the other computes it during render.
// Every committed frame is recorded here, because the cost of the first version
// is a frame the reader can see: the one where the inputs have changed and the
// mirrored value has not caught up.

function mirroredInAnEffect(edits) {
  let firstName = "";
  let lastName = "";
  let fullName = "";
  const frames = [];

  for (const [next, part] of edits) {
    if (part === "first") {
      firstName = next;
    } else {
      lastName = next;
    }
    // Render one: the input state changed, so React renders and commits.
    frames.push({ firstName, lastName, fullName });

    // The effect runs after that commit and sets state again.
    const computed = `${firstName} ${lastName}`.trim();
    if (computed !== fullName) {
      fullName = computed;
      // Render two, caused by the effect.
      frames.push({ firstName, lastName, fullName });
    }
  }
  return frames;
}

function computedDuringRender(edits) {
  let firstName = "";
  let lastName = "";
  const frames = [];

  for (const [next, part] of edits) {
    if (part === "first") {
      firstName = next;
    } else {
      lastName = next;
    }
    frames.push({ firstName, lastName, fullName: `${firstName} ${lastName}`.trim() });
  }
  return frames;
}

const edits = [
  ["Taylor", "first"],
  ["Swift", "last"],
  ["Tay", "first"],
];

function report(label, frames) {
  const stale = frames.filter((f) => f.fullName !== `${f.firstName} ${f.lastName}`.trim());
  console.log(`${label}: frames committed: ${frames.length}`);
  console.log(`${label}: frames where the shown name disagreed with the inputs: ${stale.length}`);
}

report("effect  ", mirroredInAnEffect(edits));
report("computed", computedDuringRender(edits));

const staleFrames = mirroredInAnEffect(edits).filter(
  (f) => f.fullName !== `${f.firstName} ${f.lastName}`.trim(),
);
for (const frame of staleFrames) {
  const fromInputs = `${frame.firstName} ${frame.lastName}`.trim();
  console.log(`  inputs said "${fromInputs}" but the screen said "${frame.fullName}"`);
}
