import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakeAuthSession } from '../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../testing/fake-platform.service';
import { AuthSession } from '../../core/auth/auth-session';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { API_BASE_URL } from '../../core/platform/api';
import { PlatformService } from '../../core/platform/platform.service';
import { ThemeService } from '../../core/theme/theme.service';
import { SettingsPage } from './settings.page';

describe('SettingsPage', () => {
  let platform: FakePlatformService;
  let session: FakeAuthSession;

  beforeEach(async () => {
    platform = new FakePlatformService();
    session = new FakeAuthSession();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/api/v1' },
        { provide: PlatformService, useValue: platform },
        { provide: AuthSession, useValue: session },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
    TestBed.inject(ThemeService).initialize('LIGHT');
  });

  async function render() {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('gives the three once-a-month preferences one home', async () => {
    const fixture = await render();

    // Asserted by structure rather than by heading text: the wording moves
    // with the catalogue, the sections do not.
    expect(fixture.nativeElement.querySelector('[data-testid="settings-language"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="settings-theme"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="settings-account"]')).not.toBeNull();
  });

  it('names the page from the catalogue rather than from a literal', async () => {
    const fixture = await render();
    const heading = fixture.nativeElement.querySelector('h1') as HTMLElement;

    // A missing key renders as the key itself, and a lookup of that key would
    // return the same string — so the catalogue value is checked for not
    // being the key before it is compared against the screen.
    const expected = TestBed.inject(TranslateService).instant('settings.heading');
    expect(expected).not.toBe('settings.heading');
    expect(heading.textContent).toContain(expected);
  });

  it('carries the real controls rather than copies of them', async () => {
    const fixture = await render();

    // The page owns no preference logic: each control already reads and
    // writes the thing it names, and a second implementation here would be a
    // second answer to the same question.
    expect(fixture.nativeElement.querySelector('app-language-switcher')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-theme-toggle')).not.toBeNull();
  });

  it('keeps the language control a native select with the shared look', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;

    expect(select).not.toBeNull();
    // The shared rule is what stops it drifting from the buttons beside it;
    // its keyboard and screen reader behaviour is why it is still a select.
    expect(select.classList).toContain('bl-select');
  });

  it('offers a way in while nobody is signed in', async () => {
    const fixture = await render();

    // Reading and downloading need no account, so an anonymous visitor is in
    // a complete state: the section offers a way in rather than reporting
    // something missing.
    expect(fixture.nativeElement.querySelector('app-session-menu button')).toBeNull();
    const signIn = fixture.nativeElement.querySelector(
      '[data-testid="settings-sign-in"]',
    ) as HTMLAnchorElement;
    expect(signIn.getAttribute('href')).toBe('/login');
  });

  it('shows who is signed in, and the way out', async () => {
    session.setRole('ADMIN');
    const fixture = await render();

    const menu = fixture.nativeElement.querySelector('app-session-menu') as HTMLElement;
    expect(menu.querySelector('span')).not.toBeNull();
    const signOut = menu.querySelector('button') as HTMLButtonElement;
    expect(signOut).not.toBeNull();

    signOut.click();
    await fixture.whenStable();
    expect(session.signOuts).toBe(1);
  });
});
