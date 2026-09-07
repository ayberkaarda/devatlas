import { Injectable } from '@angular/core';

import type { BlogPostSummary, Page } from '../../core/platform/models';

/** One list page as it was last read, with the moment it was read. */
export interface CachedBlogList {
  readonly list: Page<BlogPostSummary>;
  readonly loadedAt: string;
}

/**
 * The last successful blog list, held for as long as the application is
 * running.
 *
 * The blog is read live: it is in no manifest, it is never downloaded, and
 * there is no local table for it. That makes an unreachable server an
 * ordinary condition on this screen and nowhere else, and it makes the list
 * a reader already saw worth showing again — with a band saying when it was
 * loaded — instead of an empty panel that throws away what is already in
 * hand.
 *
 * It lives in memory and is deliberately never written to durable storage.
 * Persisting it would be the first step toward the blog becoming replicated
 * content by accident: a stored list outlives the session, then wants
 * invalidating, then wants the bodies too, and by then the screen is a badly
 * specified replica of a feature whose whole value is being current.
 *
 * Keyed by page index because paging is the only thing that varies the
 * request, so returning to page two offline shows page two rather than
 * whatever page happened to be read last.
 */
@Injectable({ providedIn: 'root' })
export class BlogListCache {
  private readonly pages = new Map<number, CachedBlogList>();

  /** What was last read for this page, or `null` if it never was. */
  read(page: number): CachedBlogList | null {
    return this.pages.get(page) ?? null;
  }

  /**
   * Keyed by the page that was asked for rather than by the index the
   * envelope carries back, so a later read for the same page finds it
   * whatever the server echoed.
   */
  store(
    page: number,
    list: Page<BlogPostSummary>,
    loadedAt: string = new Date().toISOString(),
  ): void {
    this.pages.set(page, { list, loadedAt });
  }
}
