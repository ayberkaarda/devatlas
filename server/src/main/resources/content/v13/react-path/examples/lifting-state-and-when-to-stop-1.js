// Two panels that each keep their own "is this one open" flag, and the same
// two panels driven by one flag held by their parent. The requirement is that
// only one panel may be open at a time. The first version cannot express it,
// and the measurement is how many panels are open after a sequence of clicks.

function duplicatedState() {
  const panels = [
    { name: "About", open: true },
    { name: "Etymology", open: false },
  ];
  return {
    click(name) {
      // A panel can only reach its own flag.
      const panel = panels.find((p) => p.name === name);
      panel.open = true;
    },
    openPanels: () => panels.filter((p) => p.open).map((p) => p.name),
  };
}

function liftedState() {
  let openPanel = "About"; // one value, held by the parent
  return {
    click(name) {
      openPanel = name;
    },
    openPanels: () => (openPanel === null ? [] : [openPanel]),
  };
}

function exercise(accordion, label) {
  accordion.click("Etymology");
  console.log(`${label}: after opening Etymology, open panels: ${accordion.openPanels().join(", ")}`);
  console.log(`${label}: at most one panel open: ${accordion.openPanels().length <= 1}`);
}

exercise(duplicatedState(), "duplicated");
exercise(liftedState(), "lifted    ");

// The rule underneath: for each piece of state there is one component that owns
// it. Two copies of the same fact can disagree, and here is the disagreement.
const copyInHeader = { unreadCount: 3 };
const copyInSidebar = { unreadCount: 3 };
copyInHeader.unreadCount = 2; // one screen marked a message read
console.log(`two copies of one fact agree: ${copyInHeader.unreadCount === copyInSidebar.unreadCount}`);

const owned = { unreadCount: 3 };
const header = () => owned.unreadCount;
const sidebar = () => owned.unreadCount;
owned.unreadCount = 2;
console.log(`one owner, two readers agree: ${header() === sidebar()}`);
