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

import type { DownloadUnit } from '../../core/library/aggregate';
import { LibraryDiscovery } from '../../core/library/library-discovery';
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
  private readonly discovery = inject(LibraryDiscovery);

  /** Bound from the route. */
  readonly trackSlug = input.required<string>();

  protected readonly track = signal<TrackDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  /** A discovery attempt that failed. Non-blocking: the track above still renders. */
  protected readonly noteKey = signal<string | null>(null);

  protected readonly canDownload = this.platform.capabilities.canDownload;

  /** Every lesson in the track, flattened, for the track-level download action. */
  protected readonly allLessons = computed<readonly LessonSummary[]>(() => {
    const detail = this.track();
    return detail ? detail.modules.flatMap((module) => module.lessons) : [];
  });

  /**
   * The mind map as one more unit the track-level action can fetch. Counting
   * it is what keeps the download button on a track whose lessons are all
   * stored but whose mind map is not.
   */
  protected readonly mindMapUnits = computed<readonly DownloadUnit[]>(() => {
    const mindMap = this.track()?.mindMap;
    return mindMap ? [mindMap] : [];
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
    this.noteKey.set(null);
    try {
      const initial = await this.platform.getTrack(slug);
      const outcome = await this.discovery.discoverTrack(initial);
      this.track.set(outcome.value);
      this.noteKey.set(outcome.noteKey);
    } catch (error) {
      this.track.set(null);
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
