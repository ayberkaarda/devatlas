import { PlatformError } from './errors';

/**
 * The error codes the interface has words for.
 *
 * A code with no entry here is rendered as the generic message rather than as
 * the code itself. Showing a raw code — or the server's English developer
 * message — tells the user nothing and looks like the application broke in a
 * way nobody anticipated, which is exactly the impression an unhandled case
 * should not give.
 *
 * The list covers both error envelopes this application ever sees: a thrown
 * `PlatformError` (from a rejected call) and the raw `code` string carried on
 * a `QueueEntry`/`DownloadProgress` for a queue entry that failed in place
 * rather than throwing. `codeKey` is the shared mapping; `errorKey` and
 * `queueErrorKey` are the two ways a caller has a code in hand.
 */
export const TRANSLATED_CODES: readonly string[] = [
  // Codes specific to this application (queue and platform-service failures
  // that never come from the HTTP API).
  'NETWORK_UNAVAILABLE',
  'ENTITY_NOT_IN_LIBRARY',
  'UNSUPPORTED_ON_WEB',
  'STORE_UNAVAILABLE',
  'ALREADY_QUEUED',
  'NOTHING_TO_DO',
  'INVALID_ARGUMENT',
  'UNEXPECTED_RESPONSE',
  'DIGEST_MISMATCH',
  'INSUFFICIENT_STORAGE',

  // The complete server error code catalogue (`ErrorCode` on the server).
  // Request / validation.
  'VALIDATION_FAILED',
  'MALFORMED_REQUEST',
  'INVALID_PARAMETER',
  'UNSUPPORTED_LOCALE',
  'UNSUPPORTED_LANGUAGE',
  'ENTITY_TYPE_UNSUPPORTED',
  'INVALID_SORT_FIELD',
  'PAGE_SIZE_EXCEEDED',
  'DUPLICATE_ITEM_IN_BATCH',
  'AMBIGUOUS_TOKEN_DELIVERY',
  'PAYLOAD_TOO_LARGE',
  'SYNC_BATCH_TOO_LARGE',
  'UNSUPPORTED_MEDIA_TYPE',
  'METHOD_NOT_ALLOWED',
  // Authentication.
  'AUTH_REQUIRED',
  'INVALID_CREDENTIALS',
  'ACCESS_TOKEN_EXPIRED',
  'ACCESS_TOKEN_INVALID',
  'REFRESH_TOKEN_INVALID',
  'REFRESH_TOKEN_EXPIRED',
  'REFRESH_TOKEN_REUSED',
  // Authorization.
  'FORBIDDEN_ROLE',
  'ACCOUNT_DISABLED',
  'AUTO_POST_NOT_EDITABLE',
  // Not found.
  'TRACK_NOT_FOUND',
  'MODULE_NOT_FOUND',
  'LESSON_NOT_FOUND',
  'CODE_EXAMPLE_NOT_FOUND',
  'MIND_MAP_NOT_FOUND',
  'BLOG_POST_NOT_FOUND',
  'TRANSLATION_NOT_FOUND',
  'SOURCE_UPDATE_NOT_FOUND',
  'WHITELIST_SOURCE_NOT_FOUND',
  'USER_NOT_FOUND',
  // Conflict.
  'EMAIL_ALREADY_REGISTERED',
  'SLUG_ALREADY_EXISTS',
  'VERSION_CONFLICT',
  'INVALID_STATE_TRANSITION',
  'AUTO_POST_APPROVAL_REQUIRED',
  'SOURCE_UPDATE_NOT_VERIFIED',
  'PARENT_NOT_EMPTY',
  'PUBLISHED_DELETE_BLOCKED',
  'ORDER_SET_INCOMPLETE',
  'PIPELINE_RUN_IN_PROGRESS',
  'CONTENT_VERSION_SUPERSEDED',
  // Semantic.
  'SANITIZED_CONTENT_EMPTY',
  'AUTO_POST_SOURCE_LINK_REQUIRED',
  'CANONICAL_LOCALE_NOT_ALLOWED',
  'MIND_MAP_INVALID',
  'INSECURE_SOURCE_URL',
  'INVALID_VERIFY_URL_PATTERN',
  'CURRENT_PASSWORD_INCORRECT',
  'CONTENT_PACKAGE_TOO_LARGE',
  // Precondition.
  'CONTENT_CHANGED_DURING_RESUME',
  // Throttling and server.
  'RATE_LIMITED',
  'INTERNAL_ERROR',
  'SERVICE_UNAVAILABLE',
];

/** Maps a raw code to the translation key that explains it. */
function codeKey(code: string): string {
  return `error.${TRANSLATED_CODES.includes(code) ? code : 'INTERNAL_ERROR'}`;
}

/** Maps a failure to the translation key that explains it. */
export function errorKey(error: unknown): string {
  const code = error instanceof PlatformError ? error.code : 'INTERNAL_ERROR';
  return codeKey(code);
}

/**
 * Maps a queue entry's `errorCode` to the same translation keys.
 *
 * A queue entry fails in place rather than by throwing — `errorCode` is a
 * plain string field, not a `PlatformError` — so this is the counterpart to
 * `errorKey` for that shape. `null` means the entry never failed, which is
 * not itself an error to explain.
 */
export function queueErrorKey(code: string | null): string | null {
  return code === null ? null : codeKey(code);
}
