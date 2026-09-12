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
import type { LessonSummary, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ContainerDownloadAction } from '../../shared/container-download-action';
import { FallbackBadge } from '../../shared/fallback-badge';
import { LessonDownloadControls } from '../../shared/lesson-download-controls';
import { StateGlyph } from '../../shared/state-glyph';

@Component({
  selector: 'app-track-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    TranslatePipe,
    FallbackBadge,
    ContainerDownloadAction,
    LessonDownloadControls,
    StateGlyph,
  ],
  templateUrl: './track-detail.page.html',
})
export class TrackDetailPage {
  private readonly platform = inject(PlatformService);
  private readonly discovery = inject(LibraryDiscovery);

  /** Bound from the route. */
  readonly trackSlug = input.required<string>();

  protected readonly track = signal<TrackDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  /** A discovery attempt that failed. Non-blocking: the track above still renders. */
  protected readonly noteKey = signal<string | null>(null);

  protected readonly canDownload = this.platform.capabilities.canDownload;

  /**
   * The lessons this reader has finished, by identifier.
   *
   * Read from the progress store rather than from the lessons themselves: the
   * summary model a path renders carries no completion field, and only the
   * full lesson does. One read answers for every row on the screen, which is
   * also why it is not folded into the track read.
   */
  protected readonly completedLessonIds = signal<ReadonlySet<string>>(new Set<string>());

  /** Every lesson in the track, flattened, for the track-level download action. */
  protected readonly allLessons = computed<readonly LessonSummary[]>(() => {
    const detail = this.track();
    return detail ? detail.modules.flatMap((module) => module.lessons) : [];
  });

  /** How many of the whole path is behind the reader, and out of how many. */
  protected readonly trackCompletion = computed(() => {
    const done = this.completedLessonIds();
    const lessons = this.allLessons();
    return {
      completed: lessons.filter((lesson) => done.has(lesson.id)).length,
      total: lessons.length,
    };
  });

  /**
   * The same count per module, keyed by module id.
   *
   * Computed once into a map rather than counted per row from the template,
   * so a path with many modules does not re-filter the completed set on every
   * change detection pass.
   */
  protected readonly moduleCompletion = computed<ReadonlyMap<string, number>>(() => {
    const done = this.completedLessonIds();
    const detail = this.track();
    return new Map(
      (detail?.modules ?? []).map((module) => [
        module.id,
        module.lessons.filter((lesson) => done.has(lesson.id)).length,
      ]),
    );
  });

  /**
   * The one lesson to resume at: the first unfinished one, in the order the
   * path itself is written.
   *
   * It walks the modules and their lessons exactly as they render rather than
   * sorting by `order`, because the rendered sequence *is* the author's
   * sequence; a second ordering rule here would let the highlight land on a
   * row other than the one a reader's eye reaches first.
   *
   * It reads the same completed set the row marks and the counts read, so
   * "which lessons are done" is asked once and answered once. Two independent
   * derivations of that question would eventually disagree — one row marked
   * finished while the resume hint still points at it.
   *
   * Null when every lesson is finished. Nothing is highlighted then, and that
   * is the intended reading: there is no next lesson, and tinting the last
   * one would invite a reader to re-read something they have already closed
   * out. A path with nothing finished yields its very first lesson, which is
   * the correct answer rather than a case to suppress — for a reader who has
   * not started, where to resume is where to begin.
   */
  protected readonly upNextLessonId = computed<string | null>(() => {
    const done = this.completedLessonIds();
    for (const module of this.track()?.modules ?? []) {
      for (const lesson of module.lessons) {
        if (!done.has(lesson.id)) {
          return lesson.id;
        }
      }
    }
    return null;
  });

  /**
   * The mind map as one more unit the track-level action can fetch. Counting
   * it is what keeps the download button on a track whose lessons are all
   * stored but whose mind map is not.
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

  protected isCompleted(lessonId: string): boolean {
    return this.completedLessonIds().has(lessonId);
  }

  protected isUpNext(lessonId: string): boolean {
    return this.upNextLessonId() === lessonId;
  }

  private async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.noteKey.set(null);
    this.completedLessonIds.set(new Set<string>());

    // Started here and awaited after the track, so the two overlap.
    const completionPromise = this.loadCompletions();

    try {
      const initial = await this.platform.getTrack(slug);
      const outcome = await this.discovery.discoverTrack(initial);
      this.track.set(outcome.value);
      this.noteKey.set(outcome.noteKey);
    } catch (error) {
      this.track.set(null);
      this.failure.set(errorKey(error));
    } finally {
      this.completedLessonIds.set(await completionPromise);
      this.loading.set(false);
    }
  }

  /**
   * Reads which lessons are finished, and says nothing when it cannot.
   *
   * Failing here is an ordinary condition rather than a fault: a reader with
   * no session gets a refusal from the progress endpoint every time, and
   * there is no progress of theirs to show because there is none. The screen
   * renders exactly as it would for a reader who has finished nothing, with
   * no error and no note — announcing "your progress could not be loaded" to
   * someone who never signed in explains nothing and offers no action.
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
