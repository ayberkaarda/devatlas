import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { errorKey } from '../../core/platform/error-key';
import { PlatformError } from '../../core/platform/errors';
import type { LessonSummary, MindMap, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ContainerDownloadAction } from '../../shared/container-download-action';
import { MindMapTree } from './mind-map-tree';

/**
 * Loads a track's mind map and hands it to the renderer, or explains why
 * there is nothing to render.
 *
 * `MindMapTree` — the piece that imports `d3-hierarchy` — is referenced only
 * inside the `@defer` block below and nowhere else in this file, which is
 * what lets the Angular compiler split it into its own lazy chunk rather than
 * folding it into this already-lazy route. See
 * `docs/adr/0001-mind-map-library.md`.
 */
@Component({
  selector: 'app-mind-map-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, ContainerDownloadAction, MindMapTree],
  templateUrl: './mind-map.page.html',
})
export class MindMapPage {
  private readonly platform = inject(PlatformService);

  readonly trackSlug = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly failureKey = signal<string | null>(null);
  protected readonly track = signal<TrackDetail | null>(null);
  protected readonly mindMap = signal<MindMap | null>(null);
  /** Specifically: the track has a mind map, but it is not in the local library yet. */
  protected readonly mindMapNotDownloaded = signal(false);

  protected readonly lessonIndex = computed<ReadonlyMap<string, LessonSummary>>(() => {
    const map = new Map<string, LessonSummary>();
    for (const lesson of this.allLessons()) {
      map.set(lesson.id, lesson);
    }
    return map;
  });

  protected readonly allLessons = computed<readonly LessonSummary[]>(() => {
    const detail = this.track();
    return detail ? detail.modules.flatMap((module) => module.lessons) : [];
  });

  constructor() {
    effect(() => {
      void this.load(this.trackSlug());
    });
  }

  protected reload(): void {
    void this.load(this.trackSlug());
  }

  private async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.failureKey.set(null);
    this.mindMap.set(null);
    this.mindMapNotDownloaded.set(false);
    try {
      const detail = await this.platform.getTrack(slug);
      this.track.set(detail);
      if (!detail.hasMindMap) {
        return;
      }
      try {
        this.mindMap.set(await this.platform.getMindMap(slug));
      } catch (error) {
        // Reads never fall back to the network: on the desktop, a track that
        // has a mind map but has never been downloaded fails this call, and
        // that is a normal state to offer a download for, not an error.
        if (error instanceof PlatformError && error.code === 'ENTITY_NOT_IN_LIBRARY') {
          this.mindMapNotDownloaded.set(true);
        } else {
          throw error;
        }
      }
    } catch (error) {
      this.track.set(null);
      this.failureKey.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
