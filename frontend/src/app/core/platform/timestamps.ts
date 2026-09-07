/**
 * The timestamp format the API defines: UTC, always three fractional digits,
 * a literal `Z`.
 *
 * It is normative rather than illustrative — the sync boundary is compared as
 * a string, and lexical order is only chronological order while every value
 * has the same shape — so it is produced here rather than left to whatever a
 * default serializer happens to emit.
 */
export function isoNow(): string {
  return new Date().toISOString().replace(/\.(\d{3})\d*Z$/, '.$1Z');
}
