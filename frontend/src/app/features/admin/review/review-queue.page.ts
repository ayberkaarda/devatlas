import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { AdminBlogPostSummary, BlogSource } from '../../../core/admin/admin-models';
import { errorKey } from '../../../core/platform/error-key';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { Pagination } from '../../../shared/pagination';

const PAGE_SIZE = 20;

/**
 * `PENDING_REVIEW` posts waiting for an editorial decision (§5.7).
 *
 * There is no `sort` control here on purpose: `ReviewQueueQuery` has no
 * `sort` field to send (`AdminApiClient.listReviewQueue`), because the
 * server's sort whitelist for this endpoint is empty and any value would be
 * rejected with `400 INVALID_SORT_FIELD`. The list is always oldest-first,
 * which is also the fair order to work through a queue.
 */
@Component({
  selector: 'app-review-queue-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, Pagination],
  templateUrl: './review-queue.page.html',
})
export class ReviewQueuePage {
  private readonly api = inject(AdminApiClient);

  protected readonly source = signal<BlogSource | undefined>(undefined);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly items = signal<readonly AdminBlogPostSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected onSourceChange(value: string): void {
    this.source.set(value === '' ? undefined : (value as BlogSource));
    this.page.set(0);
    void this.load();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    void this.load();
  }

  protected retry(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const result = await this.api.listReviewQueue({
        source: this.source(),
        page: this.page(),
        size: PAGE_SIZE,
      });
      this.items.set(result.items);
      this.totalPages.set(result.totalPages);
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
