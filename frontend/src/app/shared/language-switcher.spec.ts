import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../testing/fake-platform.service';
import { LocaleService } from '../core/i18n/locale.service';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { PlatformService } from '../core/platform/platform.service';
import { LanguageSwitcher } from './language-switcher';

describe('LanguageSwitcher', () => {
  let platform: FakePlatformService;

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
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function render() {
    const fixture = TestBed.createComponent(LanguageSwitcher);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('offers all four locales, each under its own name', async () => {
    const fixture = await render();
    const options = Array.from(
      fixture.nativeElement.querySelectorAll('option'),
    ) as HTMLOptionElement[];

    expect(options.map((option) => option.value)).toEqual(['en', 'tr', 'fr', 'de']);
    // Named in their own language: a reader looking for their language looks
    // for its own name, not for the current language's word for it.
    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'English',
      'Türkçe',
      'Français',
      'Deutsch',
    ]);
  });

  it('carries an accessible label', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.getAttribute('aria-label')).toBe('Language');
  });

  it('switches the option labels of the surrounding interface at runtime', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;

    select.value = 'de';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();

    // The same component instance, re-rendered from the translation store: no
    // reload, and no string was ever copied into a component field.
    expect(select.getAttribute('aria-label')).toBe('Sprache');
    expect(TestBed.inject(LocaleService).current()).toBe('de');
  });

  it('records the choice through the platform service', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;

    select.value = 'tr';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(platform.preferenceWrites).toEqual([{ locale: 'tr' }]);
  });
});
