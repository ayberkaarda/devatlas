import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { DownloadStore } from '../../core/library/download-store';
import type { LessonSummary, MindMapNode } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { buildMindMapLayout, type LayoutNode } from './mind-map-layout';

/**
 * The radius every node disc on this canvas is drawn at.
 *
 * Not a layout constant: `mind-map-layout.ts` positions node centres and knows
 * nothing about how big the marks around them are, and the spacing it produces
 * is generous enough that changing a mark's size does not move anything.
 */
const NODE_RADIUS = 8;

/**
 * The concept-leaf dot, at 44% of a lesson's radius — under a fifth of the
 * area. Large enough to stay visible at 1x and to give the connector line
 * something to end on, small enough that the eye sorts it out of the set of
 * things it could click before reading the label.
 */
const CONCEPT_RADIUS = 3.5;

/**
 * The completed mark is `shared/state-glyph.ts`'s `completed` shape, drawn
 * here as raw SVG rather than by embedding that component: this canvas is
 * hand-written SVG in one coordinate system, and an `<svg>`-rooted component
 * dropped into it would bring its own viewport and its own 16px sizing.
 *
 * Copying the geometry instead of the element keeps one drawing for one fact.
 * The glyph is authored on a 16x16 box centred on (8, 8) with a ring of radius
 * 6.25; scaling the whole group uniformly by `NODE_RADIUS / 6.25` puts its ring
 * exactly where every other node's disc edge is, so the marks line up down the
 * column, and preserves every proportion inside it — including the stroke
 * weight, which is why the tick keeps the same relationship to its ring that it
 * has at 16px on the track detail page.
 */
const GLYPH_CENTER = 8;
const GLYPH_RING_RADIUS = 6.25;
const GLYPH_SCALE = NODE_RADIUS / GLYPH_RING_RADIUS;

/**
 * The rendered tree: an SVG drawn from `buildMindMapLayout`'s output using
 * the token layer's own utility classes, with a WAI-ARIA-style tree keyboard
 * model layered on top.
 *
 * This is the piece of the mind map screen that imports `d3-hierarchy`, and
 * it is deliberately the only piece: `mind-map.page.ts` wraps it in `@defer`
 * so the dependency this component pulls in lands in its own lazy chunk
 * rather than the mind map route's own, verified in this phase's build
 * output (see `docs/adr/0001-mind-map-library.md`).
 *
 * A node whose `lessonId` is not in `lessonIndex` — not downloaded, on the
 * desktop — is not an error state: activating it offers to download the
 * lesson instead of navigating to it, per
 * `docs/protocol/content-sync.md` §4.3.
 *
 * Four node shapes are drawn, and the distinction they carry is "can I go
 * somewhere from here, and have I already been":
 *
 * - a finished lesson: the same ring-and-tick mark, in the same success
 *   colour, that the track detail page draws beside a finished lesson (see
 *   `shared/state-glyph.ts`). One fact should not have two drawings.
 * - a stored, unread lesson: a filled accent disc, as before.
 * - a lesson that is not stored yet: a dashed outline, as before.
 * - a concept leaf — a label hanging under a lesson, with no lesson of its
 *   own and nothing beneath it — a small muted dot. It is not a destination,
 *   and before it was drawn identically to an unread lesson, which invited
 *   readers to click a word that does nothing.
 *
 * Grouping nodes (the track root and its modules) keep the plain outlined
 * disc but carry a heavier label, because their disc differs from an unread
 * lesson's by nothing but a dash pattern on a 1.5px stroke.
 */
@Component({
  selector: 'app-mind-map-tree',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './mind-map-tree.html',
})
export class MindMapTree {
  private readonly router = inject(Router);
  private readonly store = inject(DownloadStore);
  private readonly platform = inject(PlatformService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  readonly root = input.required<MindMapNode>();
  readonly lessonIndex = input.required<ReadonlyMap<string, LessonSummary>>();
  /**
   * The lessons this reader has finished. Handed down rather than read here,
   * for the same reason `lessonIndex` is: this component draws a tree and
   * answers a keyboard, and it talks to the platform only to act on something
   * the reader did.
   */
  readonly completedLessonIds = input.required<ReadonlySet<string>>();
  readonly trackTitle = input.required<string>();
  readonly trackSlug = input.required<string>();

  protected readonly canDownload = this.platform.capabilities.canDownload;

  protected readonly nodeRadius = NODE_RADIUS;
  protected readonly conceptRadius = CONCEPT_RADIUS;
  protected readonly glyphRingRadius = GLYPH_RING_RADIUS;
  protected readonly glyphCenter = GLYPH_CENTER;
  /** The tick inside the ring, in the glyph's own 16x16 coordinates. */
  protected readonly glyphTickPath = 'm5.15 8.2 2 2 3.7-4.4';

  protected readonly layout = computed(() => buildMindMapLayout(this.root()));
  protected readonly focusedId = signal<string>('');
  protected readonly enqueueErrorKey = signal<string | null>(null);

  /**
   * Whether the next focused-node change was asked for by the person using the
   * tree, as opposed to falling out of the layout arriving.
   *
   * Without the distinction, the tree took focus away from wherever the reader
   * was the moment the deferred block resolved and put it on the root node.
   * Moving somebody's focus for them is only acceptable when they did
   * something to ask for it.
   */
  private readonly focusRequested = signal(false);

  constructor() {
    // Keeps the focused node valid whenever the tree itself changes (a
    // different track was opened), without fighting a focus change the user
    // just made within the same tree.
    effect(() => {
      const ids = this.layout().order;
      if (ids.length === 0) {
        return;
      }
      if (!ids.includes(untracked(this.focusedId))) {
        this.focusedId.set(ids[0]);
      }
    });

    effect(() => {
      const id = this.focusedId();
      if (!id || !this.focusRequested()) {
        return;
      }
      this.focusRequested.set(false);
      queueMicrotask(() => {
        this.host.nativeElement
          .querySelector<HTMLElement>(`[data-node-id="${cssEscapeId(id)}"]`)
          ?.focus();
      });
    });
  }

  protected lessonFor(node: LayoutNode): LessonSummary | null {
    return node.lessonId ? (this.lessonIndex().get(node.lessonId) ?? null) : null;
  }

  protected isDownloading(node: LayoutNode): boolean {
    return node.lessonId !== null && this.store.transferFor(node.lessonId) !== null;
  }

  protected isCompleted(node: LayoutNode): boolean {
    return node.lessonId !== null && this.completedLessonIds().has(node.lessonId);
  }

  /**
   * A label hanging under a lesson, rather than a heading over other nodes.
   *
   * Both a concept and a module carry no `lessonId`, so the lesson field alone
   * cannot separate them; what separates them is that a module is a heading —
   * it has nodes beneath it — and a concept is a leaf. The root is excluded by
   * its depth rather than by its children, so that a track whose map has not
   * been filled in yet still draws its root as a root.
   *
   * The one shape this misreads is a grouping node with nothing under it,
   * which would be drawn as a concept. Authored maps can contain one; the maps
   * derived from the corpus cannot, because a module there is built from a
   * fixed number of lessons and an empty one is refused before it is stored.
   */
  protected isConceptLeaf(node: LayoutNode): boolean {
    return node.lessonId === null && !node.hasChildren && node.depth > 0;
  }

  /** A grouping node: the track root, or a module heading over its lessons. */
  protected isGroup(node: LayoutNode): boolean {
    return node.lessonId === null && !this.isConceptLeaf(node);
  }

  /**
   * Places the borrowed glyph over a node centre: move its (8, 8) onto the
   * node, then scale the ring out to the node radius. Written in this order —
   * translate before scale — because the translation is expressed in the
   * outer, unscaled coordinate system, which is the one the node centre is in.
   */
  protected completedGlyphTransform(node: LayoutNode): string {
    const offset = GLYPH_CENTER * GLYPH_SCALE;
    return `translate(${node.cx - offset} ${node.cy - offset}) scale(${GLYPH_SCALE})`;
  }

  protected setFocus(id: string): void {
    this.focusRequested.set(true);
    this.focusedId.set(id);
  }

  /** How many nodes share this one's parent, for `aria-setsize`. */
  protected siblingCount(node: LayoutNode): number {
    return this.siblingsOf(node).length;
  }

  /** This node's one-based position among its siblings, for `aria-posinset`. */
  protected positionInSiblings(node: LayoutNode): number {
    return this.siblingsOf(node).indexOf(node.id) + 1;
  }

  private siblingsOf(node: LayoutNode): readonly string[] {
    const { parentOf, childrenOf, order } = this.layout();
    const parent = parentOf.get(node.id) ?? null;
    // A root has no parent to enumerate, and this tree has exactly one, so the
    // set it belongs to is the set of roots.
    return parent === null ? order.slice(0, 1) : (childrenOf.get(parent) ?? []);
  }

  protected onKeydown(event: KeyboardEvent, node: LayoutNode): void {
    const { parentOf, childrenOf, order } = this.layout();
    switch (event.key) {
      case 'ArrowRight': {
        const [first] = childrenOf.get(node.id) ?? [];
        if (first) {
          this.setFocus(first);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowLeft': {
        const parent = parentOf.get(node.id);
        if (parent) {
          this.setFocus(parent);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowDown': {
        const index = order.indexOf(node.id);
        if (index >= 0 && index < order.length - 1) {
          this.setFocus(order[index + 1]);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowUp': {
        const index = order.indexOf(node.id);
        if (index > 0) {
          this.setFocus(order[index - 1]);
          event.preventDefault();
        }
        break;
      }
      case 'Home':
        this.setFocus(order[0]);
        event.preventDefault();
        break;
      case 'End':
        this.setFocus(order[order.length - 1]);
        event.preventDefault();
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        void this.activate(node);
        break;
      default:
        break;
    }
  }

  protected async activate(node: LayoutNode): Promise<void> {
    if (!node.lessonId) {
      return;
    }
    const lesson = this.lessonFor(node);
    if (lesson?.availability.readable) {
      await this.router.navigate(['/tracks', this.trackSlug(), 'lessons', lesson.slug]);
      return;
    }
    if (!this.canDownload || this.isDownloading(node)) {
      return;
    }
    this.enqueueErrorKey.set(null);
    try {
      await this.store.enqueue({ kind: 'LESSON', id: node.lessonId });
    } catch {
      this.enqueueErrorKey.set('error.INTERNAL_ERROR');
    }
  }
}

/** `CSS.escape` is not available in every test environment this runs under. */
function cssEscapeId(value: string): string {
  return value.replace(/[^a-zA-Z0-9_-]/g, (char) => `\\${char}`);
}
