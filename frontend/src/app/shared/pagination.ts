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
        class="rounded-md border border-border px-3 py-1.5 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
        [disabled]="page() <= 0"
        [attr.aria-label]="'admin.common.previousPage' | translate"
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
        class="rounded-md border border-border px-3 py-1.5 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
        [disabled]="isOnLastPage()"
        [attr.aria-label]="'admin.common.nextPage' | translate"
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

  protected goToPrevious(): void {
    if (this.page() > 0) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  protected goToNext(): void {
    if (!this.isOnLastPage()) {
      this.pageChange.emit(this.page() + 1);
    }
  }
}
