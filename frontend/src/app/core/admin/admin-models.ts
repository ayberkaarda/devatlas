/**
 * View models for the admin/editor surface (blog authoring, the review queue
 * and whitelist source administration).
 *
 * The same split as `core/platform/models.ts` applies: these are the shapes
 * every admin screen is written against, and `AdminApiClient` is the only
 * place that knows the wire form underneath them. There is no local-store
 * counterpart here — the admin screens are session-scoped over HTTP on both
 * platforms (`docs/protocol/platform-service.md` §9), so one shape is enough.
 */
import type { Page } from '../platform/models';

export type BlogStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED';
export type BlogSource = 'MANUAL' | 'AUTO';
export type VerifyStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export type PipelineStep =
  | 'FETCH'
  | 'NORMALIZE'
  | 'VERIFY'
  | 'DRAFT'
  | 'SUBMIT'
  | 'APPROVE'
  | 'REJECT'
  | 'PUBLISH'
  | 'UNPUBLISH';

/**
 * The five lifecycle transitions the server exposes as endpoints
 * (`POST .../{action}`). Named apart from the blog feature's `LifecycleAction`
 * (`features/admin/blog/blog-lifecycle.ts`) on purpose: that one is the
 * six-member set of actions a user can see on a post, including `'delete'`,
 * and `availableActions` returns it. This one is the wire-level set
 * `transitionBlogPost` can actually send — `'delete'` is never a member here,
 * because deletion is a different HTTP verb on a different path
 * (`deleteBlogPost`), not a status transition. Two different questions, two
 * different names, so one wrong import cannot quietly answer the other.
 */
export type TransitionAction = 'submit' | 'approve' | 'reject' | 'publish' | 'unpublish';

/** Every write endpoint on this client echoes the row's optimistic-lock version. */
export interface TransitionInput {
  readonly expectedStatus: BlogStatus;
  /** Required by the server for `reject` and `unpublish`; ignored elsewhere. */
  readonly reason?: string;
}

export interface AdminBlogListQuery {
  readonly status?: BlogStatus;
  readonly source?: BlogSource;
  readonly q?: string;
  readonly page?: number;
  readonly size?: number;
  /** Each entry is one `field,asc|desc` token; the server accepts repeats. */
  readonly sort?: readonly string[];
}

/**
 * The review queue lists `PENDING_REVIEW` posts only, so it has no `status`
 * filter, and it deliberately has no `sort` field: the server's sort
 * whitelist for this endpoint is empty, and any value here — even a field
 * name valid on the blog list — is rejected with `400 INVALID_SORT_FIELD`.
 */
export interface ReviewQueueQuery {
  readonly source?: BlogSource;
  readonly page?: number;
  readonly size?: number;
}

export interface SourceListQuery {
  readonly page?: number;
  readonly size?: number;
  readonly sort?: readonly string[];
}

export interface PageQueryInput {
  readonly page?: number;
  readonly size?: number;
}

export interface CreateBlogPostInput {
  readonly slug: string;
  readonly title: string;
  readonly bodyMarkdown: string;
  /** Manual posts only; ignored server-side for everything else. */
  readonly sourceUrl?: string | null;
}

/** Every field but `version` is "leave unchanged" when omitted. */
export interface UpdateBlogPostInput {
  readonly slug?: string;
  readonly title?: string;
  readonly bodyMarkdown?: string;
  readonly sourceUrl?: string | null;
  readonly version: number;
}

/**
 * A blog post as authoring sees it: any status, canonical English columns.
 *
 * The server serves this exact shape — `AdminBlogPostResponse` — for both the
 * list endpoint and the single-post read, including the full
 * `bodyMarkdown`. There is no lighter list projection for admin authoring
 * the way the public read API trims a post down to an `excerpt`, so
 * `AdminBlogPostSummary` below is not a smaller type; it is the same fields,
 * named the way the row-list screens use them.
 */
export interface AdminBlogPost {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly bodyMarkdown: string;
  readonly status: BlogStatus;
  readonly source: BlogSource;
  readonly sourceUrl: string | null;
  readonly sourceUpdateId: string | null;
  readonly publishedAt: string | null;
  readonly createdBy: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

/** See the note on {@link AdminBlogPost}: identical fields, list-row usage. */
export type AdminBlogPostSummary = AdminBlogPost;

export interface AuditLogItem {
  readonly id: string;
  readonly step: PipelineStep;
  /** `null` for a machine step, non-null for every human decision. */
  readonly actorUserId: string | null;
  readonly fromStatus: BlogStatus | null;
  readonly toStatus: BlogStatus | null;
  readonly reason: string | null;
  readonly occurredAt: string;
}

export interface VerifyCheck {
  readonly check: string;
  readonly passed: boolean;
  readonly detail: string | null;
}

/** The whitelist source fields the review screen needs alongside a source update. */
export interface WhitelistSourceRef {
  readonly id: string;
  readonly name: string;
  readonly feedUrl: string;
}

export interface SourceUpdateDetail {
  readonly id: string;
  readonly whitelistSource: WhitelistSourceRef;
  readonly versionString: string;
  readonly contentHash: string;
  readonly fetchedAt: string;
  readonly verifyStatus: VerifyStatus;
  /** Ordered as executed; the first failing entry is why a rejected update never became a draft. */
  readonly verifyChecks: readonly VerifyCheck[];
  readonly rawContent: string;
}

/**
 * Everything the side-by-side review screen needs in one call.
 *
 * `sourceUpdate` is `null` for a post that reached the review queue by an
 * ordinary human "submit" — it has no pipeline provenance to show, and that
 * absence is a normal state, not an error.
 */
export interface ReviewDetail {
  readonly post: AdminBlogPost;
  readonly sourceUpdate: SourceUpdateDetail | null;
}

export interface WhitelistSource {
  readonly id: string;
  readonly name: string;
  readonly feedUrl: string;
  readonly verifyUrlPattern: string;
  readonly enabled: boolean;
  readonly lastFetchedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface CreateSourceInput {
  readonly name: string;
  readonly feedUrl: string;
  readonly verifyUrlPattern: string;
  readonly enabled?: boolean;
}

/** Every field but `version` is "leave unchanged" when omitted. */
export interface UpdateSourceInput {
  readonly name?: string;
  readonly feedUrl?: string;
  readonly verifyUrlPattern?: string;
  readonly enabled?: boolean;
  readonly version: number;
}

export interface SourceFetchRejection {
  readonly versionString: string;
  readonly failedCheck: string;
  readonly detail: string | null;
}

export interface SourceFetchResult {
  readonly whitelistSourceId: string;
  readonly fetched: number;
  readonly created: number;
  readonly duplicates: number;
  readonly rejected: number;
  readonly createdSourceUpdateIds: readonly string[];
  readonly rejections: readonly SourceFetchRejection[];
  readonly durationMs: number;
}

export type { Page };
