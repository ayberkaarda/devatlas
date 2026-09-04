import { Provider } from '@angular/core';

import { environment } from '../../../environments/environment';
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
 */
export function providePlatform(): Provider[] {
  return [
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    {
      provide: PlatformService,
      useClass: environment.platform === 'tauri' ? TauriPlatformService : WebPlatformService,
    },
  ];
}
