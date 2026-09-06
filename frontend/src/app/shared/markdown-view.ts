import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import type { SafeHtml } from '@angular/platform-browser';

import { MarkdownService } from '../core/markdown/markdown.service';
import type { ResolvedTheme } from '../core/platform/models';
import { ThemeService } from '../core/theme/theme.service';

/**
 * Renders one markdown body wherever an admin screen needs to show one: a
 * blog post preview, the review screen's generated-draft panel, an editor's
 * live preview.
 *
 * It wraps the same render-on-theme-change effect `LessonPage` writes by
 * hand for lesson bodies, so every admin screen that needs a rendered
 * markdown body gets it from one place instead of repeating the wiring, and
 * a code block never ends up light inside a dark page because a caller
 * forgot to re-render on a theme change.
 *
 * The template carries exactly one DOM-writing binding, `[innerHTML]`, and
 * what it binds is never the raw `markdown` input: it is the return value of
 * `MarkdownService.renderTrusted`, which runs the markdown through DOMPurify
 * and marks only DOMPurify's own output as trusted afterwards -- sanitize,
 * then trust, never the reverse. There is no `bypassSecurityTrust*` call in
 * this file; that call lives inside `MarkdownService` alone, and this
 * component only ever renders what it returns.
 */
@Component({
  selector: 'app-markdown-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="markdown-body" [innerHTML]="html()"></div>`,
})
export class MarkdownView {
  private readonly markdownService = inject(MarkdownService);
  private readonly theme = inject(ThemeService);

  readonly markdown = input.required<string>();

  protected readonly html = signal<SafeHtml | null>(null);

  constructor() {
    effect(() => {
      const markdown = this.markdown();
      const theme = this.theme.resolved();
      void this.render(markdown, theme);
    });
  }

  private async render(markdown: string, theme: ResolvedTheme): Promise<void> {
    this.html.set(await this.markdownService.renderTrusted(markdown, theme));
  }
}
