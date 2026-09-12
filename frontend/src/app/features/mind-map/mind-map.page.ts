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

import type { DownloadUnit } from '../../core/library/aggregate';
import { LibraryDiscovery } from '../../core/library/library-discovery';
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
  private readonly discovery = inject(LibraryDiscovery);

  readonly trackSlug = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly failureKey = signal<string | null>(null);
  protected readonly track = signal<TrackDetail | null>(null);
  protected readonly mindMap = signal<MindMap | null>(null);
  /** Specifically: the track has a mind map, but it is not in the local library yet. */
  protected readonly mindMapNotDownloaded = signal(false);
  /** A discovery attempt that failed. Non-blocking: the track above still renders. */
  protected readonly noteKey = signal<string | null>(null);

  /**
   * The lessons this reader has finished, by identifier.
   *
   * Read from the progress store rather than from the lessons themselves,
   * because the summary model a mind map node resolves to carries no
   * completion field and only the full lesson does. One read answers for every
   * node on the canvas, which is why it is owned here alongside the map rather
   * than inside the renderer: the renderer draws what it is given.
   */
  protected readonly completedLessonIds = signal<ReadonlySet<string>>(new Set<string>());

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

  /**
   * The mind map as one more unit the track download can fetch. This is the
   * screen where leaving it out hurt most: with every lesson already stored,
   * a lesson-only aggregate reported the track complete and rendered no
   * button, so the card offering to download the missing mind map offered no
   * way to do it.
   */
  protected readonly mindMapUnits = computed<readonly DownloadUnit[]>(() => {
    const mindMap = this.track()?.mindMap;
    return mindMap ? [mindMap] : [];
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
    this.noteKey.set(null);
    this.mindMap.set(null);
    this.mindMapNotDownloaded.set(false);
    this.completedLessonIds.set(new Set<string>());

    // Started here and awaited in `finally`, so the progress read overlaps the
    // track read instead of queueing behind it.
    const completionPromise = this.loadCompletions();

    try {
      const initial = await this.platform.getTrack(slug);
      const outcome = await this.discovery.discoverTrack(initial);
      const detail = outcome.value;
      this.noteKey.set(outcome.noteKey);
      this.track.set(detail);
      if (!detail.mindMap) {
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
      this.completedLessonIds.set(await completionPromise);
      this.loading.set(false);
    }
  }

  /**
   * Reads which lessons are finished, and says nothing when it cannot.
   *
   * Failing here is an ordinary condition rather than a fault: a reader with
   * no session gets a refusal from the progress endpoint every time, and there
   * is no progress of theirs to show because there is none. The map renders
   * exactly as it would for a reader who has finished nothing, with no error
   * and no note — the map itself is still perfectly readable without it, and
   * announcing a failure to someone who never signed in explains nothing.
   */
  private async loadCompletions(): Promise<ReadonlySet<string>> {
    try {
      const entries = await this.platform.listProgress();
      return new Set(
        entries.filter((entry) => entry.completedAt !== null).map((entry) => entry.lessonId),
      );
    } catch {
      return new Set<string>();
    }
  }
}
