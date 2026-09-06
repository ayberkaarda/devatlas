import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import { contentAvailability } from '../../core/platform/models';
import type { DeltaSummary, ModuleDetail, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { TrackDetailPage } from './track-detail.page';

function trackDetail(overrides: Partial<TrackDetail> = {}): TrackDetail {
  return {
    id: 'track-1',
    slug: 'signals',
    title: 'Signals',
    description: null,
    icon: null,
    contentVersion: 1,
    mindMap: null,
    modules: [],
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

function moduleWithLesson(): ModuleDetail {
  return {
    id: 'module-1',
    title: 'Basics',
    order: 0,
    estimatedMinutes: null,
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    lessons: [
      {
        id: 'lesson-1',
        slug: 'intro',
        title: 'Introduction',
        difficulty: null,
        estimatedMinutes: null,
        order: 0,
        availability: contentAvailability('NOT_DOWNLOADED'),
        translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
      },
    ],
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

describe('TrackDetailPage', () => {
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

  async function render(slug: string) {
    const fixture = TestBed.createComponent(TrackDetailPage);
    fixture.componentRef.setInput('trackSlug', slug);
    fixture.detectChanges();
    await fixture.whenStable();
    // `whenStable` can resolve one microtask short of the tail of a promise
    // chain this deep (platform read -> discovery -> store -> platform read
    // again): a macrotask boundary reliably lets it drain the rest.
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
    return fixture;
  }

  it('refreshes the library exactly once, with the track id, for a track with zero modules', async () => {
    fake.trackDetails.set('signals', trackDetail());
    const calls: (string | undefined)[] = [];
    fake.refreshLibrary = async (trackId?: string) => {
      calls.push(trackId);
      fake.trackDetails.set('signals', trackDetail({ modules: [moduleWithLesson()] }));
      return summary();
    };

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(calls).toEqual(['track-1']);
    expect(element.textContent).toContain('Introduction');
    expect(element.querySelector('[data-testid="discovery-note"]')).toBeNull();
  });

  it('does not refresh a second time when the re-read still has zero modules', async () => {
    fake.trackDetails.set('signals', trackDetail());
    let refreshCalls = 0;
    fake.refreshLibrary = async () => {
      refreshCalls += 1;
      return summary();
    };

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(refreshCalls).toBe(1);
    expect(element.textContent).toContain('This learning path has no lessons yet.');
  });

  it('keeps rendering the local track, with a note, when the refresh rejects', async () => {
    fake.trackDetails.set('signals', trackDetail());
    fake.refreshLibrary = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
    };

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.textContent).toContain('Signals');
    expect(element.querySelector('[data-testid="discovery-note"]')?.textContent).toContain(
      'The server could not be reached. Check your connection and try again.',
    );
  });

  it('never calls refresh on a build that cannot download', async () => {
    fake.capabilities = { canDownload: false, hasLocalStore: false };
    fake.trackDetails.set('signals', trackDetail());
    let called = false;
    fake.refreshLibrary = async () => {
      called = true;
      return summary();
    };

    await render('signals');

    expect(called).toBe(false);
  });
});
