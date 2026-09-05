import { contentAvailability } from '../platform/models';
import type { LessonSummary, Transfer } from '../platform/models';
import { aggregateLessons } from './aggregate';

function lesson(
  id: string,
  availability: LessonSummary['availability']['availability'],
): LessonSummary {
  return {
    id,
    slug: id,
    title: id,
    difficulty: null,
    estimatedMinutes: null,
    order: 0,
    availability: contentAvailability(availability),
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
}

const noTransfer = (): Transfer | null => null;

describe('aggregateLessons', () => {
  it('counts nothing downloaded when every lesson is not downloaded', () => {
    const result = aggregateLessons(
      [lesson('a', 'NOT_DOWNLOADED'), lesson('b', 'NOT_DOWNLOADED')],
      noTransfer,
    );
    expect(result).toEqual({
      downloadedCount: 0,
      updateAvailableCount: 0,
      totalCount: 2,
      activeLessonId: null,
      transfer: null,
    });
  });

  it('counts downloaded, update-available and withdrawn lessons as held locally', () => {
    const result = aggregateLessons(
      [
        lesson('a', 'DOWNLOADED'),
        lesson('b', 'UPDATE_AVAILABLE'),
        lesson('c', 'WITHDRAWN'),
        lesson('d', 'NOT_DOWNLOADED'),
      ],
      noTransfer,
    );
    expect(result.downloadedCount).toBe(3);
    expect(result.updateAvailableCount).toBe(1);
    expect(result.totalCount).toBe(4);
  });

  it('never counts REMOTE as held locally', () => {
    const result = aggregateLessons([lesson('a', 'REMOTE')], noTransfer);
    expect(result.downloadedCount).toBe(0);
  });

  it('reports the transfer of the first lesson that has one', () => {
    const transfer: Transfer = {
      state: 'DOWNLOADING',
      bytesDone: 10,
      bytesTotal: 100,
      attempts: 1,
      lastError: null,
    };
    const result = aggregateLessons(
      [lesson('a', 'NOT_DOWNLOADED'), lesson('b', 'NOT_DOWNLOADED')],
      (id) => (id === 'b' ? transfer : null),
    );
    expect(result.activeLessonId).toBe('b');
    expect(result.transfer).toEqual(transfer);
  });

  it('treats a locally-completed id as held even before the parent has reloaded', () => {
    const result = aggregateLessons([lesson('a', 'NOT_DOWNLOADED')], noTransfer, new Set(['a']));
    expect(result.downloadedCount).toBe(1);
  });

  it('does not double-count an update as outstanding once locally completed', () => {
    const result = aggregateLessons([lesson('a', 'UPDATE_AVAILABLE')], noTransfer, new Set(['a']));
    expect(result.updateAvailableCount).toBe(0);
    expect(result.downloadedCount).toBe(1);
  });
});
