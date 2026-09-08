// The comparison `memo` performs in React 19: props are compared shallowly,
// member by member, with Object.is. An object, an array or a function written
// inline in JSX is a new value on every render, so the comparison fails and the
// memoised component renders anyway.

function shallowEqual(previous, next) {
  const previousKeys = Object.keys(previous);
  const nextKeys = Object.keys(next);
  if (previousKeys.length !== nextKeys.length) {
    return false;
  }
  return previousKeys.every((key) => Object.is(previous[key], next[key]));
}

// Written inline in the parent's JSX: rebuilt on every render.
const inlineProps = () => ({
  labels: ["all", "active"],
  onSelect: (value) => value,
  count: 3,
});

console.log(`inline props shallow-equal across two renders: ${shallowEqual(inlineProps(), inlineProps())}`);

// Hoisted out of the component, or produced by useCallback and useMemo with
// dependencies that did not change: the same references come back.
const labels = ["all", "active"];
const onSelect = (value) => value;
const hoistedProps = () => ({ labels, onSelect, count: 3 });

console.log(`hoisted props shallow-equal across two renders: ${shallowEqual(hoistedProps(), hoistedProps())}`);

// Shallow means shallow: an equal-looking nested object is still a new one.
const nestedA = { filter: { status: "active" } };
const nestedB = { filter: { status: "active" } };
console.log(`nested objects look alike: ${JSON.stringify(nestedA) === JSON.stringify(nestedB)}`);
console.log(`nested objects are shallow-equal: ${shallowEqual(nestedA, nestedB)}`);

// And memo only answers the question "did the parent hand me the same props".
// Its own state changing is a separate reason to render, which memo cannot stop.
function wouldRender({ propsChanged, ownStateChanged, contextChanged }) {
  return propsChanged || ownStateChanged || contextChanged;
}
console.log(`memoised, props equal, own state changed: ${wouldRender({ propsChanged: false, ownStateChanged: true, contextChanged: false })}`);
console.log(`memoised, props equal, context changed: ${wouldRender({ propsChanged: false, ownStateChanged: false, contextChanged: true })}`);
console.log(`memoised, nothing changed: ${wouldRender({ propsChanged: false, ownStateChanged: false, contextChanged: false })}`);
