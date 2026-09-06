# 0001. Mind map rendering library

## Status

Accepted

## Context

Every published track may carry a `MIND_MAP` entity: a tree of labelled nodes,
some of which reference a lesson (`MindMapNode.lessonId`). The frontend needs a
screen that renders this tree, lets a node be activated to open its lesson, and
does this identically on the desktop build (reading the entity from the local
SQLite replica) and the web build (reading it live over HTTP) — the same
constraint every screen in this application already lives under: a component
never knows which platform it is running on.

Three constraints are non-negotiable, not preferences to be weighed against
the others:

1. **It must work fully offline.** The desktop client's entire premise is that
   it works with no connection once content is downloaded. A library that
   fetches a font, an icon sprite, or a stylesheet from a CDN at runtime is
   disqualified outright, whatever else it offers — that fetch fails silently
   or visibly the moment the network is gone, and a mind map that half-renders
   offline is worse than one that was never offered.
2. **It must not enter the initial chunk.** The mind map is one screen among
   many; a user who never opens a track's mind map should not pay for the
   library that draws it. It has to be behind a lazy route boundary, and
   ideally behind a second, narrower lazy boundary around only the rendering
   code, so the cost is visible in the build output as its own chunk rather
   than folded into a route the user always loads.
3. **It must render through the existing token layer**, `frontend/src/styles/tokens.css`,
   not a palette of its own. Two colour systems that have to be kept in sync by
   hand is how a library still looks like the previous theme five minutes
   after a dark-mode toggle.

Beyond those three, the screen needs: keyboard reachability for every node (a
mouse-only tree is not accessible), and a defined answer for a node whose
lesson has not been downloaded — the protocol
(`docs/protocol/content-sync.md`, §4.3) is explicit that this is a normal
state, not an error, and the UI is expected to offer to fetch it.

This decision does not touch the manifest or package shape for `MIND_MAP`
(frozen in `docs/protocol/content-sync.md`) or the `PlatformService` contract
(frozen in `docs/protocol/platform-service.md`); it is scoped entirely to how
the already-agreed `MindMap` view model is drawn on screen.

## Options considered

Sizes below are the package's own minified+gzip weight as reported by
Bundlephobia for the version `npm view` resolved to, checked on the day this
decision was written. They are the dependency's own contribution to a lazy
chunk, not a measurement of this application's build — that measurement is
reported after implementation, once we know what our own rendering code adds
on top.

### A. markmap (`markmap-lib` + `markmap-view`)

Built to turn a markdown outline into an animated, zoomable mind map — closest
in spirit to what this screen needs, on paper.

- Plus: purpose-built for mind maps; ships pan/zoom/animation for free; is
  actively maintained.
- Minus: `markmap-view` (23.9 kB gzip, 72.2 kB min) declares a hard, non-optional
  dependency on the *full* `d3` package (`"d3": "^7.8.5"`, not a single d3
  module), so the real cost is that plus whatever of `d3` tree-shaking cannot
  remove — in practice most of it, because `markmap-view` touches selection,
  zoom, drag, hierarchy and transition all at once. `markmap-lib`, which we
  would not even need (our data already arrives as a typed node tree, not
  markdown to parse), is 741 kB unpacked on its own and pulls in `markdown-it`,
  `highlight.js`, `katex` and `prismjs` — none of it relevant to rendering a
  tree of lesson titles.
- Minus: labels render inside SVG `foreignObject` elements as parsed HTML.
  Making that safe under the rule that nothing reaches the DOM without passing
  through the sanitiser first is extra surface for content
  that in our case is always plain text — a cost paid for a feature (rich
  markdown per node) nothing here uses.
- Minus: theming is a colour list handed to the library
  (`colorFreeThemeCSS`/`options.color`), not a consumer of arbitrary CSS custom
  properties — it would need its own small adapter layer to read `--color-*`
  at render time and stay in sync with a runtime theme toggle, rather than
  picking the values up the way every other component does, through a
  Tailwind utility class.
- Minus: no keyboard tree semantics. It is a pan/zoom canvas aimed at a mouse
  or touch pointer; reaching a specific node by keyboard would be new code
  regardless of which rendering library sits underneath.
- Offline: yes — nothing in `markmap-view` itself fetches from a network at
  runtime; the disqualifying CDN dependency some markmap *demos* show (loading
  fonts or the toolbar from a CDN) is optional presentation code we would not
  use.
- License: MIT.

### B. jsMind

A lightweight, dependency-free mind-mapping library.

- Plus: small and self-contained — 13.9 kB gzip, 50.7 kB min, zero runtime
  dependencies, no CDN calls of any kind.
- Minus: it is an **editor**, not a viewer. Renaming, inserting, deleting and
  dragging nodes are the library's primary interactions and are on by default;
  using it read-only means finding and disabling every one of those
  affordances rather than building on top of a viewer that never had them. A
  content-editable node in a screen whose entire job is to *navigate*
  published content is also a sanitisation question this library was never
  designed to answer.
- Minus: styling is a named theme (a bundled CSS file per palette, e.g.
  `jsmind.orange.css`), swapped by CSS class, not consumed as custom
  properties — again a translation layer between our tokens and its
  stylesheet, and one that would need to react to the runtime theme toggle.
- Plus/minus: ships default keyboard shortcuts, but they are editing shortcuts
  (Tab to insert a child, Enter to insert a sibling, F2 to rename) that would
  have to be remapped or suppressed rather than reused for "move focus between
  nodes and open a lesson."
- Offline: yes.
- License: BSD-3-Clause. Maintenance looks active (a release within the last
  year at the time of writing) but the project is a small one-or-few-maintainer
  effort rather than a foundation-backed one.

### C. Cytoscape.js

A graph-visualisation library built for network graphs — hundreds to
thousands of nodes and edges, arbitrary topologies, physics-based layouts.

- Plus: extremely capable; would render a tree without breaking a sweat.
- Minus: 137 kB gzip, 435.6 kB min — an order of magnitude heavier than
  anything else considered, for a tool aimed at a problem (large arbitrary
  graphs) this screen does not have. A course's mind map is a curriculum
  outline: tens of nodes, a handful of levels deep.
- Minus: its own styling DSL (a Cytoscape stylesheet, conceptually close to
  CSS but a separate object format passed to the library) is a second styling
  system to keep in sync with the token layer, not a consumer of Tailwind
  utility classes.
- Minus: like the others, no built-in keyboard tree semantics; that code would
  still have to be written.
- Offline: yes.
- License: MIT.

### D. Custom renderer on `d3-hierarchy`, drawn as native Angular-controlled SVG

`d3-hierarchy` is not a mind-map or charting library; it is the layout
algorithm alone — given a tree and an accessor for its children, it returns
`x`/`y` coordinates for every node. It touches no DOM and manages no
selections; everything on screen is produced by an Angular template using
ordinary control-flow (`@for`, `@if`) over the layout's output, and styled
with the same Tailwind utility classes every other component uses.

- Plus: 5.65 kB gzip, 14.5 kB min for the layout dependency itself — nothing
  else is added; there is no transitive `d3` selection/zoom/drag/transition
  machinery riding along, because none of it is imported.
- Plus: colour comes from the token layer with zero adapter code. Because
  `frontend/src/styles/tokens.css` declares its colours inside Tailwind's
  `@theme` block, Tailwind already generates the matching `fill-*`/`stroke-*`
  utilities for every token name (`fill-accent`, `stroke-border`,
  `fill-surface-raised`, …) alongside the `bg-*`/`text-*` ones components
  already use. An SVG node written as
  `<circle class="fill-accent stroke-border" />` reads the running theme the
  same way a `<button class="bg-accent">` does, with no per-node inline style
  and no palette maintained a second time.
- Plus: full ownership of the DOM means full ownership of accessibility. Every
  node is a real element we control (`role="treeitem"`, roving `tabindex`,
  arrow-key and Home/End navigation, `aria-label` built from the translated
  lesson title) — the same amount of custom keyboard code every other option
  above also required, but here it is the *only* thing being written, instead
  of being written on top of a renderer that already made different DOM and
  styling decisions.
- Plus: deterministic, synchronous layout with no animation, drag or zoom
  runtime to reason about in tests — a rendered node's position is a pure
  function of the tree and the viewport, which is what the node-to-lesson
  navigation test in this phase exercises directly.
- Minus: we own the maintenance of the rendering and interaction code that a
  library would otherwise carry — panning, zooming, or a force-directed layout
  are not "off the shelf" here if a later phase wants them. For a curriculum
  tree that is read, not explored as a large graph, this is judged an
  acceptable trade against the three constraints above.
- Minus: `d3-hierarchy`'s `tree()` produces a layout sized to fit a box we
  choose; a very wide or very deep track can produce a wide SVG. The
  implementation places the SVG in a horizontally scrollable container rather
  than trying to compress the layout, and this is the one point flagged for
  follow-up below.
- Offline: yes, trivially — the dependency contains no asset-fetching code at
  all, being pure layout arithmetic.
- License: ISC. Part of the `d3` modular family, itself an established,
  long-maintained project (the resolved version predates this decision by
  several years, which for a tree-layout algorithm is a sign of a settled API
  rather than an abandoned one).

## Decision

**Option D: a custom renderer on `d3-hierarchy`.**

The deciding factor is not raw bundle size, although it is the smallest by a
wide margin (5.65 kB gzip versus 13.9–137 kB gzip for the others, before
accounting for markmap's undeclared full-`d3` dependency). It is that every
other option requires writing the same custom keyboard-accessibility and
token-theming code *in addition to* adopting the library, while paying that
library's bundle and integration cost on top. Once the keyboard and theming
work has to be written regardless, the library that is actually a mind-map or
graph *renderer* stops paying for itself — its value proposition (pan, zoom,
animation, physics layout) is not something this screen asked for, and its
cost (a foreign styling system, in markmap's case a full `d3` dependency and a
`foreignObject`-based label that reopens the sanitisation question) is paid
regardless.

`d3-hierarchy` alone avoids all three of that: it is layout math with no DOM
opinion, so there is no foreign styling system to bridge and no rendering
decision to inherit that conflicts with the token layer or the sanitisation
rule. It also removes any doubt about the offline constraint, since there is
no library-owned rendering or asset pipeline to audit for a CDN call — the
dependency cannot fetch anything because it does not touch the network or the
DOM at all.

## Consequences

### Positive

- Smallest possible lazy-chunk cost for this screen, verified in this phase's
  build output rather than estimated (see report).
- No second theming system: the mind map goes light/dark for free whenever the
  rest of the application does, because it reads the same tokens through the
  same utility classes.
- The accessibility behaviour (roving tabindex, arrow-key movement, `Enter`/
  `Space` activation) is first-class application code, not a workaround bolted
  onto a library that assumed a mouse.
- Deterministic output makes the node-to-lesson navigation test simple: given
  a fixed tree, the rendered node for a given `lessonId` is at a computed,
  assertable position in the DOM.

### Negative

- Panning, zooming and animated transitions — free in markmap — are not
  implemented. If a future track's mind map turns out to be wide or deep
  enough that a static, scrollable SVG is not enough, that is new work, not a
  configuration flag on an existing library.
- The maintenance of the rendering and hit-testing code is ours. A change to
  the node shape (`docs/protocol/content-sync.md` §12.6 already anticipates
  per-node label maps arriving later) is a change to this component, not a
  version bump.

### Follow-up

- The report for this phase records the actual initial-chunk and mind-map
  lazy-chunk sizes from `npm run build:web` and `npm run build:tauri`, so the
  5.65 kB figure above is checked against what the bundler actually produced
  rather than left as an estimate.
- A wide-or-deep-tree layout (horizontal scroll versus a future zoom/pan) is
  an open question, noted here rather than solved speculatively before a real
  track's mind map is known to need it.
- Test coverage for this phase includes: node-to-lesson navigation for a
  downloaded lesson, the "offer to fetch" affordance for a node whose lesson
  is not downloaded, and keyboard movement between nodes.
