import type { MindMapNode } from '../../core/platform/models';
import { buildMindMapLayout } from './mind-map-layout';

function node(
  id: string,
  children: MindMapNode[] = [],
  lessonId: string | null = null,
): MindMapNode {
  return { id, label: id, lessonId, children };
}

describe('buildMindMapLayout', () => {
  const tree = node('root', [
    node('signals', [
      node('signals-basics', [], 'lesson-1'),
      node('signals-advanced', [], 'lesson-2'),
    ]),
    node('routing', [], 'lesson-3'),
  ]);

  it('positions every node, root included', () => {
    const layout = buildMindMapLayout(tree);
    expect(layout.nodes.map((n) => n.id).sort()).toEqual(
      ['root', 'routing', 'signals', 'signals-advanced', 'signals-basics'].sort(),
    );
  });

  it('places children strictly to the right of their parent', () => {
    const layout = buildMindMapLayout(tree);
    const byId = new Map(layout.nodes.map((n) => [n.id, n]));
    expect(byId.get('signals')!.cx).toBeGreaterThan(byId.get('root')!.cx);
    expect(byId.get('signals-basics')!.cx).toBeGreaterThan(byId.get('signals')!.cx);
  });

  it('keeps every coordinate non-negative, whatever the tree shape', () => {
    const layout = buildMindMapLayout(tree);
    for (const n of layout.nodes) {
      expect(n.cx).toBeGreaterThanOrEqual(0);
      expect(n.cy).toBeGreaterThanOrEqual(0);
    }
  });

  it('carries the lesson id through to the node a person would click', () => {
    const layout = buildMindMapLayout(tree);
    const target = layout.nodes.find((n) => n.id === 'signals-basics');
    expect(target?.lessonId).toBe('lesson-1');
  });

  it('leaves lessonId null for a purely structural node', () => {
    const layout = buildMindMapLayout(tree);
    const target = layout.nodes.find((n) => n.id === 'signals');
    expect(target?.lessonId).toBeNull();
  });

  it('produces one link per non-root node, each pointing at its parent', () => {
    const layout = buildMindMapLayout(tree);
    expect(layout.links).toHaveLength(4);
    expect(layout.links.every((link) => link.sourceId !== link.targetId)).toBe(true);
  });

  it('walks the tree depth-first for keyboard order', () => {
    const layout = buildMindMapLayout(tree);
    expect(layout.order).toEqual([
      'root',
      'signals',
      'signals-basics',
      'signals-advanced',
      'routing',
    ]);
  });

  it('records parent and child relationships for arrow-key navigation', () => {
    const layout = buildMindMapLayout(tree);
    expect(layout.parentOf.get('root')).toBeNull();
    expect(layout.parentOf.get('signals-basics')).toBe('signals');
    expect(layout.childrenOf.get('root')).toEqual(['signals', 'routing']);
    expect(layout.childrenOf.get('routing')).toEqual([]);
  });

  it('handles a single-node tree without dividing by anything undefined', () => {
    const layout = buildMindMapLayout(node('solo'));
    expect(layout.nodes).toHaveLength(1);
    expect(layout.links).toHaveLength(0);
    expect(layout.width).toBeGreaterThan(0);
    expect(layout.height).toBeGreaterThan(0);
  });
});
