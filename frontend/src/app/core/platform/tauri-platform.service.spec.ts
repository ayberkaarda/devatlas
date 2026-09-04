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
import { TauriPlatformService } from './tauri-platform.service';

const TRACK_ID = '018f3a01-2b7c-7a41-8f10-5c9d3e77aa10';
const LESSON_ID = '018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70';

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

function trackDetailRow(availability: string) {
  return {
    trackId: TRACK_ID,
    slug: 'angular-path',
    title: 'The Angular Path',
    description: null,
    icon: null,
    contentVersion: 47,
    mindMap: { mindMapId: 'map-1', availability: 'DOWNLOADED', contentVersion: 3, sizeBytes: 12 },
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

  it('reads a lesson and reports the store fallback flag it carries', async () => {
    respondWith(async (command) => {
      if (command === 'library_list_tracks') return [trackSummaryRow()];
      if (command === 'library_get_track') return trackDetailRow('DOWNLOADED');
      if (command === 'download_queue_state') return [];
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
});
