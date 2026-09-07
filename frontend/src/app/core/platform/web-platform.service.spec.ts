import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from './api';
import { UnsupportedOnWebError } from './errors';
import { PREFERENCES_STORAGE_KEY, WebPlatformService } from './web-platform.service';

const BASE = 'https://api.example.test/api/v1';

describe('WebPlatformService', () => {
  let service: WebPlatformService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        WebPlatformService,
      ],
    });
    service = TestBed.inject(WebPlatformService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reports no download capability and no local store', () => {
    expect(service.capabilities).toEqual({ canDownload: false, hasLocalStore: false });
  });

  it('maps a track list, reporting availability as REMOTE rather than downloaded', async () => {
    const promise = service.listTracks();

    const request = http.expectOne((candidate) => candidate.url === `${BASE}/tracks`);
    expect(request.request.method).toBe('GET');
    request.flush({
      items: [
        {
          id: 'track-1',
          slug: 'angular-path',
          title: 'Angular Yolu',
          description: 'Modern Angular.',
          icon: 'angular',
          order: 1,
          locale: 'tr',
          requested_locale: 'tr',
          is_fallback: false,
          module_count: 2,
          lesson_count: 28,
          content_version: 14,
          updated_at: '2026-08-29T11:20:04.771Z',
        },
      ],
      page: 0,
      size: 20,
      total_elements: 1,
      total_pages: 1,
    });

    const tracks = await promise;
    expect(tracks).toHaveLength(1);
    expect(tracks[0].availability).toBe('REMOTE');
    // Nothing is stored, so the downloaded count is zero as a fact rather than
    // as a value the platform could not compute.
    expect(tracks[0].downloadedLessonCount).toBe(0);
    expect(tracks[0].translation).toEqual({
      locale: 'tr',
      requestedLocale: 'tr',
      isFallback: false,
    });
  });

  it('carries the per-entity fallback flag through to the view model', async () => {
    const promise = service.getTrack('angular-path');

    http.expectOne(`${BASE}/tracks/angular-path`).flush({
      id: 'track-1',
      slug: 'angular-path',
      title: 'The Angular Path',
      description: null,
      icon: null,
      order: 1,
      locale: 'en',
      requested_locale: 'tr',
      is_fallback: true,
      content_version: 14,
      has_mind_map: true,
      updated_at: '2026-08-29T11:20:04.771Z',
      modules: [
        {
          id: 'module-1',
          title: 'Reaktivite',
          order: 1,
          estimated_minutes: 95,
          locale: 'tr',
          requested_locale: 'tr',
          is_fallback: false,
          lessons: [
            {
              id: 'lesson-1',
              slug: 'signals',
              title: 'Signals',
              difficulty: 'BEGINNER',
              estimated_minutes: 25,
              order: 1,
              content_version: 4,
              locale: 'en',
              requested_locale: 'tr',
              is_fallback: true,
              updated_at: '2026-08-29T11:20:04.771Z',
            },
          ],
        },
      ],
    });

    const track = await promise;
    // One payload mixes translated and untranslated entities, which is why the
    // resolution result is carried per object rather than per response.
    expect(track.translation.isFallback).toBe(true);
    expect(track.modules[0].translation.isFallback).toBe(false);
    expect(track.modules[0].lessons[0].translation.isFallback).toBe(true);
  });

  it('gives every lesson both availability axes, with nothing in flight', async () => {
    const promise = service.getTrack('angular-path');
    http.expectOne(`${BASE}/tracks/angular-path`).flush({
      id: 'track-1',
      slug: 'angular-path',
      title: 'Track',
      description: null,
      icon: null,
      order: 1,
      locale: 'en',
      requested_locale: 'en',
      is_fallback: false,
      content_version: 1,
      has_mind_map: false,
      updated_at: '2026-08-29T11:20:04.771Z',
      modules: [
        {
          id: 'module-1',
          title: 'Module',
          order: 1,
          estimated_minutes: null,
          locale: 'en',
          requested_locale: 'en',
          is_fallback: false,
          lessons: [
            {
              id: 'lesson-1',
              slug: 'signals',
              title: 'Signals',
              difficulty: null,
              estimated_minutes: null,
              order: 1,
              content_version: 1,
              locale: 'en',
              requested_locale: 'en',
              is_fallback: false,
              updated_at: '2026-08-29T11:20:04.771Z',
            },
          ],
        },
      ],
    });

    const availability = (await promise).modules[0].lessons[0].availability;
    expect(availability).toEqual({
      availability: 'REMOTE',
      transfer: null,
      readable: true,
    });
  });

  it('reports a mind map as a live-served unit with no identifier of its own', async () => {
    const promise = service.getTrack('angular-path');
    http.expectOne(`${BASE}/tracks/angular-path`).flush({
      id: 'track-1',
      slug: 'angular-path',
      title: 'Track',
      description: null,
      icon: null,
      order: 1,
      locale: 'en',
      requested_locale: 'en',
      is_fallback: false,
      content_version: 1,
      has_mind_map: true,
      updated_at: '2026-08-29T11:20:04.771Z',
      modules: [],
    });

    // The read API addresses a mind map by its track's slug and never exposes
    // an identifier, and nothing is stored here, so REMOTE is the honest value
    // rather than a plausible-looking DOWNLOADED.
    expect((await promise).mindMap).toEqual({
      id: null,
      sizeBytes: null,
      availability: { availability: 'REMOTE', transfer: null, readable: true },
    });
  });

  it('marks progress through the sync endpoint, sending null for an un-completion', async () => {
    const promise = service.markProgress('lesson-1', false);
    const request = http.expectOne(`${BASE}/sync/progress`);
    expect(request.request.method).toBe('POST');

    const body = request.request.body as {
      items: { lesson_id: string; completed_at: string | null; client_updated_at: string }[];
    };
    expect(body.items).toHaveLength(1);
    expect(body.items[0].lesson_id).toBe('lesson-1');
    // Null is the un-completed state, not the absence of one.
    expect(body.items[0].completed_at).toBeNull();
    expect(body.items[0].client_updated_at).toMatch(/^\d{4}-\d{2}-\d{2}T[\d:]{8}\.\d{3}Z$/);

    request.flush({ server_time: '2026-09-04T09:52:18.006Z', results: [] });
    await promise;
  });

  it('translates a server error body into a code, never a displayable message', async () => {
    const promise = service.getLesson('missing');
    http
      .expectOne(`${BASE}/lessons/missing`)
      .flush(
        { code: 'LESSON_NOT_FOUND', message: "No lesson exists with slug 'missing'." },
        { status: 404, statusText: 'Not Found' },
      );

    await expect(promise).rejects.toMatchObject({ code: 'LESSON_NOT_FOUND' });
  });

  it('reports an unreachable server as its own code, not as a server failure', async () => {
    const promise = service.listTracks();
    http
      .expectOne((candidate) => candidate.url === `${BASE}/tracks`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    await expect(promise).rejects.toMatchObject({ code: 'NETWORK_UNAVAILABLE' });
  });

  it('throws rather than returning an empty success for a library operation', async () => {
    await expect(service.queueState()).rejects.toBeInstanceOf(UnsupportedOnWebError);
    await expect(service.refreshLibrary()).rejects.toBeInstanceOf(UnsupportedOnWebError);
    await expect(service.enqueueDownload()).rejects.toBeInstanceOf(UnsupportedOnWebError);
  });

  it('round-trips preferences through the key the pre-paint script reads', async () => {
    await service.setPreferences({ theme: 'DARK' });
    expect(JSON.parse(localStorage.getItem(PREFERENCES_STORAGE_KEY) ?? '{}')).toEqual({
      locale: 'en',
      theme: 'DARK',
    });

    await service.setPreferences({ locale: 'tr' });
    expect(await service.getPreferences()).toEqual({ locale: 'tr', theme: 'DARK' });
  });

  it('falls back to defaults rather than throwing on unreadable stored preferences', async () => {
    localStorage.setItem(PREFERENCES_STORAGE_KEY, 'not json');
    expect(await service.getPreferences()).toEqual({ locale: 'en', theme: 'SYSTEM' });
  });

  it('treats revealing the application as already satisfied', async () => {
    await expect(service.revealApplication()).resolves.toBeUndefined();
  });
});
