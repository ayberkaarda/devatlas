import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { SourceFetchResult, WhitelistSource } from '../../../core/admin/admin-models';
import { errorKey } from '../../../core/platform/error-key';
import { PlatformError } from '../../../core/platform/errors';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { Pagination } from '../../../shared/pagination';
import { FetchResult } from './fetch-result';

const PAGE_SIZE = 20;

/** One `field,direction` token per sortable field the server accepts (§7.9). */
const SORT_OPTIONS: readonly string[] = [
  'name,asc',
  'name,desc',
  'created_at,desc',
  'created_at,asc',
  'updated_at,desc',
  'updated_at,asc',
  'last_fetched_at,desc',
  'last_fetched_at,asc',
];

/** The most recently triggered manual fetch, kept around to render below the table once it settles. */
interface LastFetch {
  readonly sourceId: string;
  readonly sourceName: string;
  readonly result: SourceFetchResult;
}

/**
 * `ADMIN`-only whitelist source administration: list, sort, page, enable or
 * disable in place, delete with an inline confirmation, and trigger a
 * manual fetch.
 *
 * Only one row action runs at a time (`busyId`) — a manual fetch already
 * runs the real ingest cycle synchronously and can take close to
 * `MANUAL_FETCH_TIMEOUT_MS`, and letting a second action start on another
 * row while one is in flight would have no way to show two independent
 * outcomes clearly on one small table.
 */
@Component({
  selector: 'app-whitelist-source-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, Pagination, FetchResult],
  templateUrl: './whitelist-source-list.page.html',
})
export class WhitelistSourceListPage {
  private readonly api = inject(AdminApiClient);

  protected readonly sortOptions = SORT_OPTIONS;

  protected readonly sort = signal('name,asc');
  protected readonly page = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly sources = signal<readonly WhitelistSource[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  /** The row currently running a toggle, delete, or fetch — never more than one at once. */
  protected readonly busyId = signal<string | null>(null);
  /** A failure surfaced under a specific row rather than the whole page. */
  protected readonly rowErrorKey = signal<{ readonly id: string; readonly key: string } | null>(
    null,
  );
  /** Set on a `409 PARENT_NOT_EMPTY` delete failure, to show the disable-instead hint under that row. */
  protected readonly parentNotEmptyId = signal<string | null>(null);
  /** The row asking "are you sure?" before its delete request is actually sent. */
  protected readonly confirmDeleteId = signal<string | null>(null);

  protected readonly lastFetch = signal<LastFetch | null>(null);

  constructor() {
    void this.load();
  }

  protected onSortChange(event: Event): void {
    this.sort.set((event.target as HTMLSelectElement).value);
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

  protected async toggleEnabled(source: WhitelistSource): Promise<void> {
    this.busyId.set(source.id);
    this.rowErrorKey.set(null);
    try {
      const updated = await this.api.updateSource(source.id, {
        enabled: !source.enabled,
        version: source.version,
      });
      this.replaceSource(updated);
    } catch (error) {
      this.rowErrorKey.set({ id: source.id, key: errorKey(error) });
    } finally {
      this.busyId.set(null);
    }
  }

  protected requestDelete(source: WhitelistSource): void {
    this.confirmDeleteId.set(source.id);
    this.rowErrorKey.set(null);
    this.parentNotEmptyId.set(null);
  }

  protected cancelDelete(): void {
    this.confirmDeleteId.set(null);
  }

  protected async confirmDelete(source: WhitelistSource): Promise<void> {
    this.busyId.set(source.id);
    this.rowErrorKey.set(null);
    try {
      await this.api.deleteSource(source.id);
      this.confirmDeleteId.set(null);
      this.sources.update((current) => current.filter((entry) => entry.id !== source.id));
    } catch (error) {
      if (error instanceof PlatformError && error.code === 'PARENT_NOT_EMPTY') {
        this.parentNotEmptyId.set(source.id);
      }
      this.confirmDeleteId.set(null);
      this.rowErrorKey.set({ id: source.id, key: errorKey(error) });
    } finally {
      this.busyId.set(null);
    }
  }

  protected async fetchNow(source: WhitelistSource): Promise<void> {
    this.busyId.set(source.id);
    this.rowErrorKey.set(null);
    this.lastFetch.set(null);
    try {
      const result = await this.api.fetchSourceNow(source.id);
      this.lastFetch.set({ sourceId: source.id, sourceName: source.name, result });
      try {
        this.replaceSource(await this.api.getSource(source.id));
      } catch {
        // The fetch itself already succeeded and its result is shown; a
        // failure re-reading the row afterwards (stale `lastFetchedAt`
        // display only) is not worth surfacing as an error of its own.
      }
    } catch (error) {
      this.rowErrorKey.set({ id: source.id, key: errorKey(error) });
    } finally {
      this.busyId.set(null);
    }
  }

  private replaceSource(updated: WhitelistSource): void {
    this.sources.update((current) =>
      current.map((entry) => (entry.id === updated.id ? updated : entry)),
    );
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const result = await this.api.listSources({
        sort: [this.sort()],
        page: this.page(),
        size: PAGE_SIZE,
      });
      this.sources.set(result.items);
      this.totalPages.set(result.totalPages);
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
