import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { SafeHtml } from '@angular/platform-browser';
import { TranslatePipe } from '@ngx-translate/core';

import { MarkdownService } from '../../core/markdown/markdown.service';
import { errorKey } from '../../core/platform/error-key';
import { PlatformError } from '../../core/platform/errors';
import type { Lesson, LessonSummary } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ThemeService } from '../../core/theme/theme.service';
import { FallbackBadge } from '../../shared/fallback-badge';
import { LessonDownloadControls } from '../../shared/lesson-download-controls';

interface RenderedExample {
  readonly caption: string | null;
  readonly language: string;
  readonly html: SafeHtml;
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

  readonly trackSlug = input.required<string>();
  readonly lessonSlug = input.required<string>();

  protected readonly lesson = signal<Lesson | null>(null);
  protected readonly body = signal<SafeHtml | null>(null);
  protected readonly examples = signal<readonly RenderedExample[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly completed = signal(false);

  /**
   * The lesson's entry in its track's manifest — structural data, already a
   * local read on the desktop — kept alongside the full lesson so the
   * download control has an identifier and a precise availability to render,
   * whichever branch below is showing.
   */
  protected readonly lessonSummary = signal<LessonSummary | null>(null);

  /**
   * Set when the read failed because the lesson has not been downloaded, on a
   * build that could download it. This is a normal state, not the generic
   * error path: reads never fall back to the network, so a lesson a user
   * navigated to directly is exactly this case, and the right response is to
   * offer the download, not to say something went wrong.
   */
  protected readonly notDownloaded = signal(false);

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
    try {
      await this.platform.markProgress(current.id, next);
      this.completed.set(next);
    } catch (error) {
      this.failure.set(errorKey(error));
    }
  }

  private async load(slug: string, theme: 'light' | 'dark'): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.notDownloaded.set(false);
    this.lessonSummary.set(null);

    const summaryPromise = this.platform.capabilities.canDownload
      ? this.resolveLessonSummary(slug)
      : Promise.resolve(null);

    try {
      const lesson = await this.platform.getLesson(slug);
      this.lesson.set(lesson);
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
      this.lessonSummary.set(await summaryPromise);
    } catch (error) {
      this.lesson.set(null);
      this.body.set(null);
      this.examples.set([]);
      const summary = await summaryPromise;
      if (error instanceof PlatformError && error.code === 'ENTITY_NOT_IN_LIBRARY' && summary) {
        this.lessonSummary.set(summary);
        this.notDownloaded.set(true);
      } else {
        this.failure.set(errorKey(error));
      }
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Looks the lesson up by slug in its track's manifest, already-read
   * structural data rather than a fallback content read, so the download
   * control has the identifier and availability it needs. Best-effort: a
   * failure here never blocks the main lesson read, it just means no download
   * control is offered.
   */
  private async resolveLessonSummary(slug: string): Promise<LessonSummary | null> {
    try {
      const track = await this.platform.getTrack(this.trackSlug());
      return (
        track.modules
          .flatMap((module) => module.lessons)
          .find((candidate) => candidate.slug === slug) ?? null
      );
    } catch {
      return null;
    }
  }
}
