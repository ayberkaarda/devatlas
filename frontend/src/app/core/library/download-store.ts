import { Injectable, computed, inject, signal } from '@angular/core';

import { PlatformService } from '../platform/platform.service';
import type {
  BatchHandle,
  BatchProgress,
  DeltaSummary,
  DownloadProgress,
  DownloadScope,
  QueueEntry,
  QueueState,
  Transfer,
} from '../platform/models';

/**
 * The one place the live download queue is read from.
 *
 * Every screen that shows a download in progress — a lesson row, a module or
 * track button, the downloads screen, a mind map node — needs the same two
 * things: what the queue looked like when it was last read in full, and
 * what has happened since. `PlatformService.downloadProgress()` only ever
 * describes changes, so a component that subscribed to it directly and
 * nothing else would have no history to render for a batch that started
 * before it existed. This service owns that reconciliation once, centrally,
 * rather than once per component.
 *
 * On the web build `capabilities.canDownload` is false and this service never
 * subscribes to anything: every signal here stays at its empty default, which
 * is honest — there is no queue, not a queue nobody is watching.
 */
@Injectable({ providedIn: 'root' })
export class DownloadStore {
  private readonly platform = inject(PlatformService);

  private readonly queueState = signal<readonly QueueEntry[]>([]);
  private readonly progressState = signal<ReadonlyMap<string, DownloadProgress>>(new Map());
  private readonly deltaState = signal<DeltaSummary | null>(null);
  private readonly refreshingState = signal(false);

  readonly queue = this.queueState.asReadonly();
  readonly delta = this.deltaState.asReadonly();
  readonly refreshing = this.refreshingState.asReadonly();

  /** Total bytes and count held locally, derived from `DONE` queue rows. */
  readonly downloaded = computed(() => {
    let entities = 0;
    let bytes = 0;
    for (const entry of this.queueState()) {
      if (entry.state === 'DONE') {
        entities += 1;
        bytes += entry.totalBytes;
      }
    }
    return { entities, bytes };
  });

  private started = false;

  /**
   * The live transfer for one entity, or `null` when nothing is in flight.
   *
   * `DONE` is deliberately excluded, matching the platform contract: a
   * finished transfer is expressed through availability, not through a
   * transfer object that lingers after completion.
   */
  transferFor(entityId: string): Transfer | null {
    this.ensureStarted();
    const event = this.progressState().get(entityId);
    if (!event || event.state === 'DONE') {
      return null;
    }
    return {
      state: event.state,
      bytesDone: event.receivedBytes,
      bytesTotal: event.totalBytes,
      attempts: event.attempt,
      lastError: event.errorCode,
    };
  }

  /** The raw queue state last reported for an entity, `DONE` included. */
  rawStateFor(entityId: string): QueueState | null {
    this.ensureStarted();
    return this.progressState().get(entityId)?.state ?? null;
  }

  /** The batch an entity's in-flight transfer belongs to, for pause/resume/cancel. */
  batchIdFor(entityId: string): string | null {
    this.ensureStarted();
    return this.progressState().get(entityId)?.batch.batchId ?? null;
  }

  /** The overall progress of a batch, from whichever entity last reported it. */
  batchProgress(batchId: string): BatchProgress | null {
    this.ensureStarted();
    for (const event of this.progressState().values()) {
      if (event.batch.batchId === batchId) {
        return event.batch;
      }
    }
    return null;
  }

  /** Re-reads the full queue. Called on startup and after every action below. */
  async refreshQueue(): Promise<void> {
    this.ensureStarted();
    if (!this.platform.capabilities.canDownload) {
      return;
    }
    this.queueState.set(await this.platform.queueState());
  }

  /**
   * Compares the store against the manifest without downloading anything.
   *
   * The result is kept so the update badge can read it without every caller
   * re-triggering a network round trip; a second call replaces it.
   */
  async refreshLibrary(trackId?: string): Promise<DeltaSummary> {
    this.refreshingState.set(true);
    try {
      const summary = await this.platform.refreshLibrary(trackId);
      this.deltaState.set(summary);
      return summary;
    } finally {
      this.refreshingState.set(false);
    }
  }

  async enqueue(scope: DownloadScope): Promise<BatchHandle> {
    this.ensureStarted();
    const handle = await this.platform.enqueueDownload(scope);
    await this.refreshQueue();
    return handle;
  }

  async pause(batchId?: string): Promise<void> {
    await this.platform.pauseDownloads(batchId);
    await this.refreshQueue();
  }

  async resume(batchId?: string): Promise<void> {
    await this.platform.resumeDownloads(batchId);
    await this.refreshQueue();
  }

  async cancel(batchId: string): Promise<void> {
    await this.platform.cancelDownload(batchId);
    await this.refreshQueue();
  }

  async retry(entityId?: string): Promise<void> {
    await this.platform.retryDownload(entityId);
    await this.refreshQueue();
  }

  async deleteLocal(scope: DownloadScope): Promise<void> {
    await this.platform.deleteLocal(scope);
    await this.refreshQueue();
  }

  /**
   * Starts following the event stream, once, the first time any screen asks
   * for a live value. Deferred rather than done in the constructor so a build
   * with `canDownload: false` never opens a subscription that would only ever
   * receive `EMPTY`.
   */
  private ensureStarted(): void {
    if (this.started || !this.platform.capabilities.canDownload) {
      return;
    }
    this.started = true;
    this.platform.downloadProgress().subscribe((event) => {
      const next = new Map(this.progressState());
      next.set(event.entityId, event);
      this.progressState.set(next);
      this.patchQueueFrom(event);
    });
    void this.refreshQueue();
  }

  /**
   * Keeps the full queue list current between explicit reloads, so a screen
   * showing every entry does not need to re-fetch the whole queue on every
   * event. An event for an entity the queue snapshot does not know about yet
   * is not synthesised into a row here — `title` and `batchId` context for a
   * brand-new row comes from `refreshQueue`, which every action already
   * triggers.
   */
  private patchQueueFrom(event: DownloadProgress): void {
    const current = this.queueState();
    const index = current.findIndex((entry) => entry.entityId === event.entityId);
    if (index === -1) {
      return;
    }
    const existing = current[index];
    const updated: QueueEntry = {
      ...existing,
      batchId: event.batch.batchId,
      state: event.state,
      receivedBytes: event.receivedBytes,
      totalBytes: event.totalBytes,
      attempt: event.attempt,
      errorCode: event.errorCode,
    };
    const next = [...current];
    next[index] = updated;
    this.queueState.set(next);
  }
}
