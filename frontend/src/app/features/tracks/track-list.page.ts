import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LibraryDiscovery } from '../../core/library/library-discovery';
import { errorKey } from '../../core/platform/error-key';
import type { TrackSummary } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { FallbackBadge } from '../../shared/fallback-badge';

@Component({
  selector: 'app-track-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, FallbackBadge],
  templateUrl: './track-list.page.html',
})
export class TrackListPage {
  private readonly platform = inject(PlatformService);
  private readonly discovery = inject(LibraryDiscovery);

  /**
   * Read once, at construction. The list is the same whichever implementation
   * answers, and this component has no way of telling which one did.
   */
  protected readonly tracks = signal<readonly TrackSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  /** A discovery attempt that failed. Non-blocking: the list above still renders. */
  protected readonly noteKey = signal<string | null>(null);

  /**
   * Whether a download count means anything here. The question is about the
   * capability, not about the platform: a component that asked which build it
   * was in would have learned something it is not allowed to know.
   */
  protected readonly canDownload = this.platform.capabilities.canDownload;

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.noteKey.set(null);
    try {
      const initial = await this.platform.listTracks();
      const outcome = await this.discovery.discoverTracks(initial);
      this.tracks.set(outcome.value);
      this.noteKey.set(outcome.noteKey);
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
