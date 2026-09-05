import { hierarchy, tree } from 'd3-hierarchy';

import type { MindMapNode } from '../../core/platform/models';

/**
 * The pure layout step: a `MindMapNode` tree in, screen coordinates and
 * traversal order out. Nothing here touches Angular, the DOM, or the
 * platform layer, which is what makes it possible to test the geometry and
 * the keyboard traversal order without rendering anything — see
 * `docs/adr/0001-mind-map-library.md`.
 *
 * `d3-hierarchy`'s `tree()` lays a tree out top-to-bottom by convention: `x`
 * is the position along the spread axis, `y` is the depth axis. This mind map
 * reads left-to-right instead — the root at the left margin, children
 * branching rightward — which is the standard swap: what `d3` calls `y`
 * (depth) becomes the horizontal coordinate here, and what it calls `x`
 * (spread) becomes the vertical one.
 */
export interface LayoutNode {
  readonly id: string;
  readonly label: string;
  readonly lessonId: string | null;
  readonly depth: number;
  readonly hasChildren: boolean;
  /** Horizontal position: proportional to depth. */
  readonly cx: number;
  /** Vertical position: where this node's subtree spreads to. */
  readonly cy: number;
}

export interface LayoutLink {
  readonly sourceId: string;
  readonly targetId: string;
  readonly source: { readonly cx: number; readonly cy: number };
  readonly target: { readonly cx: number; readonly cy: number };
}

export interface MindMapLayout {
  readonly nodes: readonly LayoutNode[];
  readonly links: readonly LayoutLink[];
  /** Depth-first pre-order of node ids — the order arrow-key Up/Down follows. */
  readonly order: readonly string[];
  readonly parentOf: ReadonlyMap<string, string | null>;
  readonly childrenOf: ReadonlyMap<string, readonly string[]>;
  readonly width: number;
  readonly height: number;
}

const MARGIN = 24;

export function buildMindMapLayout(
  root: MindMapNode,
  nodeWidth = 200,
  nodeHeight = 56,
): MindMapLayout {
  const hierarchyRoot = hierarchy(root, (node) =>
    node.children.length > 0 ? node.children : null,
  );
  const layout = tree<MindMapNode>().nodeSize([nodeHeight, nodeWidth])(hierarchyRoot);

  let minSpread = Number.POSITIVE_INFINITY;
  let maxSpread = Number.NEGATIVE_INFINITY;
  let maxDepth = 0;
  layout.each((node) => {
    minSpread = Math.min(minSpread, node.x);
    maxSpread = Math.max(maxSpread, node.x);
    maxDepth = Math.max(maxDepth, node.depth);
  });
  if (!Number.isFinite(minSpread)) {
    minSpread = 0;
    maxSpread = 0;
  }

  const spreadOffset = MARGIN - minSpread;
  // `depthPixels` is `d3`'s `y`, already scaled by `nodeWidth` per depth level
  // via `nodeSize` above — a pixel offset, not a literal depth index.
  const toScreen = (spread: number, depthPixels: number) => ({
    cy: spread + spreadOffset,
    cx: depthPixels + MARGIN,
  });

  const nodes: LayoutNode[] = [];
  const order: string[] = [];
  const parentOf = new Map<string, string | null>();
  const childrenOf = new Map<string, readonly string[]>();

  // Depth-first pre-order, not the breadth-first `each`: this pass builds the
  // sequence keyboard navigation follows, and a reader moving through a tree
  // expects a parent to be followed by its own children rather than by its
  // siblings' children. Breadth-first would interleave unrelated branches.
  layout.eachBefore((node) => {
    const { cx, cy } = toScreen(node.x, node.y);
    nodes.push({
      id: node.data.id,
      label: node.data.label,
      lessonId: node.data.lessonId,
      depth: node.depth,
      hasChildren: (node.children?.length ?? 0) > 0,
      cx,
      cy,
    });
    order.push(node.data.id);
    parentOf.set(node.data.id, node.parent ? node.parent.data.id : null);
    childrenOf.set(
      node.data.id,
      (node.children ?? []).map((child) => child.data.id),
    );
  });

  const links: LayoutLink[] = layout.links().map((link) => {
    const source = toScreen(link.source.x, link.source.y);
    const target = toScreen(link.target.x, link.target.y);
    return {
      sourceId: link.source.data.id,
      targetId: link.target.data.id,
      source,
      target,
    };
  });

  return {
    nodes,
    links,
    order,
    parentOf,
    childrenOf,
    width: maxDepth * nodeWidth + nodeWidth + MARGIN * 2,
    height: maxSpread - minSpread + nodeHeight + MARGIN * 2,
  };
}
