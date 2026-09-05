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
const TRANSLATED_CODES: readonly string[] = [
  'NETWORK_UNAVAILABLE',
  'TRACK_NOT_FOUND',
  'LESSON_NOT_FOUND',
  'MIND_MAP_NOT_FOUND',
  'ENTITY_NOT_IN_LIBRARY',
  'UNSUPPORTED_ON_WEB',
  'INTERNAL_ERROR',
  'STORE_UNAVAILABLE',
  'ALREADY_QUEUED',
  'NOTHING_TO_DO',
  'INVALID_ARGUMENT',
  'UNEXPECTED_RESPONSE',
  'RATE_LIMITED',
  'CONTENT_VERSION_SUPERSEDED',
  'CONTENT_CHANGED_DURING_RESUME',
  'DIGEST_MISMATCH',
  'INSUFFICIENT_STORAGE',
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
