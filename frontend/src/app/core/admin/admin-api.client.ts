import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

import { API_BASE_URL, REQUEST_TIMEOUT_MS, toPlatformError } from '../platform/api';
import type { Page } from '../platform/models';
import type { WirePage } from '../platform/rest-wire';
import type {
  AdminBlogListQuery,
  AdminBlogPost,
  AdminBlogPostSummary,
  AuditLogItem,
  CreateBlogPostInput,
  CreateSourceInput,
  PageQueryInput,
  ReviewDetail,
  ReviewQueueQuery,
  SourceFetchResult,
  SourceListQuery,
  SourceUpdateDetail,
  TransitionAction,
  TransitionInput,
  UpdateBlogPostInput,
  UpdateSourceInput,
  WhitelistSource,
} from './admin-models';
import type {
  WireAdminBlogPost,
  WireAuditLogItem,
  WireBlogTransitionRequest,
  WireCreateBlogPostRequest,
  WireCreateWhitelistSourceRequest,
  WireReviewQueueDetail,
  WireSourceFetchResult,
  WireSourceUpdateDetail,
  WireUpdateBlogPostRequest,
  WireUpdateWhitelistSourceRequest,
  WireVerifyCheck,
  WireWhitelistSource,
  WireWhitelistSourceSummary,
} from './admin-wire';

/**
 * A manual fetch runs the ingest cycle for one source synchronously — it
 * calls the same code the scheduler calls and returns when the cycle
 * completes, which depends on real feed and verify-request latency, not on
 * this application. The general request timeout would drop that call as
 * failed while the server is still doing legitimate work, so this endpoint
 * gets its own, longer allowance instead of sharing `REQUEST_TIMEOUT_MS`.
 */
export const MANUAL_FETCH_TIMEOUT_MS = 60_000;

/**
 * Authoring, review and whitelist-source administration, over HTTP on both
 * platforms.
 *
 * This client is deliberately outside `PlatformService`: the admin screens
 * are a session-scoped surface with one implementation, not one behaviour
 * that differs between the web build and the desktop replica, so there is
 * nothing here for a second implementation to provide. Wiring it into the
 * platform abstraction would only give a component a way to ask "which
 * platform is this" through a door built for a question it should never
 * need to ask.
 *
 * Follows the same shape as `BlogApiClient`: `HttpClient` + `HttpParams`,
 * `firstValueFrom`, a timeout on every call, wire types kept apart from view
 * models, and `snake_case → camelCase` mapping done here rather than left to
 * each caller.
 */
@Injectable({ providedIn: 'root' })
export class AdminApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  // ---- Blog -----------------------------------------------------------------

  async listBlogPosts(query: AdminBlogListQuery): Promise<Page<AdminBlogPostSummary>> {
    const params = buildParams({
      status: query.status,
      source: query.source,
      q: query.q,
      page: query.page,
      size: query.size,
      sort: query.sort,
    });
    try {
      const page = await firstValueFrom(
        this.http
          .get<WirePage<WireAdminBlogPost>>(`${this.baseUrl}/admin/blog/posts`, { params })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toPage(page, toAdminBlogPost);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async getBlogPost(id: string): Promise<AdminBlogPost> {
    try {
      const post = await firstValueFrom(
        this.http
          .get<WireAdminBlogPost>(`${this.baseUrl}/admin/blog/posts/${encodeURIComponent(id)}`)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toAdminBlogPost(post);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async createBlogPost(input: CreateBlogPostInput): Promise<AdminBlogPost> {
    const body: WireCreateBlogPostRequest = {
      slug: input.slug,
      title: input.title,
      body_markdown: input.bodyMarkdown,
      source_url: input.sourceUrl ?? null,
    };
    try {
      const post = await firstValueFrom(
        this.http
          .post<WireAdminBlogPost>(`${this.baseUrl}/admin/blog/posts`, body)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toAdminBlogPost(post);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async updateBlogPost(id: string, input: UpdateBlogPostInput): Promise<AdminBlogPost> {
    const body: WireUpdateBlogPostRequest = {
      slug: input.slug,
      title: input.title,
      body_markdown: input.bodyMarkdown,
      source_url: input.sourceUrl,
      version: input.version,
    };
    try {
      const post = await firstValueFrom(
        this.http
          .patch<WireAdminBlogPost>(
            `${this.baseUrl}/admin/blog/posts/${encodeURIComponent(id)}`,
            body,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toAdminBlogPost(post);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async deleteBlogPost(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http
          .delete<void>(`${this.baseUrl}/admin/blog/posts/${encodeURIComponent(id)}`)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  /**
   * The one method behind five endpoints (`submit`, `approve`, `reject`,
   * `publish`, `unpublish`): `action` is the path segment, and the request
   * body every transition shares is identical, so five near-duplicate
   * methods would carry no information a switch on `action` does not already
   * carry.
   */
  async transitionBlogPost(
    id: string,
    action: TransitionAction,
    input: TransitionInput,
  ): Promise<AdminBlogPost> {
    const body: WireBlogTransitionRequest = {
      expected_status: input.expectedStatus,
      reason: input.reason,
    };
    try {
      const post = await firstValueFrom(
        this.http
          .post<WireAdminBlogPost>(
            `${this.baseUrl}/admin/blog/posts/${encodeURIComponent(id)}/${action}`,
            body,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toAdminBlogPost(post);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async listAuditLog(id: string, query: PageQueryInput): Promise<Page<AuditLogItem>> {
    const params = buildParams({ page: query.page, size: query.size });
    try {
      const page = await firstValueFrom(
        this.http
          .get<WirePage<WireAuditLogItem>>(
            `${this.baseUrl}/admin/blog/posts/${encodeURIComponent(id)}/audit-log`,
            { params },
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toPage(page, toAuditLogItem);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  // ---- Review -----------------------------------------------------------------

  /**
   * `sort` is never sent here, on purpose: the server's sort whitelist for
   * this endpoint is currently empty, so any value — even one valid on the
   * blog list — is rejected with `400 INVALID_SORT_FIELD`
   * (`AdminReviewQueueService.list`). `ReviewQueueQuery` has no `sort` field
   * to send in the first place.
   */
  async listReviewQueue(query: ReviewQueueQuery): Promise<Page<AdminBlogPostSummary>> {
    const params = buildParams({ source: query.source, page: query.page, size: query.size });
    try {
      const page = await firstValueFrom(
        this.http
          .get<WirePage<WireAdminBlogPost>>(`${this.baseUrl}/admin/review-queue`, { params })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toPage(page, toAdminBlogPost);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async getReviewDetail(postId: string): Promise<ReviewDetail> {
    try {
      const detail = await firstValueFrom(
        this.http
          .get<WireReviewQueueDetail>(
            `${this.baseUrl}/admin/review-queue/${encodeURIComponent(postId)}`,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toReviewDetail(detail);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async getSourceUpdate(id: string): Promise<SourceUpdateDetail> {
    try {
      const detail = await firstValueFrom(
        this.http
          .get<WireSourceUpdateDetail>(
            `${this.baseUrl}/admin/source-updates/${encodeURIComponent(id)}`,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toSourceUpdateDetail(detail);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  // ---- Whitelist sources --------------------------------------------------------

  async listSources(query: SourceListQuery): Promise<Page<WhitelistSource>> {
    const params = buildParams({ page: query.page, size: query.size, sort: query.sort });
    try {
      const page = await firstValueFrom(
        this.http
          .get<WirePage<WireWhitelistSource>>(`${this.baseUrl}/admin/whitelist-sources`, { params })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toPage(page, toWhitelistSource);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async getSource(id: string): Promise<WhitelistSource> {
    try {
      const source = await firstValueFrom(
        this.http
          .get<WireWhitelistSource>(
            `${this.baseUrl}/admin/whitelist-sources/${encodeURIComponent(id)}`,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toWhitelistSource(source);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async createSource(input: CreateSourceInput): Promise<WhitelistSource> {
    const body: WireCreateWhitelistSourceRequest = {
      name: input.name,
      feed_url: input.feedUrl,
      verify_url_pattern: input.verifyUrlPattern,
      enabled: input.enabled,
    };
    try {
      const source = await firstValueFrom(
        this.http
          .post<WireWhitelistSource>(`${this.baseUrl}/admin/whitelist-sources`, body)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toWhitelistSource(source);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async updateSource(id: string, input: UpdateSourceInput): Promise<WhitelistSource> {
    const body: WireUpdateWhitelistSourceRequest = {
      name: input.name,
      feed_url: input.feedUrl,
      verify_url_pattern: input.verifyUrlPattern,
      enabled: input.enabled,
      version: input.version,
    };
    try {
      const source = await firstValueFrom(
        this.http
          .patch<WireWhitelistSource>(
            `${this.baseUrl}/admin/whitelist-sources/${encodeURIComponent(id)}`,
            body,
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return toWhitelistSource(source);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async deleteSource(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http
          .delete<void>(`${this.baseUrl}/admin/whitelist-sources/${encodeURIComponent(id)}`)
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async fetchSourceNow(id: string): Promise<SourceFetchResult> {
    try {
      const result = await firstValueFrom(
        this.http
          .post<WireSourceFetchResult>(
            `${this.baseUrl}/admin/whitelist-sources/${encodeURIComponent(id)}/fetch`,
            {},
          )
          .pipe(timeout(MANUAL_FETCH_TIMEOUT_MS)),
      );
      return toSourceFetchResult(result);
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}

// ---- Wire → model mapping -----------------------------------------------------

function toAdminBlogPost(wire: WireAdminBlogPost): AdminBlogPost {
  return {
    id: wire.id,
    slug: wire.slug,
    title: wire.title,
    bodyMarkdown: wire.body_markdown,
    status: wire.status,
    source: wire.source,
    sourceUrl: wire.source_url,
    sourceUpdateId: wire.source_update_id,
    publishedAt: wire.published_at,
    createdBy: wire.created_by,
    createdAt: wire.created_at,
    updatedAt: wire.updated_at,
    version: wire.version,
  };
}

function toAuditLogItem(wire: WireAuditLogItem): AuditLogItem {
  return {
    id: wire.id,
    step: wire.step,
    actorUserId: wire.actor_user_id,
    fromStatus: wire.from_status,
    toStatus: wire.to_status,
    reason: wire.reason,
    occurredAt: wire.occurred_at,
  };
}

function toVerifyCheck(wire: WireVerifyCheck) {
  return { check: wire.check, passed: wire.passed, detail: wire.detail };
}

function toWhitelistSourceRef(wire: WireWhitelistSourceSummary) {
  return { id: wire.id, name: wire.name, feedUrl: wire.feed_url };
}

function toSourceUpdateDetail(wire: WireSourceUpdateDetail): SourceUpdateDetail {
  return {
    id: wire.id,
    whitelistSource: toWhitelistSourceRef(wire.whitelist_source),
    versionString: wire.version_string,
    contentHash: wire.content_hash,
    fetchedAt: wire.fetched_at,
    verifyStatus: wire.verify_status,
    verifyChecks: wire.verify_checks.map(toVerifyCheck),
    rawContent: wire.raw_content,
  };
}

function toReviewDetail(wire: WireReviewQueueDetail): ReviewDetail {
  return {
    post: toAdminBlogPost(wire.post),
    sourceUpdate: wire.source_update === null ? null : toSourceUpdateDetail(wire.source_update),
  };
}

function toWhitelistSource(wire: WireWhitelistSource): WhitelistSource {
  return {
    id: wire.id,
    name: wire.name,
    feedUrl: wire.feed_url,
    verifyUrlPattern: wire.verify_url_pattern,
    enabled: wire.enabled,
    lastFetchedAt: wire.last_fetched_at,
    createdAt: wire.created_at,
    updatedAt: wire.updated_at,
    version: wire.version,
  };
}

function toSourceFetchResult(wire: WireSourceFetchResult): SourceFetchResult {
  return {
    whitelistSourceId: wire.whitelist_source_id,
    fetched: wire.fetched,
    created: wire.created,
    duplicates: wire.duplicates,
    rejected: wire.rejected,
    createdSourceUpdateIds: wire.created_source_update_ids,
    rejections: wire.rejections.map((rejection) => ({
      versionString: rejection.version_string,
      failedCheck: rejection.failed_check,
      detail: rejection.detail,
    })),
    durationMs: wire.duration_ms,
  };
}

function toPage<W, M>(wire: WirePage<W>, mapItem: (item: W) => M): Page<M> {
  return {
    items: wire.items.map(mapItem),
    page: wire.page,
    size: wire.size,
    totalElements: wire.total_elements,
    totalPages: wire.total_pages,
  };
}

/**
 * Builds query params from a plain object, skipping `undefined` entries and
 * appending an array as one repeated param — the shape `sort` needs, since
 * the server accepts it more than once in one request.
 */
function buildParams(
  entries: Readonly<Record<string, string | number | boolean | readonly string[] | undefined>>,
): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(entries)) {
    if (value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      for (const entry of value) {
        params = params.append(key, entry);
      }
    } else {
      params = params.set(key, value as string | number | boolean);
    }
  }
  return params;
}
