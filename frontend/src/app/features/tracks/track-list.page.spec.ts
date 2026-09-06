import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import type { DeltaSummary, TrackSummary } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { TrackListPage } from './track-list.page';

function track(overrides: Partial<TrackSummary> = {}): TrackSummary {
  return {
    id: 'track-1',
    slug: 'signals',
    title: 'Signals',
    description: null,
    icon: null,
    contentVersion: 1,
    lessonCount: 3,
    downloadedLessonCount: 0,
    updateAvailableCount: 0,
    availability: 'NOT_DOWNLOADED',
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

function summary(): DeltaSummary {
  return {
    checkedTracks: 1,
    updatedEntities: 0,
    withdrawnEntities: 0,
    newEntitiesAvailable: 0,
    anomalies: [],
  };
}

describe('TrackListPage', () => {
  let fake: FakePlatformService;

  beforeEach(async () => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: PlatformService, useValue: fake },
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
    const fixture = TestBed.createComponent(TrackListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    // `whenStable` can resolve one microtask short of the tail of a promise
    // chain this deep (platform read -> discovery -> store -> platform read
    // again): a macrotask boundary reliably lets it drain the rest.
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
    return fixture;
  }

  it('refreshes the library exactly once for an empty local list and renders the re-read result', async () => {
    let refreshCalls = 0;
    fake.refreshLibrary = async () => {
      refreshCalls += 1;
      fake.tracks = [track()];
      return summary();
    };

    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(refreshCalls).toBe(1);
    expect(element.querySelectorAll('li').length).toBe(1);
    expect(element.querySelector('[data-testid="discovery-note"]')).toBeNull();
  });

  it('does not refresh a second time when the re-read is still empty', async () => {
    let refreshCalls = 0;
    fake.refreshLibrary = async () => {
      refreshCalls += 1;
      return summary();
    };

    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(refreshCalls).toBe(1);
    expect(element.textContent).toContain('There are no learning paths to show yet.');
  });

  it('keeps rendering the empty local view, with a note, when the refresh rejects', async () => {
    fake.refreshLibrary = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
    };

    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.textContent).toContain('There are no learning paths to show yet.');
    expect(element.querySelector('[data-testid="discovery-note"]')?.textContent).toContain(
      'The server could not be reached. Check your connection and try again.',
    );
  });

  it('never calls refresh on a build that cannot download', async () => {
    fake.capabilities = { canDownload: false, hasLocalStore: false };
    let called = false;
    fake.refreshLibrary = async () => {
      called = true;
      return summary();
    };

    await render();

    expect(called).toBe(false);
  });
});
