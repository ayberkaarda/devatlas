import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAuthSession } from '../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../testing/fake-platform.service';
import { AuthSession } from '../../core/auth/auth-session';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import { PlatformService } from '../../core/platform/platform.service';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let session: FakeAuthSession;
  let navigations: string[];

  beforeEach(async () => {
    session = new FakeAuthSession();
    navigations = [];

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PlatformService, useValue: new FakePlatformService() },
        { provide: AuthSession, useValue: session },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
    jest.spyOn(TestBed.inject(Router), 'navigateByUrl').mockImplementation((url) => {
      navigations.push(String(url));
      return Promise.resolve(true);
    });
  });

  async function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl !== undefined) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  function fill(fixture: Awaited<ReturnType<typeof render>>, email: string, password: string) {
    const element = fixture.nativeElement as HTMLElement;
    for (const [id, value] of [
      ['login-email', email],
      ['login-password', password],
    ]) {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  async function submit(fixture: Awaited<ReturnType<typeof render>>) {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders its labels from the catalogue rather than from literals', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent?.trim()).toBe('Sign in');
    expect(element.querySelector('label[for="login-email"]')?.textContent?.trim()).toBe('Email');
    expect(element.querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Sign in');
  });

  it('says which field is missing instead of sending an empty attempt', async () => {
    const fixture = await render();
    await submit(fixture);

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#login-email-error')?.textContent?.trim()).toBe(
      'Please enter your email address.',
    );
    expect(element.querySelector('#login-password-error')?.textContent?.trim()).toBe(
      'Please enter your password.',
    );
    expect(session.signIns).toEqual([]);
  });

  it('signs in and resumes the trip the guard interrupted', async () => {
    const fixture = await render('/admin/review/42');
    fill(fixture, '  eda@example.com  ', 'kayseri-uzun-parola-2026');
    await submit(fixture);

    expect(session.signIns).toEqual([
      { email: 'eda@example.com', password: 'kayseri-uzun-parola-2026' },
    ]);
    expect(navigations).toEqual(['/admin/review/42']);
  });

  it('refuses to be turned into a redirector to somebody else', async () => {
    const fixture = await render('https://evil.test/harvest');
    fill(fixture, 'eda@example.com', 'pw');
    await submit(fixture);

    // The target arrives as a query parameter, so anyone can put anything in
    // it. Only a path on this application is followed.
    expect(navigations).toEqual(['/']);
  });

  it('refuses the protocol-relative form that looks like a path and is not', async () => {
    const fixture = await render('//evil.test/harvest');
    fill(fixture, 'eda@example.com', 'pw');
    await submit(fixture);

    expect(navigations).toEqual(['/']);
  });

  it('explains a refused sign-in through the error catalogue, never the server text', async () => {
    jest
      .spyOn(session, 'signIn')
      .mockRejectedValue(new PlatformError('INVALID_CREDENTIALS', 'Bad password for user 41.'));

    const fixture = await render();
    fill(fixture, 'eda@example.com', 'wrong');
    await submit(fixture);

    const alert = (fixture.nativeElement as HTMLElement).querySelector(
      'form div[role="alert"]',
    ) as HTMLElement;
    expect(alert.textContent).toContain('The email or password is incorrect.');
    expect(alert.textContent).not.toContain('Bad password for user 41.');
    expect(navigations).toEqual([]);
  });
});
