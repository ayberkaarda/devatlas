import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { routes } from './app.routes';
import { localeHeaderInterceptor } from './core/platform/api';
import { providePlatform } from './core/platform/platform.providers';
import { BundledTranslateLoader } from './core/i18n/translations';
import { provideStartup } from './core/startup';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([localeHeaderInterceptor])),
    ...providePlatform(),
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
