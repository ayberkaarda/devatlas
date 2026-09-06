import { Injectable, inject } from '@angular/core';

import { errorKey } from '../platform/error-key';
import type { TrackDetail, TrackSummary } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { DownloadStore } from './download-store';

/**
 * What a discovery attempt hands back to the screen that asked for it: the
 * value to render, and a translation key for a note about the attempt itself
 * when one is warranted. `noteKey` is `null` whenever nothing needs saying —
 * discovery was not attempted, or it succeeded.
 */
export interface DiscoveryOutcome<T> {
  readonly value: T;
  readonly noteKey: string | null;
}

/**
 * Fills a gap the local replica can be left with on a fresh install.
 *
 * The replica only learns a track's modules, lessons and mind map once that
 * track's manifest has been applied, and nothing does that on its own — a
 * screen that only ever reads has no way to discover content it has never
 * been told about. This service is the one place that closes the gap: it
 * tries `DownloadStore.refreshLibrary` — kept here rather than called on
 * `PlatformService` directly so the same refreshing/delta state the
 * downloads screen already reads through the store stays the single source
 * of truth — exactly once when a local read comes back empty, then re-reads.
 * A second empty result is left alone; this never loops.
 *
 * Nothing here runs unless `capabilities.canDownload` is true. On the web
 * build a local read is never empty for this reason — there is no local
 * store to fail to describe — so a refresh attempt there would only ever be
 * a call the platform is documented to reject.
 *
 * A refresh that rejects (offline, most commonly) is not treated as a
 * failure of the read: the caller keeps whatever it already had and gets a
 * translation key back to show as a quiet note, because being offline is a
 * normal condition for an offline-first application and must not replace a
 * legitimate, if incomplete, local view with a full error screen.
 */
@Injectable({ providedIn: 'root' })
export class LibraryDiscovery {
  private readonly platform = inject(PlatformService);
  private readonly store = inject(DownloadStore);

  async discoverTracks(
    tracks: readonly TrackSummary[],
  ): Promise<DiscoveryOutcome<readonly TrackSummary[]>> {
    if (tracks.length > 0 || !this.platform.capabilities.canDownload) {
      return { value: tracks, noteKey: null };
    }
    try {
      await this.store.refreshLibrary();
    } catch (error) {
      return { value: tracks, noteKey: errorKey(error) };
    }
    return { value: await this.platform.listTracks(), noteKey: null };
  }

  async discoverTrack(detail: TrackDetail): Promise<DiscoveryOutcome<TrackDetail>> {
    if (detail.modules.length > 0 || !this.platform.capabilities.canDownload) {
      return { value: detail, noteKey: null };
    }
    try {
      await this.store.refreshLibrary(detail.id);
    } catch (error) {
      return { value: detail, noteKey: errorKey(error) };
    }
    return { value: await this.platform.getTrack(detail.slug), noteKey: null };
  }
}
