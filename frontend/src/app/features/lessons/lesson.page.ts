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
import type { SafeHtml } from '@angular/platform-browser';
import { TranslatePipe } from '@ngx-translate/core';

import { MarkdownService } from '../../core/markdown/markdown.service';
import { errorKey } from '../../core/platform/error-key';
import { PlatformError } from '../../core/platform/errors';
import type { LessonSummary, Lesson, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ProgressSyncService } from '../../core/sync/progress-sync.service';
import { ThemeService } from '../../core/theme/theme.service';
import { FallbackBadge } from '../../shared/fallback-badge';
import { LessonDownloadControls } from '../../shared/lesson-download-controls';

interface RenderedExample {
  readonly caption: string | null;
  readonly language: string;
  readonly html: SafeHtml;
}

/** One lesson in the path-wide reading order, with the module it came from. */
interface SequencedLesson {
  readonly lesson: LessonSummary;
  readonly moduleTitle: string;
}

/**
 * Flattens a track into the order a reader works through it.
 *
 * `LessonSummary.order` is the position *within a module*, so it is not a
 * path-wide sequence and sorting a flattened list by it alone would
 * interleave the modules: every module has a lesson numbered 0. The order is
 * therefore composed — modules by `ModuleDetail.order`, then lessons by their
 * own order inside each one — and only then flattened.
 *
 * Both sorts run on copies. The payload's arrays are the view models the rest
 * of the screen renders from, and sorting one in place would reorder what the
 * caller is holding.
 */
function readingOrder(track: TrackDetail | null): readonly SequencedLesson[] {
  if (!track) {
    return [];
  }
  return [...track.modules]
    .sort((left, right) => left.order - right.order)
    .flatMap((module) =>
      [...module.lessons]
        .sort((left, right) => left.order - right.order)
        .map((lesson) => ({ lesson, moduleTitle: module.title })),
    );
}

@Component({
  selector: 'app-lesson-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, FallbackBadge, LessonDownloadControls],
  templateUrl: './lesson.page.html',
})
export class LessonPage {
  private readonly platform = inject(PlatformService);
  private readonly markdown = inject(MarkdownService);
  private readonly theme = inject(ThemeService);
  private readonly progressSync = inject(ProgressSyncService);

  readonly trackSlug = input.required<string>();
  readonly lessonSlug = input.required<string>();

  protected readonly lesson = signal<Lesson | null>(null);
  protected readonly body = signal<SafeHtml | null>(null);
  protected readonly examples = signal<readonly RenderedExample[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  /**
   * A failure to record a completion, kept apart from the load failure above.
   *
   * They are not the same event and they must not have the same consequence:
   * the load failure replaces the screen, because there is nothing to show,
   * while a progress write that did not land leaves a perfectly readable
   * lesson on the page. Sharing one signal meant a failed toggle threw away
   * the article the reader was in the middle of.
   */
  protected readonly toggleFailureKey = signal<string | null>(null);

  /**
   * Whether this reader has finished the lesson, seeded from what the lesson
   * came with rather than assumed.
   *
   * Starting it at `false` and waiting for a click made the screen offer to
   * mark a lesson the reader finished last week, every time they opened it:
   * the server and the local replica both know the answer, and the screen was
   * the only place it was being thrown away.
   */
  protected readonly completed = signal(false);

  /**
   * The track this lesson belongs to, fetched on every build rather than only
   * where content can be downloaded.
   *
   * It carries the structure this screen needs to place the lesson: which
   * module holds it, and which lesson comes before and after it across the
   * whole path. Reading it only where there is a local library left the web
   * build with a lesson and no way onward, so a reader finishing one had to
   * go back to the path and find the next one by hand. On the web this costs
   * one extra request per lesson, which buys the only navigation the screen
   * has.
   *
   * It is null whenever that read failed, and everything derived from it
   * renders nothing in that case. Losing the article because its neighbours
   * could not be listed would be a far worse trade than the one this makes.
   */
  protected readonly track = signal<TrackDetail | null>(null);

  /**
   * The lesson's entry in its track's manifest — structural data, already a
   * local read on the desktop — so the download control has an identifier and
   * a precise availability to render, whichever branch below is showing.
   *
   * Derived rather than fetched separately now that the track is read on both
   * builds. On the web the download control this feeds renders nothing at
   * all, because it asks the capability itself.
   */
  protected readonly lessonSummary = computed<LessonSummary | null>(
    () => this.sequence().find((entry) => entry.lesson.slug === this.lessonSlug())?.lesson ?? null,
  );

  /**
   * Set when the read failed because the lesson has not been downloaded, on a
   * build that could download it. This is a normal state, not the generic
   * error path: reads never fall back to the network, so a lesson a user
   * navigated to directly is exactly this case, and the right response is to
   * offer the download, not to say something went wrong.
   */
  protected readonly notDownloaded = signal(false);

  /** Every lesson in the track, in the order a reader meets them. */
  protected readonly sequence = computed(() => readingOrder(this.track()));

  private readonly position = computed(() =>
    this.sequence().findIndex((entry) => entry.lesson.slug === this.lessonSlug()),
  );

  /**
   * The track's title for the breadcrumb, taken from the track that was
   * fetched and falling back to what the lesson itself carried.
   *
   * The two sources do not overlap: the desktop's lesson read has no track or
   * module title on it at all, because a locally stored lesson row does not
   * repeat its container's translated title, while the track read has it on
   * both builds. The fallback covers the reverse case — a lesson that arrived
   * with its track named but whose track read failed — and null means the
   * crumb is dropped rather than filled with a guess.
   */
  protected readonly breadcrumbTrackTitle = computed<string | null>(
    () => this.track()?.title ?? this.lesson()?.trackTitle ?? null,
  );

  /** The module holding this lesson, by the same rule as the track crumb. */
  protected readonly breadcrumbModuleTitle = computed<string | null>(() => {
    const index = this.position();
    if (index >= 0) {
      return this.sequence()[index].moduleTitle;
    }
    return this.lesson()?.moduleTitle ?? null;
  });

  /**
   * How far into the path this lesson sits, one-based for a reader. Null when
   * the track could not be read, or when it holds no lesson with this slug —
   * which happens while a stale track is on screen and the next one is still
   * loading, and reporting "lesson 0 of 12" through that window would be
   * worse than reporting nothing.
   */
  protected readonly positionLabel = computed<{ index: number; total: number } | null>(() => {
    const index = this.position();
    return index < 0 ? null : { index: index + 1, total: this.sequence().length };
  });

  protected readonly previousLesson = computed<LessonSummary | null>(() => {
    const index = this.position();
    return index > 0 ? this.sequence()[index - 1].lesson : null;
  });

  protected readonly nextLesson = computed<LessonSummary | null>(() => {
    const index = this.position();
    const all = this.sequence();
    return index >= 0 && index < all.length - 1 ? all[index + 1].lesson : null;
  });

  /**
   * Whether the reader has reached the end of the path, which is a different
   * fact from "there is no next lesson": an unknown position also has no next
   * lesson, and only one of the two should turn the forward link into the way
   * back to the path.
   */
  protected readonly atPathEnd = computed(() => {
    const index = this.position();
    return index >= 0 && index === this.sequence().length - 1;
  });

  constructor() {
    // Re-runs when the lesson changes and when the palette does, because a
    // code block's colours are baked into the markup the highlighter produced
    // and would otherwise stay light inside a dark page.
    effect(() => {
      const slug = this.lessonSlug();
      const theme = this.theme.resolved();
      void this.load(slug, theme);
    });
  }

  protected reload(): void {
    void this.load(this.lessonSlug(), this.theme.resolved());
  }

  protected async toggleCompleted(): Promise<void> {
    const current = this.lesson();
    if (!current) {
      return;
    }
    const next = !this.completed();
    this.toggleFailureKey.set(null);
    try {
      await this.platform.markProgress(current.id, next);
      this.completed.set(next);
      // The completion is already recorded; sending it is a separate,
      // best-effort act that this screen never waits for and never reports
      // on. Where progress is written straight to the server this is a
      // no-op, and where it is queued locally a failure leaves the row
      // pending for the next attempt.
      void this.progressSync.sync();
    } catch (error) {
      this.toggleFailureKey.set(errorKey(error));
    }
  }

  private async load(slug: string, theme: 'light' | 'dark'): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.toggleFailureKey.set(null);
    this.notDownloaded.set(false);
    this.track.set(null);

    // Started before the lesson read and awaited after it, so the two run
    // together rather than one after the other.
    const trackPromise = this.loadTrack(this.trackSlug());

    try {
      const lesson = await this.platform.getLesson(slug);
      this.lesson.set(lesson);
      this.completed.set(lesson.completedAt !== null);
      this.body.set(await this.markdown.renderTrusted(lesson.bodyMarkdown, theme));
      this.examples.set(
        await Promise.all(
          [...lesson.codeExamples]
            .sort((left, right) => left.order - right.order)
            .map(async (example) => ({
              caption: example.caption,
              language: example.language,
              html: await this.markdown.highlightTrusted(example.code, example.language, theme),
            })),
        ),
      );
      this.track.set(await trackPromise);
    } catch (error) {
      this.lesson.set(null);
      this.body.set(null);
      this.examples.set([]);
      // No lesson on screen means no completion on screen, and leaving the
      // previous lesson's answer behind would label the next one with it.
      this.completed.set(false);
      this.track.set(await trackPromise);
      if (
        error instanceof PlatformError &&
        error.code === 'ENTITY_NOT_IN_LIBRARY' &&
        this.lessonSummary()
      ) {
        this.notDownloaded.set(true);
      } else {
        this.failure.set(errorKey(error));
      }
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Reads the track this lesson belongs to. Best-effort by design: a failure
   * here never blocks the lesson read, it only means the breadcrumb and the
   * previous/next links have nothing to render.
   */
  private async loadTrack(slug: string): Promise<TrackDetail | null> {
    try {
      return await this.platform.getTrack(slug);
    } catch {
      return null;
    }
  }
}
