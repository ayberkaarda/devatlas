import { TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LOCALES } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
import { ActiveLocale } from './active-locale';
import { LocaleService } from './locale.service';
import { BundledTranslateLoader } from './translations';

describe('LocaleService', () => {
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
    expect(translate.instant('nav.tracks')).toBe('Tracks');

    await locale.use('tr');
    expect(translate.instant('nav.tracks')).toBe('Yollar');

    await locale.use('de');
    expect(translate.instant('nav.tracks')).toBe('Lernpfade');
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
    const loader = TestBed.inject(BundledTranslateLoader);
    const bundle = await new Promise((resolve) => loader.getTranslation('kl').subscribe(resolve));
    expect((bundle as { nav: { tracks: string } }).nav.tracks).toBe('Tracks');
  });
});
