import { Injectable, effect, inject } from '@angular/core';

import { AccountApiClient } from '../auth/account-api.client';
import { AuthSession } from '../auth/auth-session';
import { ConnectivityService } from '../net/connectivity.service';
import { PlatformError } from '../platform/errors';
import { PlatformService } from '../platform/platform.service';
import { PreferenceWriter } from './preference-writer';

/**
 * Carries an outstanding preference change up to the server, and never the
 * other way.
 *
 * Theme and locale live on the device and also on the server profile, and
 * framing that as a conflict leads to synchronisation machinery nobody needs.
 * The device is authoritative: a change is applied locally and recorded, and
 * the push is an attempt that may fail. A preference that failed to travel is
 * a small loss; a theme that flips under someone's eyes because a remote copy
 * disagreed is a bigger one, so nothing here reads a server value back over a
 * local one.
 *
 * Several changes made with no connectivity collapse into one push, because
 * what is recorded is a flag rather than a queue and what is sent is the
 * final state rather than the steps that produced it.
 */
@Injectable({ providedIn: 'root' })
export class PreferenceSyncService {
  private readonly platform = inject(PlatformService);
  private readonly account = inject(AccountApiClient);
  private readonly session = inject(AuthSession);
  private readonly connectivity = inject(ConnectivityService);
  private readonly writer = inject(PreferenceWriter);

  /** The push in flight, so two triggers produce one request. */
  private pushing: Promise<void> | null = null;

  constructor() {
    void this.writer.restore();

    /*
     * One condition rather than three subscriptions: there is something to
     * send, this device holds a usable session, and the server is reachable.
     * Signals notify only on a real change, so this fires when a preference
     * is changed, when a session lands, and on each transition to online —
     * never in a loop.
     */
    effect(() => {
      const outstanding = this.writer.dirtyAt() !== null;
      const eligible = this.session.userId() !== null && !this.session.localOnly();
      const reachable = this.connectivity.online();
      if (outstanding && eligible && reachable) {
        void this.flush();
      }
    });
  }

  /**
   * Sends the current preference state, if there is a change outstanding.
   *
   * A device with nothing recorded makes no request, which is what makes this
   * cheap enough to call on every reconnection.
   */
  flush(): Promise<void> {
    this.pushing ??= this.attemptPush().finally(() => {
      this.pushing = null;
    });
    return this.pushing;
  }

  private async attemptPush(): Promise<void> {
    if (this.writer.dirtyAt() === null) {
      return;
    }
    if (this.session.userId() === null || this.session.localOnly()) {
      // Nobody to push to. The record stays, so the next sign-in carries the
      // change up — the seeding model seen from the write side.
      return;
    }
    if (this.connectivity.offline()) {
      return;
    }

    try {
      // Read back rather than replaying the patch: the server is told the
      // final state, so a run of offline changes arrives as one value.
      const current = await this.platform.getPreferences();
      await this.account.updatePreferences(current);
      await this.writer.markPushed();
    } catch (error) {
      if (error instanceof PlatformError && error.code === 'NETWORK_UNAVAILABLE') {
        this.connectivity.reportUnreachable();
      }
      // The record stays outstanding. Failing to push is ordinary, and the
      // next reconnection or session change tries again.
    }
  }
}
