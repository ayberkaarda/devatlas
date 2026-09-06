import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { DownloadStore } from '../../core/library/download-store';
import { errorKey, queueErrorKey } from '../../core/platform/error-key';
import type { DownloadScope, QueueEntry } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { BytesFormatPipe } from '../../shared/bytes.pipe';
import { progressPercent } from '../../shared/progress';
import { ProgressBar } from '../../shared/progress-bar';

interface BatchGroup {
  readonly batchId: string;
  readonly entries: readonly QueueEntry[];
  readonly receivedBytes: number;
  readonly totalBytes: number;
  readonly anyPaused: boolean;
}

/**
 * Downloaded rows for one track. `trackId` is null only for the defensive
 * path -- a row whose track went missing from the store -- and such a group
 * carries no track-level action, since there is no id to scope it to.
 */
interface TrackGroup {
  readonly trackId: string | null;
  readonly trackTitle: string | null;
  readonly entries: readonly QueueEntry[];
}

/**
 * Everything held locally, its size, and what the queue is doing right now.
 *
 * This screen is the control centre for the batch-level actions the download
 * protocol actually offers: pause, resume and cancel act on a batch — "the
 * user-visible operation that enqueued this entity", per
 * `docs/protocol/content-sync.md` §9 — not on a single entity within it, so
 * they are rendered per batch here rather than per row. Retry and delete are
 * genuinely per-entity and are rendered that way.
 *
 * Rendered only where `capabilities.canDownload`; on the web it explains
 * itself instead of calling methods that would throw.
 */
@Component({
  selector: 'app-downloads-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, BytesFormatPipe, ProgressBar],
  templateUrl: './downloads.page.html',
})
export class DownloadsPage {
  private readonly platform = inject(PlatformService);
  protected readonly store = inject(DownloadStore);

  protected readonly canDownload = this.platform.capabilities.canDownload;
  protected readonly checking = this.store.refreshing;
  protected readonly delta = this.store.delta;
  protected readonly downloaded = this.store.downloaded;

  protected readonly checkErrorKey = signal<string | null>(null);
  protected readonly actionErrorKey = signal<string | null>(null);
  protected readonly confirmingDeleteId = signal<string | null>(null);
  /**
   * Kept apart from `confirmingDeleteId`: a lesson-level and a track-level
   * confirmation are different operations at different scopes, and letting
   * one clear or trigger the other would let a confirm click on one row
   * delete a whole track, or vice versa.
   */
  protected readonly confirmingDeleteTrackId = signal<string | null>(null);

  protected readonly batches = computed<readonly BatchGroup[]>(() => {
    const map = new Map<string, QueueEntry[]>();
    for (const entry of this.store.queue()) {
      if (entry.state === 'DONE') {
        continue;
      }
      const list = map.get(entry.batchId) ?? [];
      list.push(entry);
      map.set(entry.batchId, list);
    }
    return [...map.entries()].map(([batchId, entries]) => ({
      batchId,
      entries,
      receivedBytes: entries.reduce((sum, entry) => sum + entry.receivedBytes, 0),
      totalBytes: entries.reduce((sum, entry) => sum + entry.totalBytes, 0),
      anyPaused: entries.some((entry) => entry.state === 'PAUSED'),
    }));
  });

  protected readonly done = computed(() =>
    this.store.queue().filter((entry) => entry.state === 'DONE'),
  );

  /**
   * Downloaded rows grouped by the track they belong to, in first-seen order.
   * A mind map has no download scope of its own -- it arrives with its track
   * and is released with it -- so grouping is what gives it a removal action
   * at all: the group's track-level delete covers every row in it, mind maps
   * included.
   */
  protected readonly doneGroups = computed<readonly TrackGroup[]>(() => {
    const map = new Map<string | null, QueueEntry[]>();
    for (const entry of this.done()) {
      const list = map.get(entry.trackId) ?? [];
      list.push(entry);
      map.set(entry.trackId, list);
    }
    return [...map.entries()].map(([trackId, entries]) => ({
      trackId,
      trackTitle: entries[0]?.trackTitle ?? null,
      entries,
    }));
  });

  constructor() {
    if (this.canDownload) {
      void this.store.refreshQueue();
    }
  }

  protected async checkForUpdates(): Promise<void> {
    this.checkErrorKey.set(null);
    try {
      await this.store.refreshLibrary();
    } catch (error) {
      this.checkErrorKey.set(errorKey(error));
    }
  }

  protected pauseReasonKey(entry: QueueEntry): string {
    return entry.pauseReason === 'INSUFFICIENT_STORAGE'
      ? 'download.state.PAUSED_INSUFFICIENT_STORAGE'
      : 'download.state.PAUSED';
  }

  protected failureKey(entry: QueueEntry): string {
    return queueErrorKey(entry.errorCode) ?? 'error.INTERNAL_ERROR';
  }

  protected entityLabelKey(entry: QueueEntry): string {
    return this.entityTypeLabelKey(entry.entityType);
  }

  protected entityTypeLabelKey(entityType: QueueEntry['entityType']): string {
    return entityType === 'MIND_MAP' ? 'downloads.entityMindMap' : 'downloads.entityLesson';
  }

  /**
   * The share of a batch that has arrived, as a whole percent, or null while
   * its size is still unknown — see `progressPercent`. Null renders nothing
   * rather than a "0%" that would be a guess.
   */
  protected percentOf(done: number, total: number): number | null {
    return progressPercent(done, total);
  }

  /**
   * The locales a stored package holds, as short codes.
   *
   * These are language codes, not interface text, so they are not translated:
   * a French reader looking for the Turkish body wants to see TR. The label
   * explaining what the codes are is a translation key on the element's title.
   */
  protected localeCodes(entry: QueueEntry): string {
    return entry.locales.map((locale) => locale.toUpperCase()).join(' · ');
  }

  /** A downloaded mind map has no independent download scope: it only ever
   * arrives as part of its track, so it cannot be deleted on its own either. */
  protected isDeletable(entry: QueueEntry): boolean {
    return entry.entityType === 'LESSON';
  }

  protected async pauseBatch(batchId: string): Promise<void> {
    await this.run(() => this.store.pause(batchId));
  }

  protected async resumeBatch(batchId: string): Promise<void> {
    await this.run(() => this.store.resume(batchId));
  }

  protected async cancelBatch(batchId: string): Promise<void> {
    await this.run(() => this.store.cancel(batchId));
  }

  protected async retryEntity(entityId: string): Promise<void> {
    await this.run(() => this.store.retry(entityId));
  }

  protected requestDelete(entityId: string): void {
    this.confirmingDeleteTrackId.set(null);
    this.confirmingDeleteId.set(entityId);
  }

  protected cancelDeleteRequest(): void {
    this.confirmingDeleteId.set(null);
  }

  protected async confirmDelete(entityId: string): Promise<void> {
    this.confirmingDeleteId.set(null);
    const scope: DownloadScope = { kind: 'LESSON', id: entityId };
    await this.run(() => this.store.deleteLocal(scope));
  }

  protected requestDeleteTrack(trackId: string): void {
    this.confirmingDeleteId.set(null);
    this.confirmingDeleteTrackId.set(trackId);
  }

  protected cancelDeleteTrackRequest(): void {
    this.confirmingDeleteTrackId.set(null);
  }

  protected async confirmDeleteTrack(trackId: string): Promise<void> {
    this.confirmingDeleteTrackId.set(null);
    const scope: DownloadScope = { kind: 'TRACK', id: trackId };
    await this.run(() => this.store.deleteLocal(scope));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.actionErrorKey.set(null);
    try {
      await action();
    } catch (error) {
      this.actionErrorKey.set(errorKey(error));
    }
  }
}
