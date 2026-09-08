// Uncontrolled and controlled, measured rather than described. A component
// whose important information lives in its own state is uncontrolled: the
// parent cannot influence it. One whose information arrives as a prop is
// controlled: the parent decides, and gets a callback when the child wants a
// change.

function uncontrolledPanel(name) {
  let isActive = false; // the child's own state
  return {
    name,
    toggle() {
      isActive = !isActive;
    },
    // The parent has no handle on isActive at all; there is nothing to call.
    askParentToClose: null,
    isActive: () => isActive,
  };
}

function controlledPanel(name, isActive, onChange) {
  return {
    name,
    toggle() {
      onChange(!isActive);
    },
    isActive: () => isActive,
  };
}

const loose = uncontrolledPanel("About");
loose.toggle();
console.log(`uncontrolled: panel open: ${loose.isActive()}`);
console.log(`uncontrolled: parent can close it: ${loose.askParentToClose !== null}`);

let openPanel = "About"; // held by the parent
const makeControlled = (name) =>
  controlledPanel(name, openPanel === name, (next) => {
    openPanel = next ? name : null;
  });

console.log(`controlled: About open: ${makeControlled("About").isActive()}`);
makeControlled("Etymology").toggle(); // the child asks; the parent decides
console.log(`controlled: after Etymology asked, the parent holds: ${openPanel}`);
console.log(`controlled: About open now: ${makeControlled("About").isActive()}`);
makeControlled("Etymology").toggle(); // the parent can close it too
console.log(`controlled: parent could close it: ${openPanel === null}`);

// The trade is configuration for coordination. Count what each parent must pass.
console.log(`props an uncontrolled panel needs: ${["name"].length}`);
console.log(`props a controlled panel needs: ${["name", "isActive", "onChange"].length}`);
