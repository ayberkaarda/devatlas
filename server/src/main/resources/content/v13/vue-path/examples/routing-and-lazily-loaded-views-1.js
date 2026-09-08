// A route table is data, and matching is a function over it.
//
// Vue 3.5's routing guide recommends Vue Router for most single-page
// applications, and shows that simple routing can be done "with Dynamic
// Components" plus "a routes object mapping paths to components". This listing
// is that object and the matcher over it, with nothing else, so that the shape
// of a route record is visible before any framework wraps it.

const routes = [
  { path: "/", name: "home", component: "HomeView" },
  { path: "/books", name: "books", component: "BookListView" },
  { path: "/books/:id", name: "book", component: "BookDetailView" },
  { path: "/books/:id/notes/:noteId", name: "note", component: "NoteView" },
];

const notFound = { name: "not-found", component: "NotFoundView", params: {} };

function segmentsOf(path) {
  return path.split("/").filter(Boolean);
}

function matchRoute(path) {
  const actual = segmentsOf(path);
  for (const route of routes) {
    const pattern = segmentsOf(route.path);
    if (pattern.length !== actual.length) continue;

    const params = {};
    let matched = true;
    for (let i = 0; i < pattern.length; i++) {
      if (pattern[i].startsWith(":")) {
        params[pattern[i].slice(1)] = actual[i];
      } else if (pattern[i] !== actual[i]) {
        matched = false;
        break;
      }
    }
    if (matched) {
      return { name: route.name, component: route.component, params };
    }
  }
  return notFound;
}

function describe(path) {
  const match = matchRoute(path);
  const params = Object.entries(match.params)
    .map(([key, value]) => `${key}=${value}`)
    .join(",");
  return `${path} -> ${match.name} (${match.component})${params ? " " + params : ""}`;
}

console.log(`routes registered: ${routes.length}`);
for (const path of [
  "/",
  "/books",
  "/books/42",
  "/books/42/notes/7",
  "/books/42/notes",
  "/nothing-here",
]) {
  console.log(describe(path));
}

console.log(`a longer path does not match a shorter pattern: ${matchRoute("/books/42/notes").name === "not-found"}`);
console.log(`params arrive as strings: ${typeof matchRoute("/books/42").params.id === "string"}`);
console.log(`the static route wins over nothing else: ${matchRoute("/books").name === "books"}`);
console.log(`an unmatched path falls through: ${matchRoute("/nope").component === "NotFoundView"}`);
