import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FakeAuthSession } from '../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../testing/fake-platform.service';
import { AuthSession } from '../auth/auth-session';
import { ConnectivityService } from '../net/connectivity.service';
import { API_BASE_URL } from '../platform/api';
import type { PendingProgress } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { ProgressSyncService } from './progress-sync.service';

const BASE = 'https://api.example.test/api/v1';
const SYNC = `${BASE}/sync/progress`;

/** Lets the awaits inside one cycle reach their next request. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function pendingRow(lessonId: string, index: number): PendingProgress {
  const minute = String(index % 60).padStart(2, '0');
  return {
    lessonId,
    completedAt: `2026-09-02T20:${minute}:07.400Z`,
    clientUpdatedAt: `2026-09-02T20:${minute}:07.412Z`,
  };
}

function emptyPullPage(serverTime = '2026-09-04T09:53:40.119Z') {
  return {
    items: [],
    page: 0,
    size: 100,
    total_elements: 0,
    total_pages: 0,
    server_time: serverTime,
  };
}

describe('ProgressSyncService', () => {
  let platform: FakePlatformService;
  let session: FakeAuthSession;
  let controller: HttpTestingController;
  let connectivity: ConnectivityService;

  /**
   * Arranges the module without creating the service under test: it reads the
   * recorded sync time once at construction, so a test that wants a different
   * starting point has to set it up first.
   */
  function configure(hasLocalStore = true): void {
    TestBed.resetTestingModule();
    platform = new FakePlatformService();
    platform.capabilities = { canDownload: hasLocalStore, hasLocalStore };
    session = new FakeAuthSession();
    session.setRole('USER');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        { provide: PlatformService, useValue: platform },
        { provide: AuthSession, useValue: session },
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    connectivity = TestBed.inject(ConnectivityService);
  }

  beforeEach(() => configure());

  afterEach(() => controller.verify());

  it('pushes what is pending, writes the answer back, then pulls', async () => {
    platform.pending = [pendingRow('lesson-1', 11)];
    const service = TestBed.inject(ProgressSyncService);

    const cycle = service.sync();
    await settle();

    const push = controller.expectOne(
      (request) => request.method === 'POST' && request.url === SYNC,
    );
    expect(push.request.body).toEqual({
      items: [
        {
          lesson_id: 'lesson-1',
          completed_at: '2026-09-02T20:11:07.400Z',
          client_updated_at: '2026-09-02T20:11:07.412Z',
        },
      ],
    });
    push.flush({
      server_time: '2026-09-04T09:52:18.006Z',
      applied_count: 1,
      stale_count: 0,
      rejected_count: 0,
      clamped_count: 0,
      results: [
        {
          lesson_id: 'lesson-1',
          status: 'APPLIED',
          code: null,
          clamped: false,
          server_client_updated_at: '2026-09-02T20:11:07.412Z',
        },
      ],
    });
    await settle();

    // The answer reaches the store before the pull runs, and it carries no
    // completion key at all: absent leaves the local completion alone, while
    // an explicit null would mark the lesson incomplete.
    expect(platform.appliedResults).toHaveLength(1);
    expect(platform.appliedResults[0][0]).toEqual({
      lessonId: 'lesson-1',
      status: 'APPLIED',
      code: null,
      serverClientUpdatedAt: '2026-09-02T20:11:07.412Z',
    });
    expect('completedAt' in platform.appliedResults[0][0]).toBe(false);

    const pull = controller.expectOne(
      (request) => request.method === 'GET' && request.url === SYNC,
    );
    expect(pull.request.params.get('page')).toBe('0');
    expect(pull.request.params.get('size')).toBe('100');
    // Nothing has been accepted before, so there is no lower bound to send.
    expect(pull.request.params.has('since')).toBe(false);
    pull.flush({
      items: [
        {
          lesson_id: 'lesson-9',
          completed_at: '2026-09-03T08:00:00.000Z',
          client_updated_at: '2026-09-03T08:00:00.100Z',
          updated_at: '2026-09-03T08:00:01.000Z',
        },
      ],
      page: 0,
      size: 100,
      total_elements: 1,
      total_pages: 1,
      server_time: '2026-09-04T09:53:40.119Z',
    });
    await cycle;

    expect(platform.absorbed).toEqual([
      [
        {
          lessonId: 'lesson-9',
          completedAt: '2026-09-03T08:00:00.000Z',
          clientUpdatedAt: '2026-09-03T08:00:00.100Z',
        },
      ],
    ]);
    // The earlier of the two server readings, so a row written between them
    // is fetched again rather than skipped.
    expect(platform.syncState.lastSyncAt).toBe('2026-09-04T09:52:18.006Z');
    expect(service.lastSyncAt()).toBe('2026-09-04T09:52:18.006Z');
  });

  it('splits a batch the server would refuse into ones it accepts', async () => {
    platform.pending = Array.from({ length: 501 }, (_unused, index) =>
      pendingRow(`lesson-${index}`, index),
    );
    const service = TestBed.inject(ProgressSyncService);

    const cycle = service.sync();
    await settle();

    const first = controller.expectOne(
      (request) => request.method === 'POST' && request.url === SYNC,
    );
    expect((first.request.body as { items: unknown[] }).items).toHaveLength(500);
    first.flush({
      server_time: '2026-09-04T09:52:18.006Z',
      applied_count: 500,
      stale_count: 0,
      rejected_count: 0,
      clamped_count: 0,
      results: [],
    });
    await settle();

    // A device can hold weeks of offline writes. Discovering the ceiling from
    // a 413 would mean the first sync after a long trip is the one that fails.
    const second = controller.expectOne(
      (request) => request.method === 'POST' && request.url === SYNC,
    );
    expect((second.request.body as { items: unknown[] }).items).toHaveLength(1);
    second.flush({
      server_time: '2026-09-04T09:52:19.006Z',
      applied_count: 1,
      stale_count: 0,
      rejected_count: 0,
      clamped_count: 0,
      results: [],
    });
    await settle();

    controller
      .expectOne((request) => request.method === 'GET' && request.url === SYNC)
      .flush(emptyPullPage());
    await cycle;
  });

  it('walks every page of the pull rather than reading the first one', async () => {
    platform.syncState = { preferencesDirtyAt: null, lastSyncAt: '2026-09-01T00:00:00.000Z' };
    const service = TestBed.inject(ProgressSyncService);

    const cycle = service.sync();
    await settle();

    const first = controller.expectOne(
      (request) => request.method === 'GET' && request.url === SYNC,
    );
    expect(first.request.params.get('since')).toBe('2026-09-01T00:00:00.000Z');
    first.flush({
      items: [
        {
          lesson_id: 'lesson-1',
          completed_at: null,
          client_updated_at: '2026-09-03T08:00:00.100Z',
          updated_at: '2026-09-03T08:00:01.000Z',
        },
      ],
      page: 0,
      size: 100,
      total_elements: 2,
      total_pages: 2,
      server_time: '2026-09-04T09:53:40.119Z',
    });
    await settle();

    const second = controller.expectOne(
      (request) => request.method === 'GET' && request.url === SYNC,
    );
    expect(second.request.params.get('page')).toBe('1');
    second.flush({
      items: [
        {
          lesson_id: 'lesson-2',
          completed_at: '2026-09-03T09:00:00.000Z',
          client_updated_at: '2026-09-03T09:00:00.100Z',
          updated_at: '2026-09-03T09:00:01.000Z',
        },
      ],
      page: 1,
      size: 100,
      total_elements: 2,
      total_pages: 2,
      server_time: '2026-09-04T09:53:41.119Z',
    });
    await cycle;

    expect(platform.absorbed).toHaveLength(2);
    // A row explicitly marked incomplete travels as such rather than as an
    // absence.
    expect(platform.absorbed[0][0].completedAt).toBeNull();
  });

  it('leaves the recorded time alone when the cycle fails', async () => {
    platform.pending = [pendingRow('lesson-1', 11)];
    platform.syncState = { preferencesDirtyAt: null, lastSyncAt: '2026-09-01T00:00:00.000Z' };
    const service = TestBed.inject(ProgressSyncService);

    const cycle = service.sync();
    await settle();
    controller
      .expectOne((request) => request.method === 'POST' && request.url === SYNC)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await cycle;

    // "Last synchronised" is the last time a batch was accepted, not the last
    // time one was attempted, and the pending rows are still pending.
    expect(platform.syncState.lastSyncAt).toBe('2026-09-01T00:00:00.000Z');
    expect(service.lastSyncAt()).toBe('2026-09-01T00:00:00.000Z');
    expect(platform.syncStateWrites).toEqual([]);
    // A request that reached nothing is evidence about the network.
    expect(connectivity.offline()).toBe(true);
  });

  it('joins the cycle in flight instead of starting a second one', async () => {
    platform.pending = [pendingRow('lesson-1', 11)];
    const service = TestBed.inject(ProgressSyncService);

    const first = service.sync();
    const second = service.sync();
    expect(second).toBe(first);
    await settle();

    controller
      .expectOne((request) => request.method === 'POST' && request.url === SYNC)
      .flush({
        server_time: '2026-09-04T09:52:18.006Z',
        applied_count: 1,
        stale_count: 0,
        rejected_count: 0,
        clamped_count: 0,
        results: [],
      });
    await settle();
    controller
      .expectOne((request) => request.method === 'GET' && request.url === SYNC)
      .flush(emptyPullPage());
    await Promise.all([first, second]);
  });

  it('does nothing at all where progress is not stored locally', async () => {
    configure(false);
    platform.pending = [pendingRow('lesson-1', 11)];
    const service = TestBed.inject(ProgressSyncService);

    await service.sync();

    // On the web, marking progress already is the sync, and the pending
    // methods throw by design; a cycle here would call one of them.
    controller.expectNone(() => true);
    expect(service.lastSyncAt()).toBeNull();
  });

  it('ends quietly while there is no route to the server', async () => {
    const service = TestBed.inject(ProgressSyncService);
    connectivity.reportUnreachable();
    platform.pending = [pendingRow('lesson-1', 11)];

    await service.sync();

    controller.expectNone(() => true);
  });

  it('ends quietly in local-only mode, where the credential was refused', async () => {
    const service = TestBed.inject(ProgressSyncService);
    session.enterLocalOnly();
    platform.pending = [pendingRow('lesson-1', 11)];

    await service.sync();

    // Nothing local is deleted and nothing is blocked; there is simply
    // nobody to send to until the user signs in again.
    controller.expectNone(() => true);
  });
});
