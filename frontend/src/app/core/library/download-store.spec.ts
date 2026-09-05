import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { PlatformService } from '../platform/platform.service';
import type { DownloadProgress, QueueEntry } from '../platform/models';
import { DownloadStore } from './download-store';

function progress(overrides: Partial<DownloadProgress> = {}): DownloadProgress {
  return {
    entityId: 'lesson-1',
    entityType: 'LESSON',
    state: 'DOWNLOADING',
    receivedBytes: 10,
    totalBytes: 100,
    attempt: 1,
    batch: {
      batchId: 'batch-1',
      completedEntities: 0,
      totalEntities: 1,
      receivedBytes: 10,
      totalBytes: 100,
    },
    errorCode: null,
    ...overrides,
  };
}

function queueEntry(overrides: Partial<QueueEntry> = {}): QueueEntry {
  return {
    entityId: 'lesson-1',
    entityType: 'LESSON',
    title: 'Introduction to signals',
    batchId: 'batch-1',
    state: 'DOWNLOADING',
    receivedBytes: 10,
    totalBytes: 100,
    attempt: 1,
    pauseReason: null,
    errorCode: null,
    ...overrides,
  };
}

describe('DownloadStore', () => {
  let fake: FakePlatformService;
  let events: Subject<DownloadProgress>;
  let queueRows: QueueEntry[];
  let store: DownloadStore;

  beforeEach(() => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };
    events = new Subject<DownloadProgress>();
    queueRows = [queueEntry()];
    fake.downloadProgress = () => events.asObservable();
    fake.queueState = async () => queueRows;

    TestBed.configureTestingModule({
      providers: [{ provide: PlatformService, useValue: fake }],
    });
    store = TestBed.inject(DownloadStore);
  });

  it('reports no transfer for an entity nothing has been said about', () => {
    expect(store.transferFor('lesson-1')).toBeNull();
  });

  it('starts following the event stream on first use and reflects a live event', () => {
    store.transferFor('lesson-1'); // starts the subscription
    events.next(progress({ receivedBytes: 40 }));

    const transfer = store.transferFor('lesson-1');
    expect(transfer).toEqual({
      state: 'DOWNLOADING',
      bytesDone: 40,
      bytesTotal: 100,
      attempts: 1,
      lastError: null,
    });
  });

  it('reports DONE as no transfer, matching the platform contract', () => {
    store.transferFor('lesson-1');
    events.next(progress({ state: 'DONE', receivedBytes: 100 }));
    expect(store.transferFor('lesson-1')).toBeNull();
    expect(store.rawStateFor('lesson-1')).toBe('DONE');
  });

  it('exposes the batch id of an entity in flight, for pause/resume/cancel', () => {
    store.transferFor('lesson-1');
    events.next(progress());
    expect(store.batchIdFor('lesson-1')).toBe('batch-1');
  });

  it('has no batch id for an entity nothing has been said about', () => {
    expect(store.batchIdFor('unknown')).toBeNull();
  });

  it('refreshQueue reads the full queue', async () => {
    await store.refreshQueue();
    expect(store.queue()).toEqual(queueRows);
  });

  it('patches an existing queue row from a live event without a manual refresh', async () => {
    await store.refreshQueue();
    events.next(progress({ receivedBytes: 55, state: 'VERIFYING' }));

    const [entry] = store.queue();
    expect(entry.state).toBe('VERIFYING');
    expect(entry.receivedBytes).toBe(55);
    // Fields the event does not carry are preserved from the last full read.
    expect(entry.title).toBe('Introduction to signals');
  });

  it('does not synthesise a row for an event about an entity the queue has not seen', () => {
    store.transferFor('lesson-1');
    events.next(progress({ entityId: 'lesson-unseen' }));
    expect(store.queue().find((entry) => entry.entityId === 'lesson-unseen')).toBeUndefined();
  });

  it('sums DONE rows into the downloaded total', async () => {
    queueRows = [
      queueEntry({ entityId: 'a', state: 'DONE', totalBytes: 1000 }),
      queueEntry({ entityId: 'b', state: 'DONE', totalBytes: 2000 }),
      queueEntry({ entityId: 'c', state: 'DOWNLOADING', totalBytes: 500 }),
    ];
    await store.refreshQueue();
    expect(store.downloaded()).toEqual({ entities: 2, bytes: 3000 });
  });

  it('enqueue calls through to the platform and refreshes the queue', async () => {
    const handle = await store.enqueue({ kind: 'LESSON', id: 'lesson-1' });
    expect(handle.batchId).toBe('batch');
    expect(fake.enqueued).toEqual([{ kind: 'LESSON', id: 'lesson-1' }]);
    expect(store.queue()).toEqual(queueRows);
  });

  it('refreshLibrary stores the delta summary and clears the refreshing flag', async () => {
    fake.refreshLibrary = async () => ({
      checkedTracks: 1,
      updatedEntities: 3,
      withdrawnEntities: 0,
      newEntitiesAvailable: 0,
      anomalies: [],
    });

    const promise = store.refreshLibrary();
    expect(store.refreshing()).toBe(true);
    const summary = await promise;

    expect(summary.updatedEntities).toBe(3);
    expect(store.delta()?.updatedEntities).toBe(3);
    expect(store.refreshing()).toBe(false);
  });

  it('never subscribes when the platform cannot download', () => {
    const webFake = new FakePlatformService();
    webFake.capabilities = { canDownload: false, hasLocalStore: false };
    let subscribed = false;
    webFake.downloadProgress = () =>
      new Observable<DownloadProgress>(() => {
        subscribed = true;
      });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: PlatformService, useValue: webFake }],
    });
    const webStore = TestBed.inject(DownloadStore);
    webStore.transferFor('lesson-1');

    expect(subscribed).toBe(false);
  });
});
