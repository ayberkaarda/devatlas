import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { AuditLogItem } from '../../../core/admin/admin-models';
import { errorKey } from '../../../core/platform/error-key';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { Pagination } from '../../../shared/pagination';
import { StatusBadge } from '../../../shared/status-badge';

const PAGE_SIZE = 20;

/**
 * The append-only pipeline history for one post (§5.7).
 *
 * `actorUserId === null` marks a machine step (`FETCH`, `NORMALIZE`, `VERIFY`,
 * and the pipeline's own `DRAFT`) — everything else is a human decision, and
 * telling the two apart is this screen's whole reason to exist: it is where
 * "who approved this, and why" gets answered.
 */
@Component({
  selector: 'app-audit-log-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, StatusBadge, Pagination],
  templateUrl: './audit-log.page.html',
})
export class AuditLogPage {
  private readonly api = inject(AdminApiClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly id = input.required<string>();
  /**
   * A query parameter missing from the URL still runs the router's
   * component-input binding, which calls the setter with `undefined` rather
   * than leaving `input()`'s own default in place. `numberAttribute` on its
   * own turns that `undefined` into `NaN`, which is not a page number this
   * screen (or the server it queries) should ever see; the explicit fallback
   * keeps a bare `/admin/blog/:id/audit` on page zero.
   */
  readonly page = input(0, {
    transform: (value: string | undefined) => numberAttribute(value, 0),
  });

  protected readonly items = signal<readonly AuditLogItem[]>([]);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      const page = this.page();
      void this.load(id, page);
    });
  }

  protected isPipelineStep(item: AuditLogItem): boolean {
    return item.actorUserId === null;
  }

  protected onPageChange(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : String(page) },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    void this.load(this.id(), this.page());
  }

  private async load(id: string, page: number): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const result = await this.api.listAuditLog(id, { page, size: PAGE_SIZE });
      this.items.set(result.items);
      this.totalPages.set(result.totalPages);
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
