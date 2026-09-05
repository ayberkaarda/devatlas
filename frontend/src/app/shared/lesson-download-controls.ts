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

import { DownloadStore } from '../core/library/download-store';
import { errorKey, queueErrorKey } from '../core/platform/error-key';
import { contentAvailability, type Availability, type QueueState } from '../core/platform/models';
import { PlatformService } from '../core/platform/platform.service';
import { BytesFormatPipe } from './bytes.pipe';

/**
 * The download control for exactly one lesson: a download/update button, a
 * live progress readout, and pause/resume/cancel/retry/delete once something
 * is in flight or already stored.
 *
 * It renders nothing at all when `capabilities.canDownload` is false — not a
 * disabled button, an absent one, per platform-service.md §8 rule 4. On the
 * web this component is never even reached by a template that checks the
 * capability first, but the check is repeated here too, so this component
 * stays correct if it is ever rendered from a context that forgot to.
 *
 * The two axes are rendered independently rather than switched on together:
 * `availability` says what is stored, and a live `transfer` can be present or
 * absent regardless of what that says, because a lesson can be downloaded,
 * out of date, and downloading its update at the same time.
 */
@Component({
  selector: 'app-lesson-download-controls',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, BytesFormatPipe],
  templateUrl: './lesson-download-controls.html',
})
export class LessonDownloadControls {
  private readonly platform = inject(PlatformService);
  private readonly store = inject(DownloadStore);

  readonly lessonId = input.required<string>();
  readonly title = input.required<string>();
  readonly baseAvailability = input.required<Availability>();

  protected readonly canDownload = this.platform.capabilities.canDownload;

  /**
   * Overrides the last-fetched availability once this component has seen the
   * transfer it caused reach a terminal state, so a lesson that just finished
   * downloading or was just deleted does not wait for the parent screen to
   * reload before reflecting it.
   */
  private readonly localOverride = signal<Availability | null>(null);

  protected readonly transfer = computed(() => this.store.transferFor(this.lessonId()));
  protected readonly rawState = computed<QueueState | null>(() =>
    this.store.rawStateFor(this.lessonId()),
  );
  protected readonly availability = computed(() =>
    contentAvailability(this.localOverride() ?? this.baseAvailability(), this.transfer()),
  );

  protected readonly busy = signal(false);
  protected readonly actionErrorKey = signal<string | null>(null);
  protected readonly confirmingDelete = signal(false);

  constructor() {
    effect(() => {
      if (this.rawState() === 'DONE') {
        this.localOverride.set('DOWNLOADED');
      }
    });
  }

  protected transferLastErrorKey(): string | null {
    return queueErrorKey(this.transfer()?.lastError ?? null);
  }

  protected async onDownload(): Promise<void> {
    await this.run(() => this.store.enqueue({ kind: 'LESSON', id: this.lessonId() }));
  }

  protected async onPause(): Promise<void> {
    const batchId = this.store.batchIdFor(this.lessonId());
    if (batchId) {
      await this.run(() => this.store.pause(batchId));
    }
  }

  protected async onResume(): Promise<void> {
    const batchId = this.store.batchIdFor(this.lessonId());
    if (batchId) {
      await this.run(() => this.store.resume(batchId));
    }
  }

  protected async onCancel(): Promise<void> {
    const batchId = this.store.batchIdFor(this.lessonId());
    if (batchId) {
      await this.run(() => this.store.cancel(batchId));
    }
  }

  protected async onRetry(): Promise<void> {
    await this.run(() => this.store.retry(this.lessonId()));
  }

  protected requestDelete(): void {
    this.confirmingDelete.set(true);
  }

  protected cancelDeleteRequest(): void {
    this.confirmingDelete.set(false);
  }

  protected async onConfirmDelete(): Promise<void> {
    this.confirmingDelete.set(false);
    await this.run(async () => {
      await this.store.deleteLocal({ kind: 'LESSON', id: this.lessonId() });
      this.localOverride.set('NOT_DOWNLOADED');
    });
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
