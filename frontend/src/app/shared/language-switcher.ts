import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { LocaleService } from '../core/i18n/locale.service';
import type { Locale } from '../core/platform/models';

/**
 * Switches the interface language at runtime.
 *
 * Nothing is reloaded and nothing is re-rendered by hand: every string in the
 * application is read through the translation pipe, so changing the active
 * language is the only thing this control has to do.
 *
 * The language names are deliberately not translated — a reader looking for
 * their own language looks for its own name, not for the current language's
 * word for it.
 */
@Component({
  selector: 'app-language-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <label class="sr-only" for="language-switcher">{{ 'language.label' | translate }}</label>
    <select
      id="language-switcher"
      class="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text"
      [attr.aria-label]="'language.label' | translate"
      [value]="locale.current()"
      (change)="select($event)"
    >
      @for (option of locale.available; track option) {
        <option [value]="option" [selected]="option === locale.current()">
          {{ 'language.' + option | translate }}
        </option>
      }
    </select>
  `,
})
export class LanguageSwitcher {
  protected readonly locale = inject(LocaleService);

  protected select(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as Locale;
    void this.locale.use(value);
  }
}
