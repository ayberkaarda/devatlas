import { Provider } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AUTH_TOKEN_DELIVERY } from '../auth/auth-models';
import { API_BASE_URL } from './api';
import { PlatformService } from './platform.service';
import { TauriPlatformService } from './tauri-platform.service';
import { WebPlatformService } from './web-platform.service';

/**
 * Selection happens once, at build time, and nowhere else.
 *
 * Both implementations are compiled into both builds; only one is reachable.
 * That is why the typecheck gate covers every file under `src/` rather than
 * only what the entry point imports — in each build one implementation is dead
 * code, and a narrower check would let the unused half rot behind a green
 * gate.
 *
 * The refresh-token channel is decided here for the same reason and in the
 * same breath. A browser can keep the token where no script reaches it, and a
 * desktop webview cannot share a cookie jar with the API, so the two builds
 * differ — but that difference stops at this file. Nothing downstream asks
 * which build it is in; it asks the injector which channel it has.
 */
export function providePlatform(): Provider[] {
  return [
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    {
      provide: AUTH_TOKEN_DELIVERY,
      useValue: environment.platform === 'tauri' ? 'BODY' : 'COOKIE',
    },
    {
      provide: PlatformService,
      useClass: environment.platform === 'tauri' ? TauriPlatformService : WebPlatformService,
    },
  ];
}
