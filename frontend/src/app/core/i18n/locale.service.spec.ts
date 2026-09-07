import { TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LOCALES } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { ActiveLocale } from './active-locale';
import { LocaleService } from './locale.service';
import { BundledTranslateLoader } from './translations';

describe('LocaleService', () => {
  /**
   * Reads a bundle straight from the loader, so an expectation can name a key
   * instead of the words behind it.
   */
  function bundleOf(): (tag: string) => Promise<{ nav: { tracks: string } }> {
    const loader = TestBed.inject(BundledTranslateLoader);
    return (tag) =>
      new Promise((resolve) =>
        loader
          .getTranslation(tag)
          .subscribe((value) => resolve(value as { nav: { tracks: string } })),
      );
  }

  let platform: FakePlatformService;
  let locale: LocaleService;
  let translate: TranslateService;

  beforeEach(async () => {
    platform = new FakePlatformService();
    TestBed.configureTestingModule({
      providers: [
        { provide: PlatformService, useValue: platform },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    locale = TestBed.inject(LocaleService);
    translate = TestBed.inject(TranslateService);
    await locale.initialize('en');
  });

  it('offers exactly the four supported locales', () => {
    expect([...locale.available]).toEqual([...LOCALES]);
  });

  it('does not write the stored locale back at startup', () => {
    // Re-persisting a value that was just read would mark the preference dirty
    // on every launch and push it for no reason.
    expect(platform.preferenceWrites).toEqual([]);
    expect(locale.current()).toBe('en');
  });

  it('switches the translated text at runtime, with no reload', async () => {
    // Compared against the bundles rather than against words written here.
    // The subject is that changing the language swaps which bundle answers a
    // key, which stays true when the wording is edited; an assertion naming
    // the English and Turkish text would fail on a copy change and say
    // nothing about the switching itself.
    const bundle = bundleOf();

    expect(translate.instant('nav.tracks')).toBe((await bundle('en')).nav.tracks);

    await locale.use('tr');
    expect(translate.instant('nav.tracks')).toBe((await bundle('tr')).nav.tracks);

    await locale.use('de');
    expect(translate.instant('nav.tracks')).toBe((await bundle('de')).nav.tracks);

    // And the three are genuinely different bundles, not one answering thrice.
    const [en, tr, de] = await Promise.all([bundle('en'), bundle('tr'), bundle('de')]);
    expect(new Set([en.nav.tracks, tr.nav.tracks, de.nav.tracks]).size).toBe(3);
  });

  it('records the choice and publishes it to the HTTP layer and the document', async () => {
    await locale.use('fr');

    expect(platform.preferenceWrites).toEqual([{ locale: 'fr' }]);
    expect(TestBed.inject(ActiveLocale).value()).toBe('fr');
    expect(document.documentElement.getAttribute('lang')).toBe('fr');
  });

  it('interpolates parameters rather than freezing a formatted string', async () => {
    await locale.use('tr');
    expect(translate.instant('tracks.lessonCount', { count: 28 })).toBe('28 ders');
  });

  it('falls back to English for a locale with no bundle', async () => {
    const bundle = bundleOf();
    expect((await bundle('kl')).nav.tracks).toBe((await bundle('en')).nav.tracks);
  });
});
