import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import { contentAvailability } from '../../core/platform/models';
import type {
  DeltaSummary,
  LessonSummary,
  ModuleDetail,
  ProgressEntry,
  TrackDetail,
} from '../../core/platform/models';
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

function lessonSummary(id: string, slug: string, title: string, order: number): LessonSummary {
  return {
    id,
    slug,
    title,
    difficulty: null,
    estimatedMinutes: null,
    order,
    availability: contentAvailability('NOT_DOWNLOADED'),
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
}

function moduleWithLesson(): ModuleDetail {
  return {
    id: 'module-1',
    title: 'Basics',
    order: 0,
    estimatedMinutes: null,
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    lessons: [lessonSummary('lesson-1', 'intro', 'Introduction', 0)],
  };
}

/** Two modules of two lessons each, so a per-module count can differ from the total. */
function twoModules(): readonly ModuleDetail[] {
  return [
    {
      id: 'module-1',
      title: 'Basics',
      order: 0,
      estimatedMinutes: null,
      translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
      lessons: [
        lessonSummary('lesson-1', 'intro', 'Introduction', 0),
        lessonSummary('lesson-2', 'signals', 'Signals', 1),
      ],
    },
    {
      id: 'module-2',
      title: 'Beyond',
      order: 1,
      estimatedMinutes: null,
      translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
      lessons: [
        lessonSummary('lesson-3', 'testing', 'Testing', 0),
        lessonSummary('lesson-4', 'forms', 'Forms', 1),
      ],
    },
  ];
}

function progressEntry(lessonId: string, completedAt: string | null): ProgressEntry {
  return { lessonId, completedAt, clientUpdatedAt: '2026-09-06T20:14:00.000Z' };
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

  it('marks the lessons this reader has finished, in words as well as in colour', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: twoModules() }));
    fake.listProgress = async () => [
      progressEntry('lesson-1', '2026-09-06T20:14:00.000Z'),
      // Explicitly marked incomplete, which is a recorded action and not a
      // completion: a row that exists is not the same as a lesson that is done.
      progressEntry('lesson-2', null),
    ];

    const element = (await render('signals')).nativeElement as HTMLElement;

    const marks = element.querySelectorAll('[data-testid="lesson-completed"]');
    expect(marks).toHaveLength(1);
    expect(marks[0].querySelector('.sr-only')?.textContent?.trim()).toBe('Completed');
    // The mark sits on the row it describes, ahead of that row's link.
    const rows = element.querySelectorAll('li');
    expect(rows[0].querySelector('[data-testid="lesson-completed"]')).not.toBeNull();
    expect(rows[1].querySelector('[data-testid="lesson-completed"]')).toBeNull();
  });

  it('counts the finished lessons per module and across the whole path', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: twoModules() }));
    fake.listProgress = async () => [
      progressEntry('lesson-1', '2026-09-06T20:14:00.000Z'),
      progressEntry('lesson-3', '2026-09-06T20:14:00.000Z'),
    ];

    const element = (await render('signals')).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="track-completed-count"]')?.textContent).toContain(
      '2 of 4',
    );
    const perModule = element.querySelectorAll('[data-testid="module-completed-count"]');
    expect(perModule).toHaveLength(2);
    expect(perModule[0].textContent).toContain('1 of 2');
    expect(perModule[1].textContent).toContain('1 of 2');
  });

  it('ignores completions recorded against lessons this path does not hold', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: twoModules() }));
    // One read answers for every row on the screen, so it returns this
    // reader's whole history rather than this path's slice of it.
    fake.listProgress = async () => [
      progressEntry('lesson-1', '2026-09-06T20:14:00.000Z'),
      progressEntry('lesson-from-another-path', '2026-09-06T20:14:00.000Z'),
    ];

    const element = (await render('signals')).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="track-completed-count"]')?.textContent).toContain(
      '1 of 4',
    );
  });

  it('renders the path with nothing marked when progress cannot be read', async () => {
    fake.trackDetails.set('signals', trackDetail({ modules: twoModules() }));
    fake.listProgress = async () => {
      // What an anonymous reader gets on the web, every time. It is an
      // ordinary condition rather than a fault, so it is neither shown nor
      // announced.
      throw new PlatformError('AUTH_REQUIRED', 'no session');
    };

    const element = (await render('signals')).nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('[data-testid="discovery-note"]')).toBeNull();
    expect(element.querySelectorAll('[data-testid="lesson-completed"]')).toHaveLength(0);
    expect(element.querySelector('[data-testid="track-completed-count"]')?.textContent).toContain(
      '0 of 4',
    );
  });
});
