/**
 * The admin REST payloads exactly as they arrive on and go out over the wire.
 *
 * `snake_case`, matching `dev.bytelore.server.content.admin.dto` and
 * `docs/protocol/rest-api.md` §5.5–§5.7 — field names below are read off the
 * DTO source, not guessed from the prose. Declared separately from the view
 * models in `admin-models.ts` for the same reason `rest-wire.ts` is separate
 * from `models.ts`: the snake_case-to-camelCase mapping is a visible, typed
 * step in `AdminApiClient`, not an assumption spread across call sites.
 */
import type { BlogSource, BlogStatus, PipelineStep, VerifyStatus } from './admin-models';

// ---- Blog ------------------------------------------------------------------

/** `AdminBlogPostResponse` — used for both the list row and the single-post read. */
export interface WireAdminBlogPost {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly body_markdown: string;
  readonly status: BlogStatus;
  readonly source: BlogSource;
  readonly source_url: string | null;
  readonly source_update_id: string | null;
  readonly published_at: string | null;
  readonly created_by: string | null;
  readonly created_at: string;
  readonly updated_at: string;
  readonly version: number;
}

export interface WireCreateBlogPostRequest {
  readonly slug: string;
  readonly title: string;
  readonly body_markdown: string;
  readonly source_url?: string | null;
}

export interface WireUpdateBlogPostRequest {
  readonly slug?: string;
  readonly title?: string;
  readonly body_markdown?: string;
  readonly source_url?: string | null;
  readonly version: number;
}

/** Shared by every transition endpoint (`submit`, `approve`, `reject`, `publish`, `unpublish`). */
export interface WireBlogTransitionRequest {
  readonly expected_status: BlogStatus;
  readonly reason?: string;
}

export interface WireAuditLogItem {
  readonly id: string;
  readonly step: PipelineStep;
  readonly actor_user_id: string | null;
  readonly from_status: BlogStatus | null;
  readonly to_status: BlogStatus | null;
  readonly reason: string | null;
  readonly occurred_at: string;
}

// ---- Review queue and source updates ---------------------------------------

/** `WhitelistSourceSummary` — the trimmed source reference nested in a source update. */
export interface WireWhitelistSourceSummary {
  readonly id: string;
  readonly name: string;
  readonly feed_url: string;
}

/** One entry of `VerifyCheckRecord`, in execution order. */
export interface WireVerifyCheck {
  readonly check: string;
  readonly passed: boolean;
  readonly detail: string | null;
}

export interface WireSourceUpdateDetail {
  readonly id: string;
  readonly whitelist_source: WireWhitelistSourceSummary;
  readonly version_string: string;
  readonly content_hash: string;
  readonly fetched_at: string;
  readonly verify_status: VerifyStatus;
  readonly verify_checks: readonly WireVerifyCheck[];
  readonly raw_content: string;
}

/**
 * `ReviewQueueDetailResponse`. `source_update` is `null` for a manually
 * written post that reached the queue through an ordinary submit.
 */
export interface WireReviewQueueDetail {
  readonly post: WireAdminBlogPost;
  readonly source_update: WireSourceUpdateDetail | null;
}

// ---- Whitelist sources ------------------------------------------------------

export interface WireWhitelistSource {
  readonly id: string;
  readonly name: string;
  readonly feed_url: string;
  readonly verify_url_pattern: string;
  readonly enabled: boolean;
  readonly last_fetched_at: string | null;
  readonly created_at: string;
  readonly updated_at: string;
  readonly version: number;
}

export interface WireCreateWhitelistSourceRequest {
  readonly name: string;
  readonly feed_url: string;
  readonly verify_url_pattern: string;
  readonly enabled?: boolean;
}

export interface WireUpdateWhitelistSourceRequest {
  readonly name?: string;
  readonly feed_url?: string;
  readonly verify_url_pattern?: string;
  readonly enabled?: boolean;
  readonly version: number;
}

export interface WireRejectionItem {
  readonly version_string: string;
  readonly failed_check: string;
  readonly detail: string | null;
}

export interface WireSourceFetchResult {
  readonly whitelist_source_id: string;
  readonly fetched: number;
  readonly created: number;
  readonly duplicates: number;
  readonly rejected: number;
  readonly created_source_update_ids: readonly string[];
  readonly rejections: readonly WireRejectionItem[];
  readonly duration_ms: number;
}
