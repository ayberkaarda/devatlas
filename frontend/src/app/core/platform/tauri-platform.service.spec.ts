import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, take, toArray } from 'rxjs';

import {
  calls,
  emitDesktopEvent,
  listenerCount,
  resetDesktopDouble,
  respondWith,
} from '../../../testing/desktop-ipc-double';
import { API_BASE_URL } from './api';
import type { ProgressEntry } from './models';
import { TauriPlatformService } from './tauri-platform.service';

const TRACK_ID = '018f3a01-2b7c-7a41-8f10-5c9d3e77aa10';
const LESSON_ID = '018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70';
const MIND_MAP_ID = '018f3d90-1a55-7c88-b0e2-6f31c4a9d502';

/** The store's answer for the lesson these tests read. */
function lessonRow() {
  return {
    lessonId: LESSON_ID,
    trackId: TRACK_ID,
    moduleId: 'module-1',
    slug: 'signals-and-reactivity',
    title: 'Introduction to signals',
    bodyMarkdown: '## Signals',
    difficulty: 'BEGINNER',
    estimatedMinutes: 25,
    order: 1,
    contentVersion: 4,
    locale: 'en',
    isFallback: false,
    codeExamples: [],
  };
}

function trackSummaryRow() {
  return {
    trackId: TRACK_ID,
    slug: 'angular-path',
    title: 'The Angular Path',
    description: 'Signals and routing.',
    icon: 'angular',
    contentVersion: 47,
    lessonCount: 32,
    downloadedLessonCount: 12,
    totalSizeBytes: 1893441,
    downloadedSizeBytes: 623104,
    availability: 'PARTIALLY_DOWNLOADED',
    updateAvailableCount: 3,
    withdrawnCount: 0,
  };
}

function mindMapRow(availability = 'DOWNLOADED') {
  return { mindMapId: MIND_MAP_ID, availability, contentVersion: 3, sizeBytes: 12 };
}

function trackDetailRow(availability: string, mindMap: unknown = mindMapRow()) {
  return {
    trackId: TRACK_ID,
    slug: 'angular-path',
    title: 'The Angular Path',
    description: null,
    icon: null,
    contentVersion: 47,
    mindMap,
    modules: [
      {
        moduleId: 'module-1',
        title: 'Reactivity',
        order: 1,
        estimatedMinutes: 95,
        lessons: [
          {
            lessonId: LESSON_ID,
            slug: 'signals-and-reactivity',
            title: 'Introduction to signals',
            difficulty: 'BEGINNER',
            estimatedMinutes: 25,
            order: 1,
            availability,
            contentVersion: 4,
            sizeBytes: 41233,
          },
        ],
      },
    ],
  };
}

describe('TauriPlatformService', () => {
  let service: TauriPlatformService;

  beforeEach(() => {
    resetDesktopDouble();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/api/v1' },
        TauriPlatformService,
      ],
    });
    service = TestBed.inject(TauriPlatformService);
  });

  it('reports both capabilities the web build lacks', () => {
    expect(service.capabilities).toEqual({ canDownload: true, hasLocalStore: true });
  });

  it('maps a track list into the same view model the web build produces', async () => {
    respondWith(async (command) => {
      expect(command).toBe('library_list_tracks');
      return [trackSummaryRow()];
    });

    const tracks = await service.listTracks();
    expect(tracks).toHaveLength(1);
    expect(tracks[0]).toMatchObject({
      id: TRACK_ID,
      slug: 'angular-path',
      availability: 'PARTIALLY_DOWNLOADED',
      downloadedLessonCount: 12,
      updateAvailableCount: 3,
    });
  });

  it('resolves a slug to an identifier before reading the store', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
      throw new Error(`unexpected ${command}`);
    });

    await service.getTrack('angular-path');

    const getTrack = calls.find((call) => call.command === 'library_get_track');
    // The store is addressed by identifier while the interface routes by slug;
    // the conversion happens here so no caller has to know both.
    expect(getTrack?.args).toEqual({ trackId: TRACK_ID });
  });

  it('joins what is stored with what the queue is doing, as two independent axes', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('UPDATE_AVAILABLE');
      if (command === 'download_queue_state') {
        return [
          {
            entityId: LESSON_ID,
            entityType: 'LESSON',
            title: 'Introduction to signals',
            batchId: 'batch-1',
            state: 'DOWNLOADING',
            receivedBytes: 20480,
            totalBytes: 41233,
            attempt: 1,
            pauseReason: null,
            errorCode: null,
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    const track = await service.getTrack('angular-path');
    const availability = track.modules[0].lessons[0].availability;

    // Held locally, a newer version exists, and that newer version is
    // downloading right now: three true statements a single enum could not
    // carry at once.
    expect(availability.availability).toBe('UPDATE_AVAILABLE');
    expect(availability.readable).toBe(true);
    expect(availability.transfer).toEqual({
      state: 'DOWNLOADING',
      bytesDone: 20480,
      bytesTotal: 41233,
      attempts: 1,
      lastError: null,
    });
  });

  it('does not report a finished entry as a transfer', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') {
        return [
          {
            entityId: LESSON_ID,
            entityType: 'LESSON',
            title: null,
            batchId: 'batch-1',
            state: 'DONE',
            receivedBytes: 41233,
            totalBytes: 41233,
            attempt: 1,
            pauseReason: null,
            errorCode: null,
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    const track = await service.getTrack('angular-path');
    // Completion is expressed by the availability axis; a finished transfer
    // lingering in the view model would be rendered twice.
    expect(track.modules[0].lessons[0].availability.transfer).toBeNull();
  });

  it('carries a mind map through as a unit, joined with the queue like a lesson', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') {
        return trackDetailRow('DOWNLOADED', mindMapRow('NOT_DOWNLOADED'));
      }
      if (command === 'download_queue_state') {
        return [
          {
            entityId: MIND_MAP_ID,
            entityType: 'MIND_MAP',
            title: null,
            batchId: 'batch-1',
            state: 'DOWNLOADING',
            receivedBytes: 4,
            totalBytes: 12,
            attempt: 1,
            pauseReason: null,
            errorCode: null,
            locales: [],
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    const track = await service.getTrack('angular-path');

    // Not the boolean the view model used to carry: a control that offers to
    // fetch the mind map needs its size and both availability axes, and a
    // mind map being fetched right now has to read as downloading.
    expect(track.mindMap).toEqual({
      id: MIND_MAP_ID,
      sizeBytes: 12,
      availability: {
        availability: 'NOT_DOWNLOADED',
        readable: false,
        transfer: {
          state: 'DOWNLOADING',
          bytesDone: 4,
          bytesTotal: 12,
          attempts: 1,
          lastError: null,
        },
      },
    });
  });

  it('reports no mind map for a track that has none', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED', null);
      if (command === 'download_queue_state') return [];
      throw new Error(`unexpected ${command}`);
    });

    expect((await service.getTrack('angular-path')).mindMap).toBeNull();
  });

  it('carries the locales a stored package holds, in the order the store gave them', async () => {
    respondWith(async (command) => {
      if (command === 'download_queue_state') {
        return [
          {
            entityId: LESSON_ID,
            entityType: 'LESSON',
            title: 'Introduction to signals',
            batchId: 'batch-1',
            state: 'DONE',
            receivedBytes: 41233,
            totalBytes: 41233,
            attempt: 1,
            pauseReason: null,
            errorCode: null,
            locales: ['en', 'fr', 'tr'],
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    const queue = await service.queueState();
    // A package delivers a lesson and its translations together, so the set is
    // the answer and its order is the store's to decide, not this mapping's.
    expect(queue[0].locales).toEqual(['en', 'fr', 'tr']);
  });

  it('reports an empty locale set for an entity nothing is stored for yet', async () => {
    respondWith(async (command) => {
      if (command === 'download_queue_state') {
        return [
          {
            entityId: LESSON_ID,
            entityType: 'LESSON',
            title: null,
            batchId: 'batch-1',
            state: 'QUEUED',
            receivedBytes: 0,
            totalBytes: 0,
            attempt: 0,
            pauseReason: null,
            errorCode: null,
            locales: [],
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    expect((await service.queueState())[0].locales).toEqual([]);
  });

  it('reads a lesson and reports the store fallback flag it carries', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
      if (command === 'progress_list') return [];
      if (command === 'library_get_lesson') {
        return {
          lessonId: LESSON_ID,
          trackId: TRACK_ID,
          moduleId: 'module-1',
          slug: 'signals-and-reactivity',
          title: 'Introduction to signals',
          bodyMarkdown: '## Signals\n',
          difficulty: 'BEGINNER',
          estimatedMinutes: 25,
          order: 1,
          contentVersion: 4,
          locale: 'en',
          isFallback: true,
          codeExamples: [
            { caption: 'A signal', code: 'const c = signal(0);', language: 'ts', order: 1 },
          ],
        };
      }
      throw new Error(`unexpected ${command}`);
    });

    const lesson = await service.getLesson('signals-and-reactivity');
    expect(lesson.id).toBe(LESSON_ID);
    expect(lesson.translation.isFallback).toBe(true);
    expect(lesson.codeExamples).toHaveLength(1);
  });

  it('reads the completion of a finished lesson out of the local progress table', async () => {
    // Locally, and with no session in play: this is what has to keep working
    // when there is no network and the access token expired hours ago.
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
      if (command === 'library_get_lesson') return lessonRow();
      if (command === 'progress_list') {
        return [
          {
            lessonId: LESSON_ID,
            completedAt: '2026-09-06T20:14:00.000Z',
            clientUpdatedAt: '2026-09-06T20:14:00.000Z',
            syncState: 'SYNCED',
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    const lesson = await service.getLesson('signals-and-reactivity');

    expect(lesson.completedAt).toBe('2026-09-06T20:14:00.000Z');
    expect(calls.map((call) => call.command)).toContain('progress_list');
  });

  it('reports no completion for a lesson with a row that records none', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
      if (command === 'library_get_lesson') return lessonRow();
      if (command === 'progress_list') {
        return [
          {
            lessonId: LESSON_ID,
            completedAt: null,
            clientUpdatedAt: '2026-09-06T20:14:00.000Z',
            syncState: 'SYNCED',
          },
          // Another lesson's completion must not be read as this one's.
          {
            lessonId: 'lesson-elsewhere',
            completedAt: '2026-09-06T21:00:00.000Z',
            clientUpdatedAt: '2026-09-06T21:00:00.000Z',
            syncState: 'SYNCED',
          },
        ];
      }
      throw new Error(`unexpected ${command}`);
    });

    expect((await service.getLesson('signals-and-reactivity')).completedAt).toBeNull();
  });

  it('still returns the lesson when the progress table cannot be read', async () => {
    // The article is readable either way, and losing it over a progress row
    // would be a worse answer than a toggle offering to mark it again.
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
      if (command === 'library_get_lesson') return lessonRow();
      throw { code: 'STORE_UNAVAILABLE', message: 'locked' };
    });

    const lesson = await service.getLesson('signals-and-reactivity');

    expect(lesson.id).toBe(LESSON_ID);
    expect(lesson.completedAt).toBeNull();
  });

  it('sends a partial preference patch rather than restating the other field', async () => {
    respondWith(async (command) => {
      expect(command).toBe('settings_set');
      return {
        locale: 'tr',
        theme: 'DARK',
        preferencesDirtyAt: null,
        lastSyncAt: null,
      };
    });

    await service.setPreferences({ theme: 'DARK' });
    expect(calls[0].args).toEqual({ patch: { theme: 'DARK' } });
  });

  it('narrows unknown stored preference values instead of passing them on', async () => {
    respondWith(async () => ({
      locale: 'kl',
      theme: 'NEON',
      preferencesDirtyAt: null,
      lastSyncAt: null,
    }));

    expect(await service.getPreferences()).toEqual({ locale: 'en', theme: 'SYSTEM' });
  });

  it('surfaces a command failure as a code in the shared namespace', async () => {
    respondWith(async () => {
      throw { code: 'ENTITY_NOT_IN_LIBRARY', message: 'not stored' };
    });

    await expect(service.queueState()).rejects.toMatchObject({
      code: 'ENTITY_NOT_IN_LIBRARY',
    });
  });

  it('streams download progress events and unsubscribes cleanly', async () => {
    const payload = {
      entityId: LESSON_ID,
      entityType: 'LESSON',
      state: 'DOWNLOADING',
      receivedBytes: 20480,
      totalBytes: 41233,
      attempt: 1,
      batch: {
        batchId: 'batch-1',
        completedEntities: 4,
        totalEntities: 32,
        receivedBytes: 512000,
        totalBytes: 1893441,
      },
      errorCode: null,
    };

    const collected = firstValueFrom(service.downloadProgress().pipe(take(1), toArray()));
    // The subscription is opened asynchronously, so the emission has to wait
    // for it rather than race it.
    await Promise.resolve();
    await Promise.resolve();
    emitDesktopEvent('download://progress', payload);

    expect(await collected).toEqual([payload]);
    expect(listenerCount('download://progress')).toBe(0);
  });

  it('reveals the window through the command that pairs with its hidden start', async () => {
    respondWith(async (command) => {
      expect(command).toBe('window_show');
      return null;
    });

    await service.revealApplication();
    expect(calls.map((call) => call.command)).toEqual(['window_show']);
  });

  it('maps pending progress rows, dropping the sync state the command adds', async () => {
    respondWith(async (command) => {
      expect(command).toBe('progress_pending');
      return [
        {
          lessonId: LESSON_ID,
          completedAt: null,
          clientUpdatedAt: '2026-09-04T08:00:00.000Z',
          syncState: 'PENDING',
        },
      ];
    });

    const pending = await service.pendingProgress();
    // The command returns the same row `progress_list` does, plus a
    // `syncState` field the protocol's narrower `PendingProgress` type has no
    // place for; every row this command returns is pending by definition, so
    // dropping the field loses nothing.
    expect(pending).toEqual([
      { lessonId: LESSON_ID, completedAt: null, clientUpdatedAt: '2026-09-04T08:00:00.000Z' },
    ]);
  });

  it('sends a sync result with an explicit null completion apart from one with none at all', async () => {
    respondWith(async (command) => {
      expect(command).toBe('progress_apply_results');
      return null;
    });

    await service.applyProgressResults([
      {
        lessonId: 'a',
        status: 'STALE',
        code: null,
        serverClientUpdatedAt: null,
        completedAt: null,
      },
      {
        lessonId: 'b',
        status: 'APPLIED',
        code: null,
        serverClientUpdatedAt: '2026-09-04T08:00:00.000Z',
      },
    ]);

    const sent = calls[0].args?.['results'] as Record<string, unknown>[];
    // Row 'a' carries an explicit null; row 'b' never mentions the field at
    // all. Collapsing the two would silently un-complete a lesson on every
    // STALE row with no opinion on completion.
    expect(sent[0]).toHaveProperty('completedAt', null);
    expect(sent[1]).not.toHaveProperty('completedAt');
  });

  it('hands a pulled batch to the store, restated field by field', async () => {
    respondWith(async () => null);

    // The extra field stands in for whatever a caller's object happens to
    // carry: absorbing is a write to the replica under a conflict rule, and
    // nothing a caller adds may travel into it.
    const widened = {
      lessonId: LESSON_ID,
      completedAt: '2026-09-02T20:11:07.400Z',
      clientUpdatedAt: '2026-09-02T20:11:07.412Z',
      syncState: 'SYNCED',
    } as ProgressEntry;

    await service.absorbProgress([widened]);

    expect(calls).toEqual([
      {
        command: 'progress_absorb',
        args: {
          entries: [
            {
              lessonId: LESSON_ID,
              completedAt: '2026-09-02T20:11:07.400Z',
              clientUpdatedAt: '2026-09-02T20:11:07.412Z',
            },
          ],
        },
      },
    ]);
  });

  it('loads a remembered session with every field populated', async () => {
    respondWith(async (command) => {
      expect(command).toBe('session_load');
      return {
        userId: 'user-1',
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        accessTokenExpiresAt: '2026-09-04T09:27:33.000Z',
      };
    });

    expect(await service.loadSession()).toEqual({
      userId: 'user-1',
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: '2026-09-04T09:27:33.000Z',
    });
  });

  it('reports no remembered session as null rather than an empty object', async () => {
    respondWith(async () => null);
    expect(await service.loadSession()).toBeNull();
  });

  it('stores a full session and forgets it through the paired commands', async () => {
    respondWith(async () => null);

    const session = {
      userId: 'user-1',
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: '2026-09-04T09:27:33.000Z',
    };
    await service.storeSession(session);
    await service.forgetSession();

    expect(calls).toEqual([
      { command: 'session_store', args: { session } },
      { command: 'session_clear', args: undefined },
    ]);
  });

  it('reads sync bookkeeping from the settings record, apart from the preference fields', async () => {
    respondWith(async (command) => {
      expect(command).toBe('settings_get');
      return {
        locale: 'tr',
        theme: 'DARK',
        preferencesDirtyAt: '2026-09-04T08:41:02.310Z',
        lastSyncAt: '2026-09-03T21:14:55.002Z',
      };
    });

    expect(await service.getSyncState()).toEqual({
      preferencesDirtyAt: '2026-09-04T08:41:02.310Z',
      lastSyncAt: '2026-09-03T21:14:55.002Z',
    });
  });

  it('writes sync bookkeeping without restating locale or theme', async () => {
    respondWith(async () => ({
      locale: 'en',
      theme: 'SYSTEM',
      preferencesDirtyAt: null,
      lastSyncAt: '2026-09-03T21:14:55.002Z',
    }));

    await service.setSyncState({ lastSyncAt: '2026-09-03T21:14:55.002Z' });

    // A patch that restated locale or theme could race a concurrent
    // preference write; only the field the caller supplied is sent.
    expect(calls[0].args).toEqual({ patch: { lastSyncAt: '2026-09-03T21:14:55.002Z' } });
  });

  it('clears a sync bookkeeping field with an explicit null rather than omitting it', async () => {
    respondWith(async () => ({
      locale: 'en',
      theme: 'SYSTEM',
      preferencesDirtyAt: null,
      lastSyncAt: null,
    }));

    await service.setSyncState({ preferencesDirtyAt: null });

    expect(calls[0].args).toEqual({ patch: { preferencesDirtyAt: null } });
  });
});
