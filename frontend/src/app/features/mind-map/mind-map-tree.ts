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
  readonly trackTitle = input.required<string>();
  readonly trackSlug = input.required<string>();

  protected readonly canDownload = this.platform.capabilities.canDownload;

  protected readonly layout = computed(() => buildMindMapLayout(this.root()));
  protected readonly focusedId = signal<string>('');
  protected readonly enqueueErrorKey = signal<string | null>(null);

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
      if (!id) {
        return;
      }
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

  protected setFocus(id: string): void {
    this.focusedId.set(id);
  }

  protected onKeydown(event: KeyboardEvent, node: LayoutNode): void {
    const { parentOf, childrenOf, order } = this.layout();
    switch (event.key) {
      case 'ArrowRight': {
        const [first] = childrenOf.get(node.id) ?? [];
        if (first) {
          this.focusedId.set(first);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowLeft': {
        const parent = parentOf.get(node.id);
        if (parent) {
          this.focusedId.set(parent);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowDown': {
        const index = order.indexOf(node.id);
        if (index >= 0 && index < order.length - 1) {
          this.focusedId.set(order[index + 1]);
          event.preventDefault();
        }
        break;
      }
      case 'ArrowUp': {
        const index = order.indexOf(node.id);
        if (index > 0) {
          this.focusedId.set(order[index - 1]);
          event.preventDefault();
        }
        break;
      }
      case 'Home':
        this.focusedId.set(order[0]);
        event.preventDefault();
        break;
      case 'End':
        this.focusedId.set(order[order.length - 1]);
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
