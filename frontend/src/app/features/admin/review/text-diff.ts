/**
 * Word-level diff between two text blobs, with no external dependency (I7).
 *
 * The review screen (§5.7) has one raw fetched source and one generated
 * markdown draft to compare, and a line-based diff is close to useless on
 * that pair: `raw_content` is plain prose with its own wrapping, the draft is
 * markdown with headings and a source link appended, so almost every "line"
 * differs even where the wording barely changed. Diffing at the word level
 * instead surfaces exactly what a reviewer needs to see — which words were
 * kept, which were dropped, which were introduced — regardless of how either
 * side happens to be wrapped.
 *
 * Whitespace runs are kept as their own tokens (rather than discarded and
 * reinserted) so that concatenating every segment's `text` in order always
 * reconstructs `after` exactly, and concatenating the 'equal'/'removed'
 * segments' text reconstructs `before` exactly. No token is ever invented or
 * dropped.
 */

export type DiffKind = 'equal' | 'added' | 'removed';

export interface DiffSegment {
  readonly kind: DiffKind;
  readonly text: string;
}

/**
 * The classic LCS alignment used below needs a dynamic-programming table of
 * `(tokens(before) + 1) * (tokens(after) + 1)` cells. `body_markdown` (and
 * the raw source text) can be up to 200,000 characters (§7.8) — tens of
 * thousands of words — and a naive O(n·m) table at that scale would ask a
 * browser tab for hundreds of megabytes to a few gigabytes, depending on how
 * different the two sides are. 4,000,000 cells is a stored `Uint32Array`
 * table of ~16 MB, comfortably below that, while still covering an
 * ordinary-sized blog post (a few hundred to a couple of thousand words) with
 * room to spare.
 *
 * Above the limit, `diffWords` does not attempt an approximate or truncated
 * alignment — a diff that silently compares only part of the input would
 * look complete while quietly hiding a real change outside the truncated
 * window, which is worse than admitting nothing was computed. It returns an
 * honest two-segment "everything before was removed, everything after was
 * added" result instead, and callers are expected to check
 * `exceedsDiffLimit` first and skip word-level rendering entirely — showing
 * the two texts side by side with an explanation — rather than rely on that
 * fallback shape as if it were a real diff.
 */
export const MAX_DIFF_CELLS = 4_000_000;

/** True when `diffWords(before, after)` would exceed the LCS table budget. */
export function exceedsDiffLimit(before: string, after: string): boolean {
  const beforeCount = tokenize(before).length;
  const afterCount = tokenize(after).length;
  return (beforeCount + 1) * (afterCount + 1) > MAX_DIFF_CELLS;
}

export function diffWords(before: string, after: string): readonly DiffSegment[] {
  if (before === after) {
    return before.length === 0 ? [] : [{ kind: 'equal', text: before }];
  }

  const beforeTokens = tokenize(before);
  const afterTokens = tokenize(after);

  if ((beforeTokens.length + 1) * (afterTokens.length + 1) > MAX_DIFF_CELLS) {
    const segments: DiffSegment[] = [];
    if (before.length > 0) {
      segments.push({ kind: 'removed', text: before });
    }
    if (after.length > 0) {
      segments.push({ kind: 'added', text: after });
    }
    return segments;
  }

  return align(beforeTokens, afterTokens);
}

/** Splits into words and whitespace runs, keeping every character, dropping none. */
function tokenize(text: string): readonly string[] {
  if (text.length === 0) {
    return [];
  }
  return text.split(/(\s+)/).filter((token) => token.length > 0);
}

/**
 * Longest-common-subsequence alignment over tokens, then a linear backtrack
 * that turns the alignment into ordered add/remove/equal runs.
 *
 * Ties in the backtrack (an unchanged token could equally be reached by
 * favouring a removal or an addition) always resolve toward "removed", so
 * the same pair of inputs produces the same segmentation on every call —
 * there is no randomness anywhere in this function.
 */
function align(beforeTokens: readonly string[], afterTokens: readonly string[]): DiffSegment[] {
  const n = beforeTokens.length;
  const m = afterTokens.length;

  const table: Uint32Array[] = new Array(n + 1);
  for (let i = 0; i <= n; i += 1) {
    table[i] = new Uint32Array(m + 1);
  }
  for (let i = 1; i <= n; i += 1) {
    for (let j = 1; j <= m; j += 1) {
      table[i][j] =
        beforeTokens[i - 1] === afterTokens[j - 1]
          ? table[i - 1][j - 1] + 1
          : Math.max(table[i - 1][j], table[i][j - 1]);
    }
  }

  const runs: { kind: DiffKind; token: string }[] = [];
  let i = n;
  let j = m;
  while (i > 0 && j > 0) {
    if (beforeTokens[i - 1] === afterTokens[j - 1]) {
      runs.push({ kind: 'equal', token: beforeTokens[i - 1] });
      i -= 1;
      j -= 1;
    } else if (table[i - 1][j] >= table[i][j - 1]) {
      runs.push({ kind: 'removed', token: beforeTokens[i - 1] });
      i -= 1;
    } else {
      runs.push({ kind: 'added', token: afterTokens[j - 1] });
      j -= 1;
    }
  }
  while (i > 0) {
    i -= 1;
    runs.push({ kind: 'removed', token: beforeTokens[i] });
  }
  while (j > 0) {
    j -= 1;
    runs.push({ kind: 'added', token: afterTokens[j] });
  }
  runs.reverse();

  const segments: DiffSegment[] = [];
  for (const { kind, token } of runs) {
    const last = segments.at(-1);
    if (last !== undefined && last.kind === kind) {
      segments[segments.length - 1] = { kind, text: last.text + token };
    } else {
      segments.push({ kind, text: token });
    }
  }
  return segments;
}
