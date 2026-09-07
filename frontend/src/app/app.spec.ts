import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakeAuthSession } from '../testing/fake-auth-session';
import { FakePlatformService } from '../testing/fake-platform.service';
import { environment } from '../environments/environment';
import { App } from './app';
import { AuthSession } from './core/auth/auth-session';
import { LocaleService } from './core/i18n/locale.service';
import { BundledTranslateLoader } from './core/i18n/translations';
import { ConnectivityService } from './core/net/connectivity.service';
import { API_BASE_URL } from './core/platform/api';
import type { QueueEntry, QueueState } from './core/platform/models';
import { PlatformService } from './core/platform/platform.service';
import { ThemeService } from './core/theme/theme.service';

/**
 * A platform whose download queue a test can fill, and that records being
 * asked for it.
 *
 * The shared fake answers with an empty queue because nothing else asserts on
 * one; the shell's count badge is the first thing that does, and how many
 * times the queue was read is itself the subject of a test here — a badge fed
 * only by the progress stream would never show a batch that started before
 * the shell existed.
 */
class QueuedPlatformService extends FakePlatformService {
  queue: QueueEntry[] = [];
  queueReads = 0;

  override async queueState(): Promise<QueueEntry[]> {
    this.queueReads += 1;
    return this.queue;
  }
}

/** A queue row with only the fields the count and the badge look at. */
function queueEntry(entityId: string, state: QueueState): QueueEntry {
  return {
    entityId,
    entityType: 'LESSON',
    title: null,
    batchId: 'batch',
    state,
    receivedBytes: 0,
    totalBytes: 0,
    attempt: 1,
    pauseReason: null,
    errorCode: null,
    locales: [],
    trackId: null,
    trackTitle: null,
  };
}

describe('App', () => {
  let platform: QueuedPlatformService;
  let session: FakeAuthSession;

  beforeEach(async () => {
    platform = new QueuedPlatformService();
    session = new FakeAuthSession();
    TestBed.configureTestingModule({
      providers: [
        // Two matchable routes so router-link-active, and the aria-current it
        // drives, have something real to resolve against.
        provideRouter([
          { path: 'tracks', children: [] },
          { path: 'blog', children: [] },
        ]),
        // The shell's status strip reads the progress sync, which owns an
        // HTTP client of its own. Nothing here issues a request; the backend
        // is present so the graph can be built.
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

  it('marks the navigation entry for the screen being shown', async () => {
    const router = TestBed.inject(Router);
    const fixture = await render();

    await router.navigateByUrl('/blog');
    fixture.detectChanges();
    await fixture.whenStable();

    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;
    const current = nav.querySelectorAll('[aria-current="page"]');
    // Exactly one, and the one whose screen is on display. Marking the active
    // entry with colour alone tells a screen reader nothing about where in the
    // navigation its user currently is.
    expect(current.length).toBe(1);
    expect(current[0].getAttribute('href')).toBe('/blog');
  });

  it('names the theme control with the word a person can see on it', async () => {
    const fixture = await render();
    const toggle = fixture.nativeElement.querySelector(
      '[data-testid="theme-toggle"]',
    ) as HTMLButtonElement;

    // Voice control matches what is on screen, so the accessible name has to
    // contain the visible word rather than replace it. Taken from the
    // catalogue rather than written here, because the wording may move.
    const visible = TestBed.inject(TranslateService).instant('theme.light');
    expect(visible).not.toBe('theme.light');
    expect(toggle.getAttribute('aria-label')).toBeNull();
    expect(toggle.textContent).toContain(visible);
    expect(toggle.getAttribute('aria-pressed')).toBe('false');
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

  it('carries the status strip in the footer status bar', async () => {
    const fixture = await render();

    // It renders nothing of its own here — there is nothing to report — so
    // this asserts the mounting point rather than any particular state; what
    // each state looks like belongs to the strip's own tests.
    const strip = fixture.nativeElement.querySelector('app-sync-status');
    expect(strip).not.toBeNull();
    expect(strip.parentElement?.tagName).toBe('FOOTER');
  });

  it('keeps the settings destination out of the way but reachable', async () => {
    const fixture = await render();

    const settings = fixture.nativeElement.querySelector(
      '[data-testid="nav-settings"]',
    ) as HTMLAnchorElement;
    expect(settings.getAttribute('href')).toBe('/settings');

    // The controls a person sets once are on that screen, not on this one.
    // Their absence from the header is the point of the screen existing.
    expect(fixture.nativeElement.querySelector('app-language-switcher')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-session-menu')).toBeNull();
  });

  it('keeps the blog reachable, and marks it rather than removing it, while offline', async () => {
    const connectivity = TestBed.inject(ConnectivityService);
    const fixture = await render();
    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;

    const blogLink = Array.from(nav.querySelectorAll('a')).find(
      (link) => link.getAttribute('href') === '/blog',
    );
    expect(blogLink).toBeDefined();
    expect(nav.querySelector('[data-testid="nav-offline"]')).toBeNull();

    connectivity.reportUnreachable();
    fixture.detectChanges();

    // The entry stays: a menu item that disappears is a question, and the
    // screen behind this one answers for itself.
    expect(
      Array.from(nav.querySelectorAll('a')).find((link) => link.getAttribute('href') === '/blog'),
    ).toBeDefined();
    expect(nav.querySelector('[data-testid="nav-offline"]')).not.toBeNull();
  });

  /**
   * Renders and then settles the queue read the shell starts in its
   * constructor: the badge only exists once that promise has resolved and a
   * change detection pass has run over the signal it wrote.
   */
  async function renderWithQueue(entries: QueueEntry[]) {
    platform.capabilities = { canDownload: true, hasLocalStore: true };
    platform.queue = entries;
    const fixture = await render();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('counts the unfinished queue entries on the downloads entry', async () => {
    const fixture = await renderWithQueue([
      queueEntry('a', 'DOWNLOADING'),
      queueEntry('b', 'QUEUED'),
      queueEntry('c', 'FAILED'),
      queueEntry('d', 'DONE'),
    ]);

    const badge = fixture.nativeElement.querySelector(
      '[data-testid="nav-downloads-count"]',
    ) as HTMLElement;
    expect(badge).not.toBeNull();
    // Everything that is not finished, a failed row included: the badge
    // answers whether anything is left to deal with.
    expect(badge.textContent).toContain('3');
    // And the number reaches a screen reader as a sentence rather than as a
    // bare digit stuck to the end of the link's name.
    expect(badge.querySelector('.sr-only')?.textContent).toContain('3');
  });

  it('reads the queue at startup so the count does not wait for the screen', async () => {
    // The store fills its queue signal on an explicit read, not from the
    // progress stream alone. Without the shell asking, the badge would stay
    // empty until the downloads screen was opened — the one place it is no
    // longer needed.
    //
    // How many reads that first ask turns into belongs to the store: starting
    // its event subscription triggers a read of its own, so the count is more
    // than one and is not something this test should pin.
    await renderWithQueue([queueEntry('a', 'DOWNLOADING')]);
    expect(platform.queueReads).toBeGreaterThan(0);
  });

  it('shows no badge while nothing is outstanding', async () => {
    const fixture = await renderWithQueue([queueEntry('a', 'DONE')]);

    // An empty queue is the resting state; a nought would be a permanent mark
    // reporting that there is nothing to report.
    expect(fixture.nativeElement.querySelector('[data-testid="nav-downloads-count"]')).toBeNull();
  });

  it('leaves the queue alone where the device keeps no local store', async () => {
    // The web build has no queue to read, so the shell asks for nothing
    // rather than asking and being handed an empty answer.
    await render();
    expect(platform.queueReads).toBe(0);
    expect(platform.capabilities.hasLocalStore).toBe(false);
  });
});
