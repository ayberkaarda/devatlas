import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Previous/next paging for an admin list, plus a "x / y" position readout.
 *
 * Zero-based `page`, matching `docs/protocol/rest-api.md` §2.5 — the same
 * index every `AdminApiClient` list method sends and every `Page<T>`
 * envelope returns, so a screen wires this directly to the query it already
 * has without converting between a zero-based wire index and a one-based
 * display one in two different places.
 *
 * Text comes from `admin.common.*` translation keys rather than being built
 * here: this component owns layout and paging behaviour, not the strings
 * that describe them.
 *
 * The longer wording is a description rather than the buttons' accessible
 * name. A name that replaces the visible word breaks voice control, which
 * matches what a person can read on screen — and it breaks it unevenly across
 * languages, because "Zurück" is not a substring of "Zur vorherigen Seite
 * gehen" while "Previous" is a substring of "Go to the previous page". Leaving
 * the visible word as the name makes the guarantee hold in every locale
 * without asking translators to preserve a substring.
 *
 * At either bound the button is marked `aria-disabled` rather than given the
 * `disabled` property. A button that goes inert while it holds the keyboard
 * focus hands that focus to the document body, and paging to the last page is
 * precisely the moment Next becomes unavailable under someone's finger — they
 * would lose their place at the bottom of a list and have to tab in from the
 * top of the page. Marked this way it stays focusable, stays in the tab order
 * and is still announced as unavailable; refusing the press is then the
 * handlers' own job, and they do it on their first line.
 */
@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <nav
      class="flex items-center justify-between gap-4"
      [attr.aria-label]="'admin.common.pagination' | translate"
    >
      <button
        type="button"
        data-testid="pagination-previous"
        class="rounded-md border border-border px-3 py-2 text-sm text-text aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
        [attr.aria-disabled]="page() <= 0 ? true : null"
        [title]="'admin.common.previousPage' | translate"
        (click)="goToPrevious()"
      >
        {{ 'admin.common.previous' | translate }}
      </button>

      <span class="text-sm text-text-muted">
        {{
          'admin.common.pageOfTotal'
            | translate: { current: displayPage(), total: displayTotalPages() }
        }}
      </span>

      <button
        type="button"
        data-testid="pagination-next"
        class="rounded-md border border-border px-3 py-2 text-sm text-text aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
        [attr.aria-disabled]="isOnLastPage() ? true : null"
        [title]="'admin.common.nextPage' | translate"
        (click)="goToNext()"
      >
        {{ 'admin.common.next' | translate }}
      </button>
    </nav>
  `,
})
export class Pagination {
  /** Zero-based, as every admin list query and page envelope uses. */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();

  readonly pageChange = output<number>();

  protected readonly displayPage = computed(() => this.page() + 1);
  protected readonly displayTotalPages = computed(() => Math.max(this.totalPages(), 1));
  protected readonly isOnLastPage = computed(() => this.page() + 1 >= this.displayTotalPages());

  /**
   * Refuses the press the button's `aria-disabled` announces as unavailable,
   * stated as the first thing this method does rather than inferred from the
   * arithmetic that follows. The bound is checked here and nowhere else,
   * which matters because the button is deliberately not `disabled` — see
   * the note on the template above — and therefore still fires this handler
   * on the first page.
   */
  protected goToPrevious(): void {
    if (this.page() <= 0) {
      return;
    }
    this.pageChange.emit(this.page() - 1);
  }

  /** As `goToPrevious`, at the other bound. */
  protected goToNext(): void {
    if (this.isOnLastPage()) {
      return;
    }
    this.pageChange.emit(this.page() + 1);
  }
}
