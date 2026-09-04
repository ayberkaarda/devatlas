import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { errorKey } from '../../core/platform/error-key';
import type { TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { FallbackBadge } from '../../shared/fallback-badge';

@Component({
  selector: 'app-track-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, FallbackBadge],
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

  constructor() {
    effect(() => {
      void this.load(this.trackSlug());
    });
  }

  protected reload(): void {
    void this.load(this.trackSlug());
  }

  /**
   * Offers the track for download.
   *
   * The download scope names the entity in the manifest, so it carries the
   * identifier rather than the slug. The control that calls this is only
   * rendered where the capability exists, which is why there is no branch
   * here: an unavailable control is not rendered and disabled, it is absent.
   */
  protected async download(): Promise<void> {
    const current = this.track();
    if (!current) {
      return;
    }
    try {
      await this.platform.enqueueDownload({ kind: 'TRACK', id: current.id });
    } catch (error) {
      this.failure.set(errorKey(error));
    }
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
