import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import type {
  AdminBlogPostSummary,
  BlogSource,
  BlogStatus,
} from '../../../core/admin/admin-models';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import { errorKey } from '../../../core/platform/error-key';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { Pagination } from '../../../shared/pagination';
import { StatusBadge } from '../../../shared/status-badge';

/** How many rows one page of the list shows, matching the audit log's own page size. */
const PAGE_SIZE = 20;

/** How long typing pauses before a `q` change is sent, so every keystroke does not fire a request. */
const SEARCH_DEBOUNCE_MS = 300;

const STATUS_OPTIONS: readonly BlogStatus[] = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED'];
const SOURCE_OPTIONS: readonly BlogSource[] = ['MANUAL', 'AUTO'];

/** One `field,direction` token per sortable field the server accepts (§5.5.2, #23). */
const SORT_OPTIONS: readonly string[] = [
  'created_at,desc',
  'created_at,asc',
  'updated_at,desc',
  'updated_at,asc',
  'published_at,desc',
  'published_at,asc',
  'title,asc',
  'title,desc',
];

/**
 * The blog authoring list: filter by status and source, free-text search, one
 * sort field, and pagination — all of it living in the URL's query params
 * (via `withComponentInputBinding`, `app.config.ts`) rather than in
 * component state alone, so the browser's back button returns to the exact
 * filtered view a user left, not an empty default list.
 */
@Component({
  selector: 'app-blog-post-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, StatusBadge, DateTimePipe, Pagination],
  templateUrl: './blog-post-list.page.html',
})
export class BlogPostListPage {
  private readonly api = inject(AdminApiClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * A query parameter missing from the URL still runs the router's
   * component-input binding, which calls the setter with `undefined` rather
   * than leaving `input()`'s own default in place. Visiting `/admin/blog`
   * with no query string at all would otherwise leave every field below
   * holding `undefined`: the search box would render the literal text
   * "undefined", and `q.trim()` in `load()` would throw before a request is
   * ever sent. Each transform folds a missing parameter back onto the value
   * the field already uses to mean "no filter" (or, for `page`, the first
   * page).
   */

  /** `''` means "every status"; anything else is a literal `BlogStatus`. */
  readonly status = input('', { transform: (value: string | undefined) => value ?? '' });
  /** `''` means "every source". */
  readonly source = input('', { transform: (value: string | undefined) => value ?? '' });
  readonly q = input('', { transform: (value: string | undefined) => value ?? '' });
  readonly sort = input('created_at,desc', {
    transform: (value: string | undefined) => value ?? 'created_at,desc',
  });
  readonly page = input(0, {
    transform: (value: string | undefined) => numberAttribute(value, 0),
  });

  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly sourceOptions = SOURCE_OPTIONS;
  protected readonly sortOptions = SORT_OPTIONS;

  /**
   * The search box's own draft value. It tracks the `q` query param so a
   * back-navigation or a shared link shows the right text, but it also holds
   * every keystroke between them, which the query param — updated only after
   * the debounce settles — does not.
   */
  protected readonly qDraft = signal('');

  protected readonly posts = signal<readonly AdminBlogPostSummary[]>([]);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      this.qDraft.set(this.q());
    });

    effect(() => {
      const status = this.status();
      const source = this.source();
      const q = this.q();
      const sort = this.sort();
      const page = this.page();
      void this.load(status, source, q, sort, page);
    });

    this.destroyRef.onDestroy(() => {
      if (this.searchTimer !== null) {
        clearTimeout(this.searchTimer);
      }
    });
  }

  protected onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.navigate({ status: value === '' ? null : value, page: null });
  }

  protected onSourceChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.navigate({ source: value === '' ? null : value, page: null });
  }

  protected onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.navigate({ sort: value, page: null });
  }

  protected onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.qDraft.set(value);
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.navigate({ q: value.trim() === '' ? null : value, page: null });
    }, SEARCH_DEBOUNCE_MS);
  }

  protected onPageChange(page: number): void {
    this.navigate({ page: page === 0 ? null : String(page) });
  }

  protected retry(): void {
    void this.load(this.status(), this.source(), this.q(), this.sort(), this.page());
  }

  private async load(
    status: string,
    source: string,
    q: string,
    sort: string,
    page: number,
  ): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const result = await this.api.listBlogPosts({
        status: status === '' ? undefined : (status as BlogStatus),
        source: source === '' ? undefined : (source as BlogSource),
        q: q.trim() === '' ? undefined : q.trim(),
        sort: sort === '' ? undefined : [sort],
        page,
        size: PAGE_SIZE,
      });
      this.posts.set(result.items);
      this.totalPages.set(result.totalPages);
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }

  private navigate(patch: Readonly<Record<string, string | null>>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: patch,
      queryParamsHandling: 'merge',
    });
  }
}
