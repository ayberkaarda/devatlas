import { PlatformError } from './errors';

/**
 * The error codes the interface has words for.
 *
 * A code with no entry here is rendered as the generic message rather than as
 * the code itself. Showing a raw code — or the server's English developer
 * message — tells the user nothing and looks like the application broke in a
 * way nobody anticipated, which is exactly the impression an unhandled case
 * should not give.
 */
const TRANSLATED_CODES: readonly string[] = [
  'NETWORK_UNAVAILABLE',
  'TRACK_NOT_FOUND',
  'LESSON_NOT_FOUND',
  'ENTITY_NOT_IN_LIBRARY',
  'UNSUPPORTED_ON_WEB',
  'INTERNAL_ERROR',
];

/** Maps a failure to the translation key that explains it. */
export function errorKey(error: unknown): string {
  const code = error instanceof PlatformError ? error.code : 'INTERNAL_ERROR';
  return `error.${TRANSLATED_CODES.includes(code) ? code : 'INTERNAL_ERROR'}`;
}
