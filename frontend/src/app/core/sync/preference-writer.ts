import { Injectable, inject, signal } from '@angular/core';

import type { Preferences } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { isoNow } from '../platform/timestamps';

/**
 * The one way a preference is changed, and the record that it has not
 * travelled yet.
 *
 * It writes locally and stamps, in that order, and it never talks to a
 * server. That separation is deliberate: the services that own the theme and
 * the locale are on the startup path and must not drag an HTTP client into
 * their construction, while the thing that pushes the change needs a session
 * and a network. Splitting them leaves one small class either side of the
 * line, and a signal between them.
 *
 * Stamping unconditionally — rather than only when offline — is what makes
 * the record survive the application closing between the local write and the
 * push. A stamp that is cleared a moment later by a successful push costs one
 * small write; a change that was never recorded because the push was assumed
 * to be about to succeed is simply lost.
 */
@Injectable({ providedIn: 'root' })
export class PreferenceWriter {
  private readonly platform = inject(PlatformService);

  private readonly dirty = signal<string | null>(null);

  /**
   * When this device last changed a preference the server has not been told
   * about, or null when there is nothing outstanding.
   */
  readonly dirtyAt = this.dirty.asReadonly();

  /**
   * Applies a preference change on the device and records it as outstanding.
   *
   * The local write is never conditional on anything else: this is what makes
   * a preference set with no network behave like one set with a network,
   * minus the travel.
   */
  async write(patch: Partial<Preferences>): Promise<void> {
    await this.platform.setPreferences(patch);
    const stamp = isoNow();
    await this.stamp(stamp);
    this.dirty.set(stamp);
  }

  /** Reads the stamp an earlier run left behind, so it still travels. */
  async restore(): Promise<void> {
    try {
      const state = await this.platform.getSyncState();
      this.dirty.set(state.preferencesDirtyAt);
    } catch {
      // Unreadable bookkeeping means nothing known to be outstanding. The
      // next change stamps again.
    }
  }

  /** Clears the record, once the server holds the final state. */
  async markPushed(): Promise<void> {
    try {
      await this.platform.setSyncState({ preferencesDirtyAt: null });
    } catch {
      // A stamp that could not be cleared costs one redundant push later,
      // which is the harmless direction to fail in.
    }
    this.dirty.set(null);
  }

  private async stamp(value: string): Promise<void> {
    try {
      await this.platform.setSyncState({ preferencesDirtyAt: value });
    } catch {
      // A device that cannot record the stamp still applied the preference,
      // and still holds it in memory for this run.
    }
  }
}
