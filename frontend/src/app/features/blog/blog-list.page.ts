import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { ConnectivityService } from '../../core/net/connectivity.service';
import { errorKey } from '../../core/platform/error-key';
import { PlatformError } from '../../core/platform/errors';
import type { BlogPostSummary } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { FallbackBadge } from '../../shared/fallback-badge';
import { Pagination } from '../../shared/pagination';
import { BlogListCache } from './blog-list-cache';
import { BlogOffline } from './blog-offline';
import { sourceHost } from './blog-source';

/** Posts per page. Smaller than an admin table's page because each row carries an excerpt. */
const PAGE_SIZE = 10;

/**
 * Folds a `page` query parameter onto a page index.
 *
 * The router calls an input's transform with `undefined` when the parameter
 * is absent from the URL, which is the ordinary case — `/blog` with no query
 * string at all — rather than an edge case, and it happens instead of, not
 * alongside, the input's own default. Anything that is not a whole page index
 * (a negative number, a fraction, a word someone typed) means the first page:
 * a request the server would reject with `INVALID_PARAMETER` is not worth
 * making, and an error where a reader expected a list explains nothing.
 */
function pageParam(value: string | undefined): number {
  const parsed = numberAttribute(value, 0);
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : 0;
}

/**
 * A failure that produced no response at all: a connection that never
 * opened, or one that hung past the request timeout. Both arrive as the same
 * code, and they are the only two conditions that mean the network rather
 * than the server.
 */
function isTransportFailure(error: unknown): boolean {
  return error instanceof PlatformError && error.code === 'NETWORK_UNAVAILABLE';
}

/** One row: the post, plus the host of its source link when it has one. */
interface BlogRow {
  readonly post: BlogPostSummary;
  readonly host: string | null;
}

/**
 * The public blog list: published posts, newest first, paged.
 *
 * The page index lives in the URL rather than in component state, so a
 * shared link and the browser's back button both land on the page they were
 * taken from. Everything else about the request is fixed here — the server
 * already sorts by publication date descending by default, and a reader has
 * no use for a second ordering of a feed.
 *
 * Reads go through the platform abstraction like every other screen's, and
 * this one cannot tell that both implementations answer it with the same
 * HTTP client. That is the point: if blog posts ever become downloadable,
 * one implementation changes and nothing here does.
 */
@Component({
  selector: 'app-blog-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, FallbackBadge, Pagination, BlogOffline],
  templateUrl: './blog-list.page.html',
})
export class BlogListPage {
  private readonly platform = inject(PlatformService);
  private readonly cache = inject(BlogListCache);
  private readonly connectivity = inject(ConnectivityService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly page = input(0, { transform: pageParam });

  protected readonly posts = signal<readonly BlogPostSummary[]>([]);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  /** Set when the server could not be reached; never set for a server that answered. */
  protected readonly offline = signal(false);

  /**
   * When the posts on screen were read from the server, or `null` when
   * nothing on screen came from the server at all.
   *
   * It is recorded for a successful read as well as for a cached one, because
   * a read succeeding does not keep it current: the connection can go away a
   * second later, and from then on the only honest thing the screen can say
   * about the list it is holding is when it was read.
   */
  protected readonly readAt = signal<string | null>(null);

  /**
   * The moment the posts on screen were read, given only while they are known
   * not to be current, and `null` while they are.
   *
   * The judgement is deliberately not "did my own last read fail". Content
   * read before the server became unreachable is exactly as out of date as
   * content a failed refresh fell back on, and a screen that dated only the
   * second one would let the first stand as a live fact — which is how an
   * empty page read minutes ago comes to say "there is nothing published"
   * about a server that is not answering at all. Any request in the
   * application discovering that the server cannot be reached is enough, and
   * this screen does not have to be the one that discovered it.
   */
  protected readonly staleSince = computed(() =>
    this.offline() || this.connectivity.offline() ? this.readAt() : null,
  );

  protected readonly rows = computed<readonly BlogRow[]>(() =>
    this.posts().map((post) => ({
      post,
      host: post.sourceUrl === null ? null : sourceHost(post.sourceUrl),
    })),
  );

  /**
   * Whether the server was last known to be unreachable, kept as a plain
   * field so that reading it does not subscribe anything to it. It exists to
   * tell a recovery apart from the first reading of a connection that has
   * been fine all along.
   */
  private serverWasUnreachable = false;

  constructor() {
    // `untracked` keeps the dependency exactly the page index. Without it the
    // effect also subscribes to every signal the request reads on its way out
    // through the interceptors -- the access token and the active locale among
    // them -- and a token being refreshed would silently reload the list.
    effect(() => {
      const page = this.page();
      untracked(() => void this.load(page));
    });

    // A screen that is only ever loaded when it is constructed cannot recover
    // from an outage without the reader leaving it and coming back. This is
    // what closes that: the moment anything in the application reaches the
    // server again, the list that was dated as stale is read afresh.
    effect(() => {
      const reachable = this.connectivity.online();
      const recovered = reachable && this.serverWasUnreachable;
      this.serverWasUnreachable = !reachable;
      if (recovered) {
        untracked(() => void this.load(this.page()));
      }
    });
  }

  protected onPageChange(page: number): void {
    // Zero is written as an absent parameter rather than as `page=0`, so the
    // first page has one URL instead of two that differ only in noise.
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page === 0 ? null : String(page) },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    void this.load(this.page());
  }

  private async load(page: number): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.offline.set(false);
    try {
      // The client underneath this call carries a request timeout, which is
      // what stops an unreachable server from leaving a spinner running: an
      // indeterminate spinner is exactly how being offline comes to look
      // like a hang.
      const result = await this.platform.listBlogPosts({ page, size: PAGE_SIZE });
      const loadedAt = new Date().toISOString();
      this.cache.store(page, result, loadedAt);
      this.posts.set(result.items);
      this.totalPages.set(result.totalPages);
      this.readAt.set(loadedAt);
    } catch (error) {
      if (isTransportFailure(error)) {
        this.offline.set(true);
        // Whatever this page held when it was last read is better than an
        // empty panel; the band above it says when that was, so nothing on
        // screen is passed off as current. An empty page that was read
        // successfully counts as something read: the timestamp is the only
        // thing that tells it apart from a server that is genuinely empty,
        // so it is recorded whether or not the page had rows in it.
        const cached = this.cache.read(page);
        this.posts.set(cached?.list.items ?? []);
        this.totalPages.set(cached?.list.totalPages ?? 0);
        this.readAt.set(cached?.loadedAt ?? null);
      } else {
        this.posts.set([]);
        this.totalPages.set(0);
        this.readAt.set(null);
        this.failure.set(errorKey(error));
      }
    } finally {
      this.loading.set(false);
    }
  }
}
