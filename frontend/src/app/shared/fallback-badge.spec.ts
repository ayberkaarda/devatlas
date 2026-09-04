import { ComponentRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

import { BundledTranslateLoader } from '../core/i18n/translations';
import type { TranslationState } from '../core/platform/models';
import { FallbackBadge } from './fallback-badge';

function state(overrides: Partial<TranslationState> = {}): TranslationState {
  return { locale: 'en', requestedLocale: 'tr', isFallback: true, ...overrides };
}

describe('FallbackBadge', () => {
  let translate: TranslateService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    translate = TestBed.inject(TranslateService);
    await firstValueFrom(translate.use('en'));
  });

  async function render(value: TranslationState) {
    const fixture = TestBed.createComponent(FallbackBadge);
    (fixture.componentRef as ComponentRef<FallbackBadge>).setInput('state', value);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('marks text that fell back to the canonical locale', async () => {
    const fixture = await render(state());
    expect(fixture.nativeElement.textContent).toContain('Not yet translated');
  });

  it('renders nothing when the requested locale was available', async () => {
    const fixture = await render(state({ locale: 'tr', isFallback: false }));
    expect(fixture.nativeElement.querySelector('span')).toBeNull();
  });

  it('renders the badge in the active interface language', async () => {
    const fixture = await render(state());
    await firstValueFrom(translate.use('tr'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Henüz çevrilmedi');
  });

  it('explains itself through a translated title attribute', async () => {
    const fixture = await render(state());
    const badge = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(badge.getAttribute('title')).toBe(
      'Shown in English because no translation exists for this locale yet.',
    );
  });
});
