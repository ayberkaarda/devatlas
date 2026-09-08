// A model of the decision React 19 makes when a set function is called: if the
// value handed in is identical to the current one by Object.is, it skips
// re-rendering the component and its children. Mutating an object in place and
// then handing back the same reference is exactly that case.

function stateSlot(initial) {
  let value = initial;
  let renders = 0;
  return {
    read: () => value,
    set(next) {
      if (Object.is(next, value)) {
        return; // the documented bail-out
      }
      value = next;
      renders += 1;
    },
    renderCount: () => renders,
  };
}

// Mutating, then handing the same reference back.
const mutated = stateSlot({ x: 0, y: 0 });
const positionA = mutated.read();
positionA.x = 5;
mutated.set(positionA);
console.log(`after mutating in place, x is ${mutated.read().x}`);
console.log(`renders caused by the mutation: ${mutated.renderCount()}`);

// Replacing, so the reference differs.
const replaced = stateSlot({ x: 0, y: 0 });
replaced.set({ ...replaced.read(), x: 5 });
console.log(`after replacing, x is ${replaced.read().x}`);
console.log(`renders caused by the replacement: ${replaced.renderCount()}`);

// The same distinction for arrays: push returns a length, not a new array.
const list = stateSlot(["a", "b"]);
const pushed = list.read();
pushed.push("c");
console.log(`push handed back the same array: ${Object.is(pushed, list.read())}`);
list.set(pushed);
console.log(`renders caused by push: ${list.renderCount()}`);
list.set([...list.read(), "d"]);
console.log(`renders caused by a new array: ${list.renderCount()}`);
