import { DOCUMENT, Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

import { LOCALES, type Locale } from '../platform/models';
import { PreferenceWriter } from '../sync/preference-writer';
import { ActiveLocale } from './active-locale';

/**
 * Owns the active interface locale: the translation store, the `lang`
 * attribute on the document, the copy the HTTP layer reads, and the stored
 * preference.
 *
 * The switch is a runtime one and stays that way. No component copies a
 * translated string into a field, because a copy is a snapshot and a snapshot
 * does not change when the language does; templates read through the pipe so
 * that the change propagates on its own.
 */
@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly translate = inject(TranslateService);
  private readonly preferences = inject(PreferenceWriter);
  private readonly activeLocale = inject(ActiveLocale);
  private readonly document = inject(DOCUMENT);

  readonly available = LOCALES;

  /** The active locale, as a signal so templates can mark the current choice. */
  readonly current = this.activeLocale.value;

  /**
   * Applies a locale everywhere it is observable, then records the choice.
   *
   * The order matters: the interface changes first and the write follows, so a
   * storage failure produces a preference that did not travel rather than a
   * language switch that appeared not to work.
   */
  async use(locale: Locale, persist = true): Promise<void> {
    await firstValueFrom(this.translate.use(locale));
    this.activeLocale.set(locale);
    this.document.documentElement.setAttribute('lang', locale);
    if (persist) {
      await this.preferences.write({ locale });
    }
  }

  /**
   * Applies the stored locale at startup without writing it back.
   *
   * Re-persisting a value that was just read would mark the preference dirty
   * on every launch and push it to the server for no reason.
   */
  async initialize(locale: Locale): Promise<void> {
    this.translate.addLangs([...LOCALES]);
    this.translate.setFallbackLang('en');
    await this.use(locale, false);
  }
}
