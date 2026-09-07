import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import type {
  Availability,
  DeltaSummary,
  LessonSummary,
  MindMapSummary,
  ModuleDetail,
  TrackDetail,
} from '../../core/platform/models';
import { contentAvailability } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { MindMapPage } from './mind-map.page';

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

function mindMapSummary(availability: Availability = 'NOT_DOWNLOADED'): MindMapSummary {
  return { id: 'map-1', availability: contentAvailability(availability), sizeBytes: 4096 };
}

function lesson(id: string, availability: Availability): LessonSummary {
  return {
    id,
    slug: id,
    title: id,
    difficulty: null,
    estimatedMinutes: null,
    order: 0,
    availability: contentAvailability(availability),
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
}

function moduleWith(lessons: readonly LessonSummary[]): ModuleDetail {
  return {
    id: 'module-1',
    title: 'Basics',
    order: 0,
    estimatedMinutes: null,
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    lessons,
  };
}

function moduleWithLesson(): ModuleDetail {
  return moduleWith([lesson('lesson-1', 'NOT_DOWNLOADED')]);
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

describe('MindMapPage', () => {
  let fake: FakePlatformService;

  beforeEach(async () => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };
    // Never reached once the track is discovered with a mind map, in these
    // tests — kept failing loudly so a scenario that does reach it says so.
    fake.getMindMap = async () => {
      throw new PlatformError('ENTITY_NOT_IN_LIBRARY', 'not local');
    };

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
    const fixture = TestBed.createComponent(MindMapPage);
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

  it('discovers the track once, before deciding whether it has a mind map', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: [] }));
    const calls: (string | undefined)[] = [];
    fake.refreshLibrary = async (trackId?: string) => {
      calls.push(trackId);
      fake.trackDetails.set(
        'signals',
        trackDetail({ modules: [moduleWithLesson()], mindMap: mindMapSummary() }),
      );
      return summary();
    };

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(calls).toEqual(['track-1']);
    // The track is known to have a mind map now, but it is still not in the
    // local library, which is a normal state to offer a download for.
    expect(element.textContent).toContain(
      'This mind map has not been downloaded to this device yet.',
    );
  });

  it('offers a usable download when every lesson is stored but the mind map is not', async () => {
    // The state the reported defect was found in: four downloaded lessons, no
    // mind map. A control that aggregated lessons alone found four of four,
    // declared the track complete and rendered no button at all, leaving this
    // card explaining that the mind map is missing with no way to fetch it.
    fake.trackDetails.set(
      'signals',
      trackDetail({
        modules: [
          moduleWith([
            lesson('lesson-1', 'DOWNLOADED'),
            lesson('lesson-2', 'DOWNLOADED'),
            lesson('lesson-3', 'DOWNLOADED'),
            lesson('lesson-4', 'DOWNLOADED'),
          ]),
        ],
        mindMap: mindMapSummary('NOT_DOWNLOADED'),
      }),
    );

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain(
      'This mind map has not been downloaded to this device yet.',
    );

    const action = element.querySelector('[data-testid="container-download-action"]');
    expect(action?.textContent).toContain('4 of 5 downloaded');

    const button = action?.querySelector('button');
    expect(button).not.toBeNull();
    // The visible word, plus the container's title in a screen-reader-only
    // span: the accessible name says what this button downloads without
    // replacing the word a person can read and can speak to voice control.
    expect(button?.textContent?.trim()).toBe('Download Signals');

    button?.click();
    await fixture.whenStable();
    expect(fake.enqueued).toEqual([{ kind: 'TRACK', id: 'track-1' }]);
  });

  it('does not refresh a second time when the re-read still has zero modules', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: [] }));
    let refreshCalls = 0;
    fake.refreshLibrary = async () => {
      refreshCalls += 1;
      return summary();
    };

    const fixture = await render('signals');
    const element = fixture.nativeElement as HTMLElement;

    expect(refreshCalls).toBe(1);
    expect(element.textContent).toContain('This learning path has no mind map.');
  });

  it('keeps rendering the local track, with a note, when the refresh rejects', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: [] }));
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
    fake.trackDetails.set('signals', trackDetail({ modules: [] }));
    let called = false;
    fake.refreshLibrary = async () => {
      called = true;
      return summary();
    };

    await render('signals');

    expect(called).toBe(false);
  });
});
