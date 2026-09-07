import { Injectable, effect, inject, signal } from '@angular/core';

import { AuthSession } from '../auth/auth-session';
import { ConnectivityService } from '../net/connectivity.service';
import { PlatformError } from '../platform/errors';
import { PlatformService } from '../platform/platform.service';
import { SYNC_BATCH_LIMIT, SyncApiClient } from './sync-api.client';

/**
 * An upper bound on how many pages one pull will walk.
 *
 * At the maximum page size this is twenty thousand rows, far past any real
 * library. It exists so that a server answering with a page count it never
 * reaches cannot turn a sync into an endless loop of requests.
 */
const MAX_PULL_PAGES = 200;

/**
 * Drains the local progress queue upward and brings other devices' progress
 * down into the replica.
 *
 * It runs only where content and progress are stored locally. On the web
 * `markProgress` already *is* the sync — it posts a single-item batch and
 * returns once the server has it — so there is no queue to drain, and the
 * platform methods this service depends on throw there by design. Branching
 * on the capability rather than on the build is what keeps that fact out of
 * every caller.
 *
 * Failure here is ordinary, not exceptional. Being offline, holding an
 * expired credential and being rate-limited all end a cycle quietly, leave
 * the pending rows pending, and change nothing about reading a lesson or
 * recording a completion. Nothing in this file is on the path of either.
 */
@Injectable({ providedIn: 'root' })
export class ProgressSyncService {
  private readonly platform = inject(PlatformService);
  private readonly api = inject(SyncApiClient);
  private readonly session = inject(AuthSession);
  private readonly connectivity = inject(ConnectivityService);

  private readonly enabled = this.platform.capabilities.hasLocalStore;

  /** The cycle in flight, so a second trigger joins it instead of stacking. */
  private cycle: Promise<void> | null = null;

  private readonly lastAccepted = signal<string | null>(null);
  private readonly running = signal(false);

  /**
   * When a batch was last *accepted*, which is not when one was last
   * attempted. A failed sync must not let the interface claim otherwise.
   */
  readonly lastSyncAt = this.lastAccepted.asReadonly();

  /** True while a cycle is in flight, for a quiet indicator only. */
  readonly syncing = this.running.asReadonly();

  constructor() {
    if (!this.enabled) {
      return;
    }

    void this.platform
      .getSyncState()
      .then((state) => this.lastAccepted.set(state.lastSyncAt))
      .catch(() => undefined);

    /*
     * The two automatic triggers, expressed as one condition rather than as
     * two subscriptions: a cycle is worth starting when this device holds a
     * usable session and the server is reachable. Signals only notify on a
     * real change, so this fires once when the session lands at startup and
     * once on each transition to online — never in a loop, and never while a
     * cycle is already running, because `sync` joins the one in flight.
     */
    effect(() => {
      const eligible = this.session.userId() !== null && !this.session.localOnly();
      const reachable = this.connectivity.online();
      if (eligible && reachable) {
        void this.sync();
      }
    });
  }

  /**
   * Runs one cycle, or joins the one already running.
   *
   * The third trigger calls this directly: a completion the user just marked
   * is worth sending immediately. A completion marked *during* a cycle is not
   * chased with a second one — the row stays pending and travels with the
   * next trigger, which is what keeps a burst of completions from becoming a
   * burst of batches.
   */
  sync(): Promise<void> {
    if (!this.enabled) {
      return Promise.resolve();
    }
    this.cycle ??= this.runCycle().finally(() => {
      this.cycle = null;
    });
    return this.cycle;
  }

  private async runCycle(): Promise<void> {
    if (this.session.userId() === null || this.session.localOnly()) {
      return;
    }
    if (this.connectivity.offline()) {
      return;
    }

    this.running.set(true);
    try {
      const state = await this.platform.getSyncState();

      // Push before pull, within the cycle and deliberately. A local row the
      // server has not seen yet must not be compared against a server row
      // that predates it: pulling first would apply the older value locally
      // and the newer local value would then win the push, costing a round
      // trip to arrive where starting the other way round arrives directly.
      const pushedAt = await this.pushPending();
      const pulledAt = await this.pullSince(state.lastSyncAt);

      const accepted = earlier(pushedAt, pulledAt);
      if (accepted !== null) {
        await this.platform.setSyncState({ lastSyncAt: accepted });
        this.lastAccepted.set(accepted);
      }
    } catch (error) {
      this.noteFailure(error);
    } finally {
      this.running.set(false);
    }
  }

  /**
   * Sends what the store has not had acknowledged, in batches the server will
   * accept.
   *
   * The chunking is not an optimisation. A device can hold weeks of offline
   * writes, the server refuses more than five hundred items outright, and
   * discovering that from a `413` would mean the first sync after a long trip
   * is the one that fails.
   *
   * Returns the server's clock at the first accepted batch, or null when
   * there was nothing to send.
   */
  private async pushPending(): Promise<string | null> {
    const pending = await this.platform.pendingProgress();
    let firstServerTime: string | null = null;

    for (let start = 0; start < pending.length; start += SYNC_BATCH_LIMIT) {
      const outcome = await this.api.push(pending.slice(start, start + SYNC_BATCH_LIMIT));
      // Written back per batch rather than once at the end: a batch the
      // server accepted is acknowledged even if a later one fails, and
      // re-sending it would only produce a page of stale rows.
      await this.platform.applyProgressResults(outcome.results);
      firstServerTime ??= outcome.serverTime;
    }
    return firstServerTime;
  }

  /**
   * Reads back everything recorded since the last accepted boundary, page by
   * page.
   *
   * Paging is not optional. The response is a page whatever its size, and a
   * client that read the first one and stopped would silently drop the rest
   * of another device's history.
   *
   * Returns the server's clock at the first page.
   */
  private async pullSince(since: string | null): Promise<string | null> {
    let firstServerTime: string | null = null;

    for (let page = 0; page < MAX_PULL_PAGES; page += 1) {
      const answer = await this.api.pull(since, page);
      firstServerTime ??= answer.serverTime;
      if (answer.items.length > 0) {
        await this.platform.absorbProgress(answer.items);
      }
      if (page + 1 >= answer.totalPages) {
        break;
      }
    }
    return firstServerTime;
  }

  /**
   * Records what a failed cycle taught, which is almost never anything.
   *
   * The one exception is a request that never reached the server: that is
   * evidence about the network, and the indicator that reads it should not
   * wait for a browser event that may not come.
   */
  private noteFailure(error: unknown): void {
    if (error instanceof PlatformError && error.code === 'NETWORK_UNAVAILABLE') {
      this.connectivity.reportUnreachable();
    }
  }
}

/**
 * The earlier of two server timestamps, either of which may be absent.
 *
 * The earlier one is the safe boundary: a later boundary would skip rows the
 * server wrote between the two readings, while an earlier one costs at worst
 * a repeated pull, and absorbing a row twice changes nothing.
 *
 * They are compared as strings because the API defines one timestamp format —
 * UTC, three fractional digits, a literal `Z` — in which lexical order is
 * chronological order.
 */
function earlier(left: string | null, right: string | null): string | null {
  if (left === null) {
    return right;
  }
  if (right === null) {
    return left;
  }
  return left <= right ? left : right;
}
