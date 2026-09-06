import { TestBed } from '@angular/core/testing';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { PlatformError } from '../platform/errors';
import type { DeltaSummary, TrackDetail, TrackSummary } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { LibraryDiscovery } from './library-discovery';

function summary(): DeltaSummary {
  return {
    checkedTracks: 1,
    updatedEntities: 0,
    withdrawnEntities: 0,
    newEntitiesAvailable: 0,
    anomalies: [],
  };
}

function track(overrides: Partial<TrackSummary> = {}): TrackSummary {
  return {
    id: 'track-1',
    slug: 'signals',
    title: 'Signals',
    description: null,
    icon: null,
    contentVersion: 1,
    lessonCount: 3,
    downloadedLessonCount: 0,
    updateAvailableCount: 0,
    availability: 'NOT_DOWNLOADED',
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

function trackDetail(overrides: Partial<TrackDetail> = {}): TrackDetail {
  return {
    id: 'track-1',
    slug: 'signals',
    title: 'Signals',
    description: null,
    icon: null,
    contentVersion: 1,
    mindMap: null,
    modules: [],
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

describe('LibraryDiscovery', () => {
  let fake: FakePlatformService;
  let discovery: LibraryDiscovery;

  beforeEach(() => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };
    TestBed.configureTestingModule({
      providers: [{ provide: PlatformService, useValue: fake }],
    });
    discovery = TestBed.inject(LibraryDiscovery);
  });

  describe('discoverTracks', () => {
    it('leaves a non-empty list alone and never refreshes', async () => {
      const initial = [track()];
      let refreshCalls = 0;
      fake.refreshLibrary = async () => {
        refreshCalls += 1;
        return summary();
      };

      const outcome = await discovery.discoverTracks(initial);

      expect(outcome.value).toBe(initial);
      expect(outcome.noteKey).toBeNull();
      expect(refreshCalls).toBe(0);
    });

    it('refreshes exactly once and returns the re-read list', async () => {
      let refreshCalls = 0;
      fake.refreshLibrary = async () => {
        refreshCalls += 1;
        fake.tracks = [track()];
        return summary();
      };

      const outcome = await discovery.discoverTracks([]);

      expect(refreshCalls).toBe(1);
      expect(outcome.value).toEqual([track()]);
      expect(outcome.noteKey).toBeNull();
    });

    it('does not refresh a second time when the re-read is still empty', async () => {
      let refreshCalls = 0;
      fake.refreshLibrary = async () => {
        refreshCalls += 1;
        return summary();
      };

      const outcome = await discovery.discoverTracks([]);

      expect(refreshCalls).toBe(1);
      expect(outcome.value).toEqual([]);
      expect(outcome.noteKey).toBeNull();
    });

    it('keeps the local view and reports a note when refresh rejects', async () => {
      fake.refreshLibrary = async () => {
        throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
      };

      const outcome = await discovery.discoverTracks([]);

      expect(outcome.value).toEqual([]);
      expect(outcome.noteKey).toBe('error.NETWORK_UNAVAILABLE');
    });

    it('never calls refresh when the platform cannot download', async () => {
      fake.capabilities = { canDownload: false, hasLocalStore: false };
      let called = false;
      fake.refreshLibrary = async () => {
        called = true;
        return summary();
      };

      const outcome = await discovery.discoverTracks([]);

      expect(called).toBe(false);
      expect(outcome.value).toEqual([]);
      expect(outcome.noteKey).toBeNull();
    });
  });

  describe('discoverTrack', () => {
    it('leaves a track with modules alone and never refreshes', async () => {
      const detail = trackDetail({
        modules: [
          {
            id: 'module-1',
            title: 'Basics',
            order: 0,
            estimatedMinutes: null,
            lessons: [],
            translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
          },
        ],
      });
      let refreshCalls = 0;
      fake.refreshLibrary = async () => {
        refreshCalls += 1;
        return summary();
      };

      const outcome = await discovery.discoverTrack(detail);

      expect(outcome.value).toBe(detail);
      expect(refreshCalls).toBe(0);
    });

    it('refreshes exactly once, with the track id, and returns the re-read track', async () => {
      const initial = trackDetail();
      const refreshed = trackDetail({
        modules: [
          {
            id: 'module-1',
            title: 'Basics',
            order: 0,
            estimatedMinutes: null,
            lessons: [],
            translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
          },
        ],
      });
      const calls: (string | undefined)[] = [];
      fake.refreshLibrary = async (trackId?: string) => {
        calls.push(trackId);
        fake.trackDetails.set(initial.slug, refreshed);
        return summary();
      };
      fake.trackDetails.set(initial.slug, initial);

      const outcome = await discovery.discoverTrack(initial);

      expect(calls).toEqual([initial.id]);
      expect(outcome.value).toEqual(refreshed);
      expect(outcome.noteKey).toBeNull();
    });

    it('does not refresh a second time when the re-read still has zero modules', async () => {
      const initial = trackDetail();
      let refreshCalls = 0;
      fake.trackDetails.set(initial.slug, initial);
      fake.refreshLibrary = async () => {
        refreshCalls += 1;
        return summary();
      };

      const outcome = await discovery.discoverTrack(initial);

      expect(refreshCalls).toBe(1);
      expect(outcome.value).toEqual(initial);
      expect(outcome.noteKey).toBeNull();
    });

    it('keeps the local view and reports a note when refresh rejects', async () => {
      const initial = trackDetail();
      fake.refreshLibrary = async () => {
        throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
      };

      const outcome = await discovery.discoverTrack(initial);

      expect(outcome.value).toBe(initial);
      expect(outcome.noteKey).toBe('error.NETWORK_UNAVAILABLE');
    });

    it('never calls refresh when the platform cannot download', async () => {
      fake.capabilities = { canDownload: false, hasLocalStore: false };
      const initial = trackDetail();
      let called = false;
      fake.refreshLibrary = async () => {
        called = true;
        return summary();
      };

      const outcome = await discovery.discoverTrack(initial);

      expect(called).toBe(false);
      expect(outcome.value).toBe(initial);
      expect(outcome.noteKey).toBeNull();
    });
  });
});
