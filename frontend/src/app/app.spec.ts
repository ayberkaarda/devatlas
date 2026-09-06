import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakeAuthSession } from '../testing/fake-auth-session';
import { FakePlatformService } from '../testing/fake-platform.service';
import { environment } from '../environments/environment';
import { App } from './app';
import { AuthSession } from './core/auth/auth-session';
import { LocaleService } from './core/i18n/locale.service';
import { BundledTranslateLoader } from './core/i18n/translations';
import { PlatformService } from './core/platform/platform.service';
import { ThemeService } from './core/theme/theme.service';

describe('App', () => {
  let platform: FakePlatformService;
  let session: FakeAuthSession;

  beforeEach(async () => {
    platform = new FakePlatformService();
    session = new FakeAuthSession();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
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
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('creates the root component', async () => {
    const fixture = await render();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders navigation from translation keys rather than literals', async () => {
    const fixture = await render();
    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;
    expect(nav.getAttribute('aria-label')).toBe('Main navigation');
    // Taken from the catalogue, not written here: the subject is that the
    // navigation renders a translated value rather than a literal, which a
    // hard-coded word would keep asserting long after the wording moved on.
    // The second expectation is what keeps the first honest -- a missing key
    // renders as the key itself, and the catalogue lookup would return that
    // same string, so the two would agree while the screen showed nothing a
    // reader could use.
    const label = TestBed.inject(TranslateService).instant('nav.tracks');
    expect(label).not.toBe('nav.tracks');
    expect(nav.textContent).toContain(label);
  });

  it('re-renders the whole shell when the language changes', async () => {
    const fixture = await render();
    await TestBed.inject(LocaleService).use('tr');
    fixture.detectChanges();
    await fixture.whenStable();

    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;
    expect(nav.getAttribute('aria-label')).toBe('Ana gezinme');
    expect(nav.textContent).toContain(TestBed.inject(TranslateService).instant('nav.tracks'));
  });

  it('offers a skip link ahead of the header', async () => {
    const fixture = await render();
    const skip = fixture.nativeElement.querySelector('.skip-link') as HTMLAnchorElement;
    expect(skip.getAttribute('href')).toBe('#main-content');
    expect(fixture.nativeElement.querySelector('#main-content')).not.toBeNull();
  });

  it('runs under the web target, which is the environment Jest resolves', () => {
    // The desktop environment file is only ever substituted by a build, so
    // this asserts the default rather than the replacement. It guards against
    // the platform flag being dropped or hard-coded.
    expect(environment.platform).toBe('web');
  });

  it('offers no session control while nobody is signed in', async () => {
    const fixture = await render();
    // Reading and downloading need no account, so an anonymous visitor is in a
    // complete state and the header says nothing about a session.
    expect(fixture.nativeElement.querySelector('app-session-menu button')).toBeNull();
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
