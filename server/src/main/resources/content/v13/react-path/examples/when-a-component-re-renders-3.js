// The third reason. React 19 re-renders every component that reads a context,
// starting from the provider that received a different value, and the previous
// and next values are compared with Object.is. memo does not stand in the way
// of that, which is why a provider value rebuilt on every render is expensive.

function update({ providerValue, previousValue, consumers }) {
  const changed = !Object.is(providerValue, previousValue);
  return consumers
    .filter((consumer) => changed && consumer.readsContext)
    .map((consumer) => consumer.name);
}

const consumers = [
  { name: "ThemeToggle", readsContext: true, memoised: false },
  { name: "Avatar", readsContext: true, memoised: true },
  { name: "Footer", readsContext: false, memoised: true },
];

// A provider value rebuilt inline on every render of the provider's component.
const rebuilt = () => ({ theme: "dark" });
console.log(`inline value, Object.is holds: ${Object.is(rebuilt(), rebuilt())}`);
console.log(`inline value, consumers re-rendered: ${update({ providerValue: rebuilt(), previousValue: rebuilt(), consumers }).join(", ")}`);

// The same value kept stable, for instance by useMemo with unchanged deps.
const kept = { theme: "dark" };
console.log(`stable value, Object.is holds: ${Object.is(kept, kept)}`);
const stableRun = update({ providerValue: kept, previousValue: kept, consumers });
console.log(`stable value, consumers re-rendered: ${stableRun.length}`);

// A genuine change reaches the memoised consumer as well.
const switched = { theme: "light" };
console.log(`value actually changed, consumers re-rendered: ${update({ providerValue: switched, previousValue: kept, consumers }).join(", ")}`);
console.log(`memo kept Avatar out of that list: ${!update({ providerValue: switched, previousValue: kept, consumers }).includes("Avatar")}`);
