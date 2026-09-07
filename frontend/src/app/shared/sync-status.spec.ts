import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAuthSession } from '../../testing/fake-auth-session';
import { FakePlatformService } from '../../testing/fake-platform.service';
import { AuthSession } from '../core/auth/auth-session';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { LocaleService } from '../core/i18n/locale.service';
import { ConnectivityService } from '../core/net/connectivity.service';
import { API_BASE_URL } from '../core/platform/api';
import { PlatformService } from '../core/platform/platform.service';
import { SyncStatus } from './sync-status';

describe('SyncStatus', () => {
  let platform: FakePlatformService;
  let session: FakeAuthSession;
  let connectivity: ConnectivityService;

  /**
   * The session is left signed out on purpose: the automatic sync triggers
   * wait on a session, and this is a test about what is rendered rather than
   * about what is sent.
   */
  async function configure(lastSyncAt: string | null = null, hasLocalStore = true): Promise<void> {
    TestBed.resetTestingModule();
    platform = new FakePlatformService();
    platform.capabilities = { canDownload: hasLocalStore, hasLocalStore };
    platform.syncState = { preferencesDirtyAt: null, lastSyncAt };
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
    connectivity = TestBed.inject(ConnectivityService);
  }

  async function render() {
    const fixture = TestBed.createComponent(SyncStatus);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => configure());

  it('renders nothing while there is nothing to report', async () => {
    const fixture = await render();

    // A status layer that is always visible is a banner, and a banner nobody
    // needs is noise on every screen.
    expect(fixture.nativeElement.querySelector('[data-testid="sync-status"]')).toBeNull();
  });

  it('reports being offline without blocking anything', async () => {
    connectivity.reportUnreachable();
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('[data-testid="sync-offline"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="sync-sign-in"]')).toBeNull();
  });

  it('offers a way back in when the credential was refused', async () => {
    session.localOnly.set(true);
    const fixture = await render();

    const link = fixture.nativeElement.querySelector(
      '[data-testid="sync-sign-in"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    // A link rather than a dialog: someone reading a lesson did not ask to
    // sign in, and local reading and progress both still work.
    expect(link.getAttribute('href')).toBe('/login');
  });

  it('shows when progress last reached the server', async () => {
    await configure('2026-09-04T09:52:18.006Z');
    const fixture = await render();

    const shown = fixture.nativeElement.querySelector(
      '[data-testid="sync-last-synchronised"]',
    ) as HTMLElement;
    expect(shown).not.toBeNull();
    // The assertion is that a formatted timestamp reached the row, not which
    // words surround it — those change with the catalogue.
    expect(shown.textContent).toContain('2026');
  });

  it('says nothing about synchronising where progress is not stored locally', async () => {
    // The recorded time is there to be read; the build simply has no queue
    // whose synchronisation it would describe.
    await configure('2026-09-04T09:52:18.006Z', false);
    const fixture = await render();

    // On the web a completion is written to the server as it is made, so
    // "last synchronised" would be a number with no meaning behind it.
    expect(
      fixture.nativeElement.querySelector('[data-testid="sync-last-synchronised"]'),
    ).toBeNull();
  });
});
