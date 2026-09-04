import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

import { API_BASE_URL, REQUEST_TIMEOUT_MS, toPlatformError } from './api';
import type { BlogListQuery, BlogPost, BlogPostSummary, Page } from './models';
import {
  translationOf,
  type WireBlogPost,
  type WireBlogPostSummary,
  type WirePage,
} from './rest-wire';

/**
 * Blog reads, shared verbatim by both platform implementations.
 *
 * Blog posts are not replica content: they are in no manifest, they are never
 * downloaded, and the local store has no table for them. On the desktop the
 * blog is the one screen that needs a connection, and the read endpoint is
 * anonymous, so that costs nothing in session terms.
 *
 * Neither implementation adds anything to this client. It stays behind the
 * platform abstraction anyway, because that is what keeps the decision
 * reversible: if blog posts ever become downloadable, only the desktop
 * implementation changes and no component notices.
 */
@Injectable({ providedIn: 'root' })
export class BlogApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async list(query: BlogListQuery): Promise<Page<BlogPostSummary>> {
    let params = new HttpParams();
    if (query.page !== undefined) {
      params = params.set('page', query.page);
    }
    if (query.size !== undefined) {
      params = params.set('size', query.size);
    }
    if (query.source !== undefined) {
      params = params.set('source', query.source);
    }

    try {
      const page = await firstValueFrom(
        this.http
          .get<WirePage<WireBlogPostSummary>>(`${this.baseUrl}/blog/posts`, { params })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return {
        items: page.items.map((item) => ({
          id: item.id,
          slug: item.slug,
          title: item.title,
          excerpt: item.excerpt,
          source: item.source,
          sourceUrl: item.source_url,
          publishedAt: item.published_at,
          translation: translationOf(item),
        })),
        page: page.page,
        size: page.size,
        totalElements: page.total_elements,
        totalPages: page.total_pages,
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async get(slug: string): Promise<BlogPost> {
    try {
      const post = await firstValueFrom(
        this.http
          .get<WireBlogPost>(`${this.baseUrl}/blog/posts/${encodeURIComponent(slug)}`)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return {
        id: post.id,
        slug: post.slug,
        title: post.title,
        bodyMarkdown: post.body_markdown,
        source: post.source,
        sourceUrl: post.source_url,
        publishedAt: post.published_at,
        translation: translationOf(post),
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}
