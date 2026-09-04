import { Injectable } from '@angular/core';
import { TranslateLoader, type TranslationObject } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';

import type { Locale } from '../platform/models';

import de from '../../../assets/i18n/de.json';
import en from '../../../assets/i18n/en.json';
import fr from '../../../assets/i18n/fr.json';
import tr from '../../../assets/i18n/tr.json';

/**
 * The four locale bundles, compiled into the application rather than fetched.
 *
 * A loader that fetches would put a network round trip in front of the first
 * rendered string, which the desktop client — whose entire premise is that it
 * works with no connection — cannot rely on. Four small bundles cost less than
 * the machinery needed to make a fetched loader correct offline, and switching
 * language at runtime becomes synchronous rather than a request that can fail
 * halfway through a page.
 */
const BUNDLES: Record<Locale, TranslationObject> = {
  en: en as TranslationObject,
  tr: tr as TranslationObject,
  fr: fr as TranslationObject,
  de: de as TranslationObject,
};

@Injectable({ providedIn: 'root' })
export class BundledTranslateLoader extends TranslateLoader {
  getTranslation(lang: string): Observable<TranslationObject> {
    return of(BUNDLES[lang as Locale] ?? BUNDLES.en);
  }
}
