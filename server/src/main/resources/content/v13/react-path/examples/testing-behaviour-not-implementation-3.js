// A test written against React 19 itself. Not executed: this repository does
// not install React or a DOM environment, and the component under test is JSX,
// which a plain Node run cannot parse. There is no recorded output for it.
//
// What it would need to run, none of which this file sets up itself:
//   - react and react-dom 19 installed;
//   - a runner supplying `test` and `expect` as globals -- Jest or Vitest with
//     globals enabled -- since neither is imported below;
//   - a simulated DOM supplying `document` and `MouseEvent`, such as the jsdom
//     test environment, because `createRoot` needs a real container node;
//   - a JSX transform, which the runner's transformer normally provides.
//
// The shape worth reading is the pairing of `act` with `await`, and the fact
// that every assertion is phrased in terms of what a reader would see: a
// heading, a control with a name, a message announced as an alert.

import { act, useState } from "react";
import { createRoot } from "react-dom/client";

function Counter({ label }) {
  const [count, setCount] = useState(0);
  return (
    <div>
      <h2>{label}</h2>
      <button onClick={() => setCount((c) => c + 1)}>Add one</button>
      <p role="status">{count} added</p>
    </div>
  );
}

// react.dev states that `act` requires this global, and that React Testing
// Library sets it for you.
global.IS_REACT_ACT_ENVIRONMENT = true;

test("counting up announces the new total", async () => {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);

  await act(async () => {
    root.render(<Counter label="Sourdough" />);
  });

  const status = container.querySelector('[role="status"]');
  const button = [...container.querySelectorAll("button")].find(
    (node) => node.textContent === "Add one",
  );

  expect(status.textContent).toBe("0 added");

  await act(async () => {
    button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });

  expect(status.textContent).toBe("1 added");

  await act(async () => {
    root.unmount();
  });
  container.remove();
});
