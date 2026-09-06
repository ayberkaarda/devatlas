import type { ContentAvailability, LessonSummary, Transfer } from '../platform/models';

/**
 * One downloadable unit inside a container, reduced to what the aggregate
 * needs: an identity to look a live transfer up by, and what is stored.
 *
 * A lesson is the usual one. A track's mind map is the other: it is a separate
 * package with its own availability, so a container that includes one counts
 * it as one more unit. Without that, a track whose four lessons are all
 * downloaded reports itself complete, hides its download button, and leaves
 * the mind map with no control anywhere that can ask for it.
 */
export interface DownloadUnit {
  /** Null where the platform has no identifier for the unit; never in flight then. */
  readonly id: string | null;
  readonly availability: ContentAvailability;
}

/**
 * What a module or track download button renders from.
 *
 * A track is a container, not a downloadable entity in its own right — the
 * platform contract gives it `TrackAvailability`, not the six-value entity
 * `Availability`, and a module has no availability of its own at all. Both
 * are rendered from the same aggregate over their units instead of a single
 * enum, which is the two-axis rule applied one level up: "some downloaded,
 * one of them updating" is a normal, simultaneously-true state here exactly
 * as it is for a single lesson.
 */
export interface LessonAggregate {
  readonly downloadedCount: number;
  readonly updateAvailableCount: number;
  readonly totalCount: number;
  /** The unit whose transfer is reported below, if any is in flight. */
  readonly activeEntityId: string | null;
  readonly transfer: Transfer | null;
  /**
   * Whether this container has nothing left to fetch: it holds at least one
   * unit and every one of them is downloaded, with no update pending.
   *
   * A container with `totalCount === 0` is deliberately not complete. Zero
   * units locally is indistinguishable from a container whose structure
   * has never been read at all, and treating that as "fully downloaded"
   * would hide the only control that can fetch it.
   */
  readonly complete: boolean;
}

/**
 * Folds a container's lessons, plus whatever else it contains, into the counts
 * a container control needs.
 *
 * `extraUnits` is how a non-lesson unit enters the count — the caller names it
 * explicitly rather than the fold guessing which of a track's parts are
 * downloadable. A module-level control passes none and is unaffected.
 *
 * `transferFor` is injected rather than read from a store directly so the
 * function stays pure and is testable with a plain map. `locallyCompleted`
 * lets a caller reflect a batch that finished moments ago before the parent
 * screen has re-fetched the track — without it, a lesson that just finished
 * downloading would flicker back to "not downloaded" until the next reload.
 */
export function aggregateLessons(
  lessons: readonly LessonSummary[],
  transferFor: (entityId: string) => Transfer | null,
  locallyCompleted: ReadonlySet<string> = new Set(),
  extraUnits: readonly DownloadUnit[] = [],
): LessonAggregate {
  const units: readonly DownloadUnit[] = [
    ...lessons.map((lesson) => ({ id: lesson.id, availability: lesson.availability })),
    ...extraUnits,
  ];

  let downloadedCount = 0;
  let updateAvailableCount = 0;
  let activeEntityId: string | null = null;
  let transfer: Transfer | null = null;

  for (const unit of units) {
    const availability = unit.availability.availability;
    const completedLocally = unit.id !== null && locallyCompleted.has(unit.id);
    const heldLocally =
      completedLocally || (availability !== 'NOT_DOWNLOADED' && availability !== 'REMOTE');
    if (heldLocally) {
      downloadedCount += 1;
    }
    if (availability === 'UPDATE_AVAILABLE' && !completedLocally) {
      updateAvailableCount += 1;
    }
    if (transfer === null && unit.id !== null) {
      const candidate = transferFor(unit.id);
      if (candidate) {
        transfer = candidate;
        activeEntityId = unit.id;
      }
    }
  }

  const totalCount = units.length;
  return {
    downloadedCount,
    updateAvailableCount,
    totalCount,
    activeEntityId,
    transfer,
    complete: totalCount > 0 && downloadedCount >= totalCount && updateAvailableCount === 0,
  };
}
