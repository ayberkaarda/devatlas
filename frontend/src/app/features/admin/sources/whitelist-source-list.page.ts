import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
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

  private readonly deleteButtons = viewChildren<ElementRef<HTMLButtonElement>>('deleteButton');
  private readonly confirmDeleteButton =
    viewChild<ElementRef<HTMLButtonElement>>('confirmDeleteButton');
  private readonly fetchResultHeading = viewChild<ElementRef<HTMLElement>>('fetchResultHeading');

  /**
   * The row whose Delete button opened the confirmation, held so focus can go
   * back to it. Opening the confirmation replaces that button, and a destroyed
   * element takes the focus with it to the top of the document.
   */
  private readonly focusDeleteButtonFor = signal<string | null>(null);

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
    // The view references are signals, so each effect runs again once the @if
    // has actually put the element in the document, rather than at the moment
    // the state behind it changed.
    effect(() => {
      const button = this.confirmDeleteButton();
      if (this.confirmDeleteId() !== null && button) {
        button.nativeElement.focus();
      }
    });

    // Reading the query result is what makes this correct rather than merely
    // hopeful: it is a signal that updates once the row's own button is back
    // in the document, so the effect runs again at the moment there is
    // something to focus, instead of firing while the confirmation is still up.
    effect(() => {
      const id = this.focusDeleteButtonFor();
      const buttons = this.deleteButtons();
      if (id === null) {
        return;
      }
      const restored = buttons.find((button) => button.nativeElement.dataset['deleteFor'] === id);
      if (!restored) {
        return;
      }
      this.focusDeleteButtonFor.set(null);
      restored.nativeElement.focus();
    });

    // A manual fetch runs the whole ingest cycle on the server and can answer
    // a minute after the click, by which time the result has appeared below a
    // table the reader has probably scrolled past. Focus is the only thing
    // that reliably takes them to it.
    effect(() => {
      const heading = this.fetchResultHeading();
      if (this.lastFetch() !== null && heading) {
        heading.nativeElement.focus();
      }
    });

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
    this.focusDeleteButtonFor.set(this.confirmDeleteId());
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
