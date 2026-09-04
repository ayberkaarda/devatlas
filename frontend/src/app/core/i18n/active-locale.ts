import { Injectable, signal } from '@angular/core';

import type { Locale } from '../platform/models';

/**
 * The active interface locale, held in a service with no dependencies of its
 * own.
 *
 * It exists so that the HTTP layer can read the locale without depending on
 * the translation service, which depends on the platform service, which
 * depends on the HTTP layer. Splitting the value out of that cycle costs one
 * small class and removes a class of first-request construction failures.
 */
@Injectable({ providedIn: 'root' })
export class ActiveLocale {
  private readonly current = signal<Locale>('en');

  readonly value = this.current.asReadonly();

  set(locale: Locale): void {
    this.current.set(locale);
  }
}
