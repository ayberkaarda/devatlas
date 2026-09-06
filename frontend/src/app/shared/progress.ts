/**
 * The completed share of a transfer, as a whole percent, or `null` when there
 * is nothing honest to report.
 *
 * A queue entry exists before its size is known: a freshly queued entity has a
 * total of zero until the first response says otherwise. Dividing by that is
 * one bug and rounding the result to a full bar is a worse one, because a bar
 * that reads "complete" the moment a download is queued is actively
 * misleading. `null` says "not known yet" and lets the caller render an empty,
 * value-less bar instead of inventing a number.
 *
 * The ratio is clamped because a resumed transfer can briefly report more
 * received bytes than the total it was last told about.
 */
export function progressPercent(done: number, total: number): number | null {
  if (!Number.isFinite(total) || !Number.isFinite(done) || total <= 0) {
    return null;
  }
  const ratio = Math.min(Math.max(done / total, 0), 1);
  return Math.round(ratio * 100);
}
