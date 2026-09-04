import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { SafeHtml } from '@angular/platform-browser';
import { TranslatePipe } from '@ngx-translate/core';

import { MarkdownService } from '../../core/markdown/markdown.service';
import { errorKey } from '../../core/platform/error-key';
import type { Lesson } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ThemeService } from '../../core/theme/theme.service';
import { FallbackBadge } from '../../shared/fallback-badge';

interface RenderedExample {
  readonly caption: string | null;
  readonly language: string;
  readonly html: SafeHtml;
}

@Component({
  selector: 'app-lesson-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, FallbackBadge],
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
    } catch (error) {
      this.lesson.set(null);
      this.body.set(null);
      this.examples.set([]);
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
