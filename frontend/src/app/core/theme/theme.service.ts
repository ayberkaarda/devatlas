import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

import type { ResolvedTheme, ThemePreference } from '../platform/models';
import { PreferenceWriter } from '../sync/preference-writer';

/**
 * Owns the theme: the preference, what it resolves to, and the attribute the
 * token layer reacts to.
 *
 * Three preference values and two outcomes. `SYSTEM` is not a third palette,
 * it is a deferral, and keeping it distinct from the resolved value is what
 * lets a machine set to follow the system actually follow it when the system
 * changes — a two-value model would have to guess at that moment whether the
 * user had chosen dark or merely inherited it.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly preferences = inject(PreferenceWriter);
  private readonly document = inject(DOCUMENT);

  private readonly systemPrefersDark = signal(false);
  private readonly preferenceState = signal<ThemePreference>('SYSTEM');

  /** What the user asked for, which may be "whatever the system says". */
  readonly preference = this.preferenceState.asReadonly();

  /** What that resolves to right now. This is what is on the document. */
  readonly resolved = computed<ResolvedTheme>(() => {
    const preference = this.preferenceState();
    if (preference === 'LIGHT' || preference === 'DARK') {
      return preference === 'DARK' ? 'dark' : 'light';
    }
    return this.systemPrefersDark() ? 'dark' : 'light';
  });

  private query: MediaQueryList | null = null;

  /**
   * Applies a stored preference without writing it back, and starts following
   * the system setting.
   */
  initialize(preference: ThemePreference): void {
    this.watchSystem();
    this.preferenceState.set(preference);
    this.apply();
  }

  /**
   * Records a preference and applies it immediately.
   *
   * The device is authoritative: the change is applied first and stored
   * second, and nothing ever reads a value back over it. A theme that flips
   * under the user's eyes because a remote copy disagreed is worse than a
   * preference that failed to travel.
   *
   * The write goes through the preference writer rather than straight to the
   * platform, so the change is recorded as outstanding and travels to the
   * account on its own once there is a session and a route.
   */
  async set(preference: ThemePreference): Promise<void> {
    this.preferenceState.set(preference);
    this.apply();
    await this.preferences.write({ theme: preference });
  }

  /**
   * The toggle a single button drives: it moves between the two explicit
   * values, starting from whatever is on screen. Cycling through `SYSTEM` in a
   * two-state control would give one press in three no visible effect.
   */
  toggle(): Promise<void> {
    return this.set(this.resolved() === 'dark' ? 'LIGHT' : 'DARK');
  }

  private apply(): void {
    this.document.documentElement.setAttribute('data-theme', this.resolved());
  }

  private watchSystem(): void {
    if (this.query) {
      return;
    }
    // Guarded because the media query API is absent in some test and embedded
    // environments, and a theme service that throws would take the whole
    // startup sequence with it.
    const view = this.document.defaultView;
    if (!view || typeof view.matchMedia !== 'function') {
      return;
    }
    this.query = view.matchMedia('(prefers-color-scheme: dark)');
    this.systemPrefersDark.set(this.query.matches);
    this.query.addEventListener('change', (event) => {
      this.systemPrefersDark.set(event.matches);
      this.apply();
    });
  }
}
