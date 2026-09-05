import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { errorKey } from '../../core/platform/error-key';
import type { LessonSummary, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ContainerDownloadAction } from '../../shared/container-download-action';
import { FallbackBadge } from '../../shared/fallback-badge';
import { LessonDownloadControls } from '../../shared/lesson-download-controls';

@Component({
  selector: 'app-track-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    TranslatePipe,
    FallbackBadge,
    ContainerDownloadAction,
    LessonDownloadControls,
  ],
  templateUrl: './track-detail.page.html',
})
export class TrackDetailPage {
  private readonly platform = inject(PlatformService);

  /** Bound from the route. */
  readonly trackSlug = input.required<string>();

  protected readonly track = signal<TrackDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  protected readonly canDownload = this.platform.capabilities.canDownload;

  /** Every lesson in the track, flattened, for the track-level download action. */
  protected readonly allLessons = computed<readonly LessonSummary[]>(() => {
    const detail = this.track();
    return detail ? detail.modules.flatMap((module) => module.lessons) : [];
  });

  constructor() {
    effect(() => {
      void this.load(this.trackSlug());
    });
  }

  protected reload(): void {
    void this.load(this.trackSlug());
  }

  private async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      this.track.set(await this.platform.getTrack(slug));
    } catch (error) {
      this.track.set(null);
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
