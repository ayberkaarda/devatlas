import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API_BASE_URL, toPlatformError } from '../platform/api';
import type { Preferences } from '../platform/models';

/**
 * The signed-in account's own resource, which in this application means its
 * two preferences and nothing else.
 *
 * It is a plain HTTP client rather than a platform method because both builds
 * would issue the same request with the same headers and read the same
 * response: an abstraction over it would be one body of code written twice
 * and a third place to keep in step.
 *
 * The update deliberately returns nothing. The server answers with the stored
 * account, and the device is the authority on its own preferences — a caller
 * handed that body might apply it back over the local value, and a theme that
 * flips under someone's eyes because a remote copy disagreed is worse than a
 * preference that failed to travel. Returning `void` makes that mistake
 * unavailable rather than merely discouraged.
 */
@Injectable({ providedIn: 'root' })
export class AccountApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async updatePreferences(preferences: Preferences): Promise<void> {
    try {
      await firstValueFrom(
        this.http.patch<unknown>(`${this.baseUrl}/auth/me`, {
          locale: preferences.locale,
          theme: preferences.theme,
        }),
      );
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}
