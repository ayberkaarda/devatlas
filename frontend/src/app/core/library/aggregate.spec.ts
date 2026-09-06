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
      activeEntityId: null,
      transfer: null,
      complete: false,
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
    expect(result.activeEntityId).toBe('b');
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

  it('treats an empty lesson set as incomplete, not vacuously complete', () => {
    const result = aggregateLessons([], noTransfer);
    expect(result.totalCount).toBe(0);
    expect(result.complete).toBe(false);
  });

  it('is complete once every lesson is held locally and none has an update pending', () => {
    const result = aggregateLessons(
      [lesson('a', 'DOWNLOADED'), lesson('b', 'WITHDRAWN')],
      noTransfer,
    );
    expect(result.complete).toBe(true);
  });

  it('is not complete while an update is available, even if everything is downloaded', () => {
    const result = aggregateLessons([lesson('a', 'UPDATE_AVAILABLE')], noTransfer);
    expect(result.complete).toBe(false);
  });

  it('counts an extra unit as one more thing to fetch', () => {
    const result = aggregateLessons(
      [lesson('a', 'DOWNLOADED'), lesson('b', 'DOWNLOADED')],
      noTransfer,
      new Set(),
      [{ id: 'map-1', availability: contentAvailability('NOT_DOWNLOADED') }],
    );
    // Every lesson is held locally, and there is still something missing.
    expect(result.totalCount).toBe(3);
    expect(result.downloadedCount).toBe(2);
    expect(result.complete).toBe(false);
  });

  it('is complete once the extra unit is downloaded too', () => {
    const result = aggregateLessons([lesson('a', 'DOWNLOADED')], noTransfer, new Set(), [
      { id: 'map-1', availability: contentAvailability('DOWNLOADED') },
    ]);
    expect(result.totalCount).toBe(2);
    expect(result.downloadedCount).toBe(2);
    expect(result.complete).toBe(true);
  });

  it('reports an extra unit that is in flight as the active transfer', () => {
    const transfer: Transfer = {
      state: 'DOWNLOADING',
      bytesDone: 512,
      bytesTotal: 4096,
      attempts: 1,
      lastError: null,
    };
    const result = aggregateLessons(
      [lesson('a', 'DOWNLOADED')],
      (id) => (id === 'map-1' ? transfer : null),
      new Set(),
      [{ id: 'map-1', availability: contentAvailability('NOT_DOWNLOADED') }],
    );
    expect(result.activeEntityId).toBe('map-1');
    expect(result.transfer).toEqual(transfer);
  });

  it('counts an extra unit with an update pending as outstanding', () => {
    const result = aggregateLessons([lesson('a', 'DOWNLOADED')], noTransfer, new Set(), [
      { id: 'map-1', availability: contentAvailability('UPDATE_AVAILABLE') },
    ]);
    expect(result.updateAvailableCount).toBe(1);
    expect(result.complete).toBe(false);
  });

  it('never looks a transfer up for a unit the platform gives no identifier', () => {
    const lookups: string[] = [];
    const result = aggregateLessons(
      [],
      (id) => {
        lookups.push(id);
        return null;
      },
      new Set(),
      [{ id: null, availability: contentAvailability('REMOTE') }],
    );
    expect(lookups).toEqual([]);
    expect(result.totalCount).toBe(1);
    expect(result.downloadedCount).toBe(0);
  });

  it('leaves a container that passes no extra units exactly as it was', () => {
    const withoutExtras = aggregateLessons([lesson('a', 'DOWNLOADED')], noTransfer);
    const withEmptyExtras = aggregateLessons(
      [lesson('a', 'DOWNLOADED')],
      noTransfer,
      new Set(),
      [],
    );
    expect(withEmptyExtras).toEqual(withoutExtras);
    expect(withoutExtras.complete).toBe(true);
  });
});
