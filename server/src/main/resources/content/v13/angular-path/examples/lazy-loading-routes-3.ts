// What `loadComponent: () => import(...)` actually is, without Angular in the
// way: a function that has not run yet, wrapped around a dynamic import that
// fetches nothing until it does.
//
// The module imported here is a Node built-in, chosen because it is guaranteed
// to be present and has a function with an output worth printing. The shape is
// the shape the router uses.

type Loader = () => Promise<(value: string) => string>;

async function main(): Promise<void> {
  console.log('route table built');

  const loadEscape: Loader = () => import('node:querystring').then((m) => m.escape);

  console.log('loader declared, module not requested');

  // Activating the route is what calls the function.
  const escape = await loadEscape();

  console.log('module resolved');
  console.log(escape('a b'));

  // Calling it again does not fetch again: a module is evaluated once and the
  // second import resolves from the registry. This is why a route revisited is
  // not a second download.
  const again = await loadEscape();
  console.log(`same function: ${again === escape}`);
}

void main();
