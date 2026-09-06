import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { aggregateLessons, type DownloadUnit } from '../core/library/aggregate';
import { DownloadStore } from '../core/library/download-store';
import { errorKey } from '../core/platform/error-key';
import type { DownloadScope, LessonSummary } from '../core/platform/models';
import { PlatformService } from '../core/platform/platform.service';
import { BytesFormatPipe } from './bytes.pipe';
import { ProgressBar } from './progress-bar';

/**
 * The download action for a module or a track: neither has an `Availability`
 * of its own, so this renders from an aggregate over the lessons it contains
 * rather than a single enum. "12 of 32 downloaded, one updating" is the
 * container equivalent of a lesson being downloaded and updating at once.
 *
 * There is no delete button here. Removing every lesson in a module or track
 * is a heavier action than the other controls on this component, and it gets
 * its own confirmed flow on the downloads screen instead of a one-click
 * button inline in a lesson list.
 */
@Component({
  selector: 'app-container-download-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, BytesFormatPipe, ProgressBar],
  templateUrl: './container-download-action.html',
})
export class ContainerDownloadAction {
  private readonly platform = inject(PlatformService);
  private readonly store = inject(DownloadStore);

  readonly scope = input.required<DownloadScope>();
  readonly title = input.required<string>();
  readonly lessons = input.required<readonly LessonSummary[]>();
  /**
   * Downloadable units this container holds beyond its lessons — a track's
   * mind map is the one that exists today. A module has none and passes
   * nothing, which leaves the counts exactly as they were.
   */
  readonly extraUnits = input<readonly DownloadUnit[]>([]);

  protected readonly canDownload = this.platform.capabilities.canDownload;

  private readonly locallyCompleted = signal<ReadonlySet<string>>(new Set());

  protected readonly counts = computed(() =>
    aggregateLessons(
      this.lessons(),
      (entityId) => this.store.transferFor(entityId),
      this.locallyCompleted(),
      this.extraUnits(),
    ),
  );

  protected readonly busy = signal(false);
  protected readonly actionErrorKey = signal<string | null>(null);

  constructor() {
    // Marks a unit as held locally as soon as its own transfer completes,
    // rather than waiting for the parent screen to re-fetch the whole track.
    effect(() => {
      const ids = [
        ...this.lessons().map((lesson) => lesson.id),
        ...this.extraUnits().map((unit) => unit.id),
      ];
      const current = this.locallyCompleted();
      let next: Set<string> | null = null;
      for (const id of ids) {
        if (id !== null && this.store.rawStateFor(id) === 'DONE' && !current.has(id)) {
          next ??= new Set(current);
          next.add(id);
        }
      }
      if (next) {
        this.locallyCompleted.set(next);
      }
    });
  }

  protected async onDownload(): Promise<void> {
    await this.run(() => this.store.enqueue(this.scope()));
  }

  protected async onPause(): Promise<void> {
    const batchId = this.activeBatchId();
    if (batchId) {
      await this.run(() => this.store.pause(batchId));
    }
  }

  protected async onResume(): Promise<void> {
    const batchId = this.activeBatchId();
    if (batchId) {
      await this.run(() => this.store.resume(batchId));
    }
  }

  protected async onCancel(): Promise<void> {
    const batchId = this.activeBatchId();
    if (batchId) {
      await this.run(() => this.store.cancel(batchId));
    }
  }

  protected async onRetry(): Promise<void> {
    const activeEntityId = this.counts().activeEntityId;
    await this.run(() => this.store.retry(activeEntityId ?? undefined));
  }

  private activeBatchId(): string | null {
    const activeEntityId = this.counts().activeEntityId;
    return activeEntityId ? this.store.batchIdFor(activeEntityId) : null;
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    this.actionErrorKey.set(null);
    try {
      await action();
    } catch (error) {
      this.actionErrorKey.set(errorKey(error));
    } finally {
      this.busy.set(false);
    }
  }
}
