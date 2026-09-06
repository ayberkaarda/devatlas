import { Pipe, PipeTransform, inject } from '@angular/core';

import { ActiveLocale } from '../core/i18n/active-locale';

/**
 * Formats an ISO timestamp for the active interface locale.
 *
 * Angular's own `DatePipe` needs its target locale's data registered with
 * `registerLocaleData`, which this project does not do — adding it would
 * mean carrying `@angular/common/locales/*` bundles for four locales for a
 * feature only the admin screens use. `Intl.DateTimeFormat` reads locale data
 * the browser (and the Tauri webview's underlying engine) already ships, so
 * this pipe gets locale-correct formatting with no extra weight and no
 * registration step.
 *
 * Reads `ActiveLocale` directly rather than taking a locale as a pipe
 * argument: every admin timestamp is shown in the interface language, never
 * in the content locale, and a pipe argument at every call site would be one
 * more place to get that distinction wrong.
 */
@Pipe({ name: 'dateTime', pure: false })
export class DateTimePipe implements PipeTransform {
  private readonly activeLocale = inject(ActiveLocale);

  transform(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return new Intl.DateTimeFormat(this.activeLocale.value(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  }
}
