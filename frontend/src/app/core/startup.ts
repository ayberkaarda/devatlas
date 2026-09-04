import { EnvironmentProviders, inject, provideAppInitializer } from '@angular/core';

import { LocaleService } from './i18n/locale.service';
import type { Preferences } from './platform/models';
import { PlatformService } from './platform/platform.service';
import { ThemeService } from './theme/theme.service';

const FALLBACK_PREFERENCES: Preferences = { locale: 'en', theme: 'SYSTEM' };

/**
 * Reads the stored preferences, applies them, and only then lets the
 * application be seen.
 *
 * The reveal is in a `finally` on purpose. On the desktop the window is
 * created hidden, so anything that escapes this initializer without revealing
 * it leaves a process running with no window at all — a failure the user
 * cannot see, report, or recover from except by killing it. A wrong theme is
 * recoverable; an invisible application is not.
 */
export function provideStartup(): EnvironmentProviders {
  return provideAppInitializer(async () => {
    const platform = inject(PlatformService);
    const theme = inject(ThemeService);
    const locale = inject(LocaleService);

    try {
      let preferences = FALLBACK_PREFERENCES;
      try {
        preferences = await platform.getPreferences();
      } catch {
        // A preference store that cannot be read is not a reason to refuse to
        // start; English and the system palette are a working application.
      }
      theme.initialize(preferences.theme);
      await locale.initialize(preferences.locale);
    } finally {
      await platform.revealApplication().catch(() => undefined);
    }
  });
}
