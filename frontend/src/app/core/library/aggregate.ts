import type { LessonSummary, Transfer } from '../platform/models';

/**
 * What a module or track download button renders from.
 *
 * A track is a container, not a downloadable entity in its own right — the
 * platform contract gives it `TrackAvailability`, not the six-value entity
 * `Availability`, and a module has no availability of its own at all. Both
 * are rendered from the same aggregate over their lessons instead of a single
 * enum, which is the two-axis rule applied one level up: "some downloaded,
 * one of them updating" is a normal, simultaneously-true state here exactly
 * as it is for a single lesson.
 */
export interface LessonAggregate {
  readonly downloadedCount: number;
  readonly updateAvailableCount: number;
  readonly totalCount: number;
  /** The lesson whose transfer is reported below, if any is in flight. */
  readonly activeLessonId: string | null;
  readonly transfer: Transfer | null;
}

/**
 * Folds a set of lessons into the counts a container control needs.
 *
 * `transferFor` is injected rather than read from a store directly so the
 * function stays pure and is testable with a plain map. `locallyCompleted`
 * lets a caller reflect a batch that finished moments ago before the parent
 * screen has re-fetched the track — without it, a lesson that just finished
 * downloading would flicker back to "not downloaded" until the next reload.
 */
export function aggregateLessons(
  lessons: readonly LessonSummary[],
  transferFor: (lessonId: string) => Transfer | null,
  locallyCompleted: ReadonlySet<string> = new Set(),
): LessonAggregate {
  let downloadedCount = 0;
  let updateAvailableCount = 0;
  let activeLessonId: string | null = null;
  let transfer: Transfer | null = null;

  for (const lesson of lessons) {
    const availability = lesson.availability.availability;
    const heldLocally =
      locallyCompleted.has(lesson.id) ||
      (availability !== 'NOT_DOWNLOADED' && availability !== 'REMOTE');
    if (heldLocally) {
      downloadedCount += 1;
    }
    if (availability === 'UPDATE_AVAILABLE' && !locallyCompleted.has(lesson.id)) {
      updateAvailableCount += 1;
    }
    if (transfer === null) {
      const candidate = transferFor(lesson.id);
      if (candidate) {
        transfer = candidate;
        activeLessonId = lesson.id;
      }
    }
  }

  return {
    downloadedCount,
    updateAvailableCount,
    totalCount: lessons.length,
    activeLessonId,
    transfer,
  };
}
