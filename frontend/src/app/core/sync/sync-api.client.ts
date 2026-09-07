import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API_BASE_URL, toPlatformError } from '../platform/api';
import type { PendingProgress, ProgressEntry, ProgressSyncResult } from '../platform/models';
import type { WirePage, WireProgressItem } from '../platform/rest-wire';

/** The largest batch the server accepts; a larger one is refused outright. */
export const SYNC_BATCH_LIMIT = 500;

/** The largest page the read side allows. */
export const SYNC_PAGE_SIZE = 100;

interface WireSyncResult {
  readonly lesson_id: string;
  readonly status: string;
  readonly code: string | null;
  readonly clamped: boolean;
  readonly server_client_updated_at: string | null;
}

interface WireSyncResponse {
  readonly server_time: string;
  readonly applied_count: number;
  readonly stale_count: number;
  readonly rejected_count: number;
  readonly clamped_count: number;
  readonly results: readonly WireSyncResult[];
}

interface WirePulledPage extends WirePage<WireProgressItem> {
  readonly server_time: string;
}

/** What one accepted batch produced. */
export interface SyncPushOutcome {
  /**
   * The server's own clock at the moment it processed the batch. Every
   * timestamp this client stores as a sync boundary comes from here rather
   * than from the device, because the boundary is compared against values the
   * server wrote.
   */
  readonly serverTime: string;
  readonly results: readonly ProgressSyncResult[];
}

/** One page of the pull direction. */
export interface SyncPullPage {
  readonly items: readonly ProgressEntry[];
  readonly page: number;
  readonly totalPages: number;
  readonly serverTime: string;
}

/**
 * The two progress-sync endpoints.
 *
 * They are not platform methods: each is one HTTP call, identical in both
 * builds, made by the layer that owns the session. The platform supplies the
 * rows and absorbs the answer; it does not carry them over the wire.
 */
@Injectable({ providedIn: 'root' })
export class SyncApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * Uploads one batch of local completion state.
   *
   * The caller is responsible for keeping a batch inside `SYNC_BATCH_LIMIT`.
   * A device may hold weeks of offline writes, and discovering the ceiling
   * from a `413` at runtime would mean the first sync after a long trip is
   * also the one that fails.
   */
  async push(items: readonly PendingProgress[]): Promise<SyncPushOutcome> {
    try {
      const response = await firstValueFrom(
        this.http.post<WireSyncResponse>(`${this.baseUrl}/sync/progress`, {
          items: items.map((item) => ({
            lesson_id: item.lessonId,
            // Null is the un-completed state, a real user action rather
            // than the absence of a value, and it has to survive the round
            // trip as such.
            completed_at: item.completedAt,
            client_updated_at: item.clientUpdatedAt,
          })),
        }),
      );
      return {
        serverTime: response.server_time,
        results: response.results.map(toSyncResult),
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  /**
   * Reads back what other devices recorded.
   *
   * `since` is an exclusive lower bound on the server's own `updated_at`, so
   * it is only ever a timestamp the server itself produced. Sending a value
   * from this device's clock would silently skip rows on a machine running
   * fast.
   */
  async pull(since: string | null, page: number): Promise<SyncPullPage> {
    let params = new HttpParams().set('page', page).set('size', SYNC_PAGE_SIZE);
    if (since !== null) {
      params = params.set('since', since);
    }
    try {
      const response = await firstValueFrom(
        this.http.get<WirePulledPage>(`${this.baseUrl}/sync/progress`, { params }),
      );
      return {
        page: response.page,
        totalPages: response.total_pages,
        serverTime: response.server_time,
        items: response.items.map((item) => ({
          lessonId: item.lesson_id,
          completedAt: item.completed_at,
          clientUpdatedAt: item.client_updated_at,
        })),
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}

/**
 * Maps one row of the server's answer.
 *
 * The completion key is deliberately never added. A result says what the
 * server now holds for `client_updated_at` and whether it took the row; it
 * carries no completion value, and inventing one — even `undefined` — would
 * be read as "explicitly marked incomplete" by a store that distinguishes an
 * absent key from a null one, quietly un-completing a lesson on every stale
 * row.
 */
function toSyncResult(wire: WireSyncResult): ProgressSyncResult {
  return {
    lessonId: wire.lesson_id,
    status: narrowStatus(wire.status),
    code: wire.code,
    serverClientUpdatedAt: wire.server_client_updated_at,
  };
}

function narrowStatus(value: string): ProgressSyncResult['status'] {
  // An unknown status is treated as a rejection rather than as an
  // application: the one thing that must not happen is a row being marked
  // acknowledged because a value nobody recognised was assumed to be good.
  return value === 'APPLIED' || value === 'STALE' ? value : 'REJECTED';
}
