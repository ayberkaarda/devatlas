import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { routes } from './app.routes';
import { AuthSession } from './core/auth/auth-session';
import { authInterceptor } from './core/auth/auth.interceptor';
import { connectivityInterceptor } from './core/net/connectivity.interceptor';
import { requestTimeoutInterceptor } from './core/net/request-timeout.interceptor';
import { localeHeaderInterceptor } from './core/platform/api';
import { providePlatform } from './core/platform/platform.providers';
import { BundledTranslateLoader } from './core/i18n/translations';
import { provideStartup } from './core/startup';
import { PreferenceSyncService } from './core/sync/preference-sync.service';
import { ProgressSyncService } from './core/sync/progress-sync.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Order is load-bearing, and the last two entries in particular.
    //
    // The connectivity interceptor sits below the auth interceptor so that
    // what it observes is the request that actually reached the network,
    // including the retry a token refresh produces.
    //
    // The request deadline sits below connectivity, innermost of all, so the
    // deadline expires inside the chain: a hung server then fails the
    // connectivity observer instead of silently cancelling it. Innermost also
    // means the deadline is per attempt, so a refreshed and retried request
    // gets a full allowance of its own rather than the remains of one.
    provideHttpClient(
      withInterceptors([
        localeHeaderInterceptor,
        authInterceptor,
        connectivityInterceptor,
        requestTimeoutInterceptor,
      ]),
    ),
    ...providePlatform(),
    // Picks a session back up without asking for a password, and deliberately
    // does not block startup while it tries: the attempt is a network round
    // trip, the interface is complete without it, and everything that depends
    // on a session reads a signal that updates when it lands.
    provideAppInitializer(() => {
      void inject(AuthSession).restore();
    }),
    // Creating the two sync services is what arms their triggers; neither
    // does any work here, and neither is allowed to delay startup. They are
    // constructed explicitly rather than left to whichever screen happens to
    // inject one first, so that synchronisation does not depend on a
    // particular view being on screen.
    provideAppInitializer(() => {
      inject(ProgressSyncService);
      inject(PreferenceSyncService);
    }),
    provideTranslateService({
      loader: BundledTranslateLoader,
      // English is the canonical locale on the content side too, so a missing
      // interface string and a missing translation fall back to the same
      // language rather than to two different ones.
      fallbackLang: 'en',
      lang: 'en',
    }),
    // Last, because it depends on everything above it being available.
    provideStartup(),
  ],
};
