import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { type DiffSegment, diffWords, exceedsDiffLimit } from './text-diff';

/**
 * Renders the word-level diff between a fetched source and the generated
 * draft (§5.7 — the server ships both texts and leaves the diff to the
 * screen).
 *
 * Every segment is painted with plain text interpolation, never
 * `[innerHTML]`: `text-diff.ts` only ever returns literal substrings of
 * `before`/`after`, but this view still has no business being a second place
 * in the codebase that decides content is safe to inject as markup — that
 * decision belongs to `MarkdownView` alone (I8).
 *
 * Added and removed segments are never marked by background color alone —
 * each also carries its own text decoration (underline / strikethrough) and
 * a screen-reader-only label, so the distinction survives for a color-blind
 * reader, a high-contrast theme, or someone using a screen reader.
 */
@Component({
  selector: 'app-diff-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './diff-view.html',
})
export class DiffView {
  readonly before = input.required<string>();
  readonly after = input.required<string>();

  /**
   * True once the two inputs are large enough that a word-level LCS table
   * would exceed `MAX_DIFF_CELLS`. The template renders an explanation
   * instead of a diff in that case — it never falls back silently.
   */
  protected readonly tooLarge = computed(() => exceedsDiffLimit(this.before(), this.after()));

  protected readonly segments = computed<readonly DiffSegment[]>(() =>
    this.tooLarge() ? [] : diffWords(this.before(), this.after()),
  );
}
