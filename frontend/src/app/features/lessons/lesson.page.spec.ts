import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { AUTH_TOKEN_DELIVERY } from '../../core/auth/auth-models';
import { API_BASE_URL } from '../../core/platform/api';
import { PlatformError } from '../../core/platform/errors';
import { contentAvailability } from '../../core/platform/models';
import type { Lesson, LessonSummary, ModuleDetail, TrackDetail } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ThemeService } from '../../core/theme/theme.service';
import { LessonPage } from './lesson.page';

function lessonSummary(slug: string, title: string, order: number): LessonSummary {
  return {
    id: `id-${slug}`,
    slug,
    title,
    difficulty: null,
    estimatedMinutes: null,
    order,
    availability: contentAvailability('REMOTE'),
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
}

function module(
  id: string,
  title: string,
  order: number,
  lessons: readonly LessonSummary[],
): ModuleDetail {
  return {
    id,
    title,
    order,
    estimatedMinutes: null,
    lessons,
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
}

/**
 * A track whose payload order contradicts its declared order on both axes:
 * the second module is listed first, and inside each module the higher-ordered
 * lesson is listed first. The reading order is therefore Introduction,
 * Signals, Testing, Forms — which a flat sort by lesson order alone cannot
 * produce, because every module numbers its lessons from zero.
 */
function track(overrides: Partial<TrackDetail> = {}): TrackDetail {
  return {
    id: 'track-1',
    slug: 'angular',
    title: 'Angular',
    description: null,
    icon: null,
    contentVersion: 1,
    mindMap: null,
    modules: [
      module('module-2', 'Beyond', 1, [
        lessonSummary('forms', 'Forms', 1),
        lessonSummary('testing', 'Testing', 0),
      ]),
      module('module-1', 'Basics', 0, [
        lessonSummary('signals', 'Signals', 1),
        lessonSummary('intro', 'Introduction', 0),
      ]),
    ],
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

function lesson(overrides: Partial<Lesson> = {}): Lesson {
  return {
    id: 'lesson-1',
    slug: 'signals',
    title: 'Signals',
    difficulty: null,
    estimatedMinutes: null,
    bodyMarkdown: '## Body\n\nA paragraph.\n',
    codeExamples: [],
    contentVersion: 1,
    trackSlug: 'angular',
    trackTitle: 'Angular',
    moduleTitle: 'Basics',
    completedAt: null,
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

describe('LessonPage', () => {
  let fake: FakePlatformService;

  beforeEach(async () => {
    fake = new FakePlatformService();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        // The progress sync service this screen touches owns an HTTP client;
        // nothing here issues a request, the backend is present so the
        // injector graph can be built.
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'https://api.example.test/api/v1' },
        { provide: AUTH_TOKEN_DELIVERY, useValue: 'COOKIE' },
        { provide: PlatformService, useValue: fake },
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

  async function render(lessonSlug = 'signals') {
    const fixture = TestBed.createComponent(LessonPage);
    fixture.componentRef.setInput('trackSlug', 'angular');
    fixture.componentRef.setInput('lessonSlug', lessonSlug);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
    return fixture;
  }

  it('keeps a first-level heading on the screen when the lesson cannot be loaded', async () => {
    fake.getLesson = async () => {
      throw new PlatformError('LESSON_NOT_FOUND', 'gone');
    };
    const element = (await render()).nativeElement as HTMLElement;

    // The error card is the only heading there is in this branch, so it is the
    // first level. A document whose highest heading is an h2 begins halfway
    // down for anyone navigating by heading.
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('h2')).toBeNull();
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('keeps the lesson on screen when recording a completion fails', async () => {
    fake.lessons.set('signals', lesson());
    fake.markProgress = async () => {
      throw new PlatformError('STORE_UNAVAILABLE', 'no store');
    };
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    const toggle = element.querySelector<HTMLButtonElement>(
      '[data-testid="lesson-complete-toggle"]',
    )!;
    toggle.click();
    await fixture.whenStable();
    fixture.detectChanges();

    // A progress write that did not land is not a reason to take away the
    // article somebody is in the middle of reading — which is what happened
    // while this shared the load failure's signal.
    expect(element.querySelector('article')).not.toBeNull();
    expect(element.querySelector('[data-testid="lesson-complete-toggle"]')).not.toBeNull();
    expect(
      element.querySelector('[data-testid="lesson-toggle-error"]')?.textContent?.trim(),
    ).not.toBe('');
  });

  it('opens a lesson that was already finished offering to undo it, not to redo it', async () => {
    fake.lessons.set('signals', lesson({ completedAt: '2026-09-06T20:14:00.000Z' }));
    const element = (await render()).nativeElement as HTMLElement;

    const toggle = element.querySelector('[data-testid="lesson-complete-toggle"]')!;
    const translate = TestBed.inject(TranslateService);
    // Taken from the catalogue rather than written here, and checked against
    // the other label: a missing key renders as the key itself, and the
    // lookup would then agree with the screen while the reader saw nothing
    // useful.
    expect(translate.instant('lesson.markIncomplete')).not.toBe('lesson.markIncomplete');
    expect(toggle.textContent?.trim()).toBe(translate.instant('lesson.markIncomplete'));
    expect(toggle.textContent?.trim()).not.toBe(translate.instant('lesson.markComplete'));
  });

  it('offers to mark a lesson with no completion recorded against it', async () => {
    // Which covers a reader with no session as well: the server sends no
    // completion for them, and there is none of theirs to show.
    fake.lessons.set('signals', lesson({ completedAt: null }));
    const element = (await render()).nativeElement as HTMLElement;

    const toggle = element.querySelector('[data-testid="lesson-complete-toggle"]')!;
    expect(toggle.textContent?.trim()).toBe(
      TestBed.inject(TranslateService).instant('lesson.markComplete'),
    );
  });

  it('reports the completion state through its label rather than twice over', async () => {
    fake.lessons.set('signals', lesson());
    const element = (await render()).nativeElement as HTMLElement;

    // With both, a screen reader announced "Mark as not completed, pressed":
    // the state given twice, in opposite directions.
    const toggle = element.querySelector('[data-testid="lesson-complete-toggle"]')!;
    expect(toggle.getAttribute('aria-pressed')).toBeNull();
    expect(toggle.textContent?.trim()).not.toBe('');
  });

  it('lets a wide code example scroll inside its own box', async () => {
    fake.lessons.set(
      'signals',
      lesson({ codeExamples: [{ language: 'ts', code: 'const x = 1;', caption: null, order: 0 }] }),
    );
    const element = (await render()).nativeElement as HTMLElement;

    // These examples sit outside the rendered-markdown container, so the
    // stylesheet's overflow rule for code does not reach them and one long
    // line would otherwise widen the page body itself.
    const figure = element.querySelector('figure')!;
    expect(figure.querySelector('.overflow-x-auto')).not.toBeNull();
  });

  it('reads the track on a build that cannot download anything', async () => {
    // The default capabilities of the fake are the web build's. The track is
    // the only source of the structure this screen navigates by, so gating
    // the read on a local library is what used to leave the web with a lesson
    // and no way onward.
    expect(fake.capabilities.canDownload).toBe(false);
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const slugs: string[] = [];
    const real = fake.getTrack.bind(fake);
    fake.getTrack = async (slug: string) => {
      slugs.push(slug);
      return real(slug);
    };

    await render();

    expect(slugs).toEqual(['angular']);
  });

  it('names the track and the module above the title', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    const crumbs = element.querySelectorAll('[data-testid="lesson-breadcrumb"] li');
    expect(crumbs).toHaveLength(3);
    expect(crumbs[1].textContent).toContain('Angular');
    // The module holding this lesson, taken from the track's structure rather
    // than from the lesson payload, which carries no module title on a client
    // reading from a local library.
    expect(crumbs[2].textContent).toContain('Basics');
    expect(crumbs[2].querySelector('a')).toBeNull();
  });

  it('drops a crumb it has no value for instead of inventing one', async () => {
    // Which is the shape a locally stored lesson arrives in: the row carries
    // neither its track's nor its module's translated title.
    fake.lessons.set('signals', lesson({ trackTitle: null, moduleTitle: null }));
    fake.getTrack = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
    };
    const element = (await render()).nativeElement as HTMLElement;

    const nav = element.querySelector('[data-testid="lesson-breadcrumb"]')!;
    expect(nav.querySelectorAll('li')).toHaveLength(1);
    expect(nav.getAttribute('aria-label')).toBe(
      TestBed.inject(TranslateService).instant('lesson.breadcrumb'),
    );
  });

  it('keeps the article when the track it belongs to cannot be read', async () => {
    fake.lessons.set('signals', lesson());
    fake.getTrack = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'offline');
    };
    const element = (await render()).nativeElement as HTMLElement;

    // Losing the lesson because its neighbours could not be listed would be a
    // worse trade than the one the neighbours are there to fix.
    expect(element.querySelector('article')).not.toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
    expect(element.querySelector('[data-testid="lesson-pager"]')).toBeNull();
    expect(element.querySelector('[data-testid="lesson-position"]')).toBeNull();
  });

  it('walks the path by module order first and lesson order within it', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    // Lesson order is per module, so every module has a lesson numbered zero
    // and a flat sort by it would put Testing — the first lesson of the
    // second module — next to Introduction.
    const previous = element.querySelector('[data-testid="lesson-previous"]')!;
    const next = element.querySelector('[data-testid="lesson-next"]')!;
    expect(previous.textContent).toContain('Introduction');
    expect(next.textContent).toContain('Testing');
    expect(previous.getAttribute('href')).toBe('/tracks/angular/lessons/intro');
    expect(next.getAttribute('href')).toBe('/tracks/angular/lessons/testing');
  });

  it('counts the reader position across the whole path, not within one module', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="lesson-position"]')?.textContent?.trim()).toBe(
      TestBed.inject(TranslateService).instant('lesson.position', { index: 2, total: 4 }),
    );
  });

  it('offers no way back from the first lesson of the path', async () => {
    fake.lessons.set('intro', lesson({ slug: 'intro', title: 'Introduction' }));
    fake.trackDetails.set('angular', track());
    const element = (await render('intro')).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="lesson-previous"]')).toBeNull();
    expect(element.querySelector('[data-testid="lesson-next"]')?.textContent).toContain('Signals');
  });

  it('turns the forward link into the way back to the path on the last lesson', async () => {
    fake.lessons.set('forms', lesson({ slug: 'forms', title: 'Forms' }));
    fake.trackDetails.set('angular', track());
    const element = (await render('forms')).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="lesson-next"]')).toBeNull();
    const end = element.querySelector('[data-testid="lesson-path-end"]')!;
    expect(end.getAttribute('href')).toBe('/tracks/angular');
    expect(end.textContent?.trim()).toBe(
      TestBed.inject(TranslateService).instant('lesson.backToTrack'),
    );
  });

  it('names each neighbour with its own title and nothing else', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    // A control whose accessible name does not contain its visible text
    // cannot be asked for by name, so the visible title is the name and there
    // is no aria-label saying something different over the top of it.
    for (const id of ['lesson-previous', 'lesson-next']) {
      const link = element.querySelector(`[data-testid="${id}"]`)!;
      expect(link.getAttribute('aria-label')).toBeNull();
      expect(link.getAttribute('title')).toBeNull();
    }
  });

  it('builds the two ends out of parts that may shrink rather than parts held at a width', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    // Named for what it checks. These tests run without a layout engine, so
    // nothing here can watch the row wrap at 320px — "Vorherige Lektion" over
    // a lesson title, twice, is a measurement and belongs to a visual pass.
    // What is checkable is that nothing in the row forbids it: the container
    // is allowed to break onto a second line, neither end is pinned to its
    // content width, and a long title breaks inside its own column.
    const pager = element.querySelector('[data-testid="lesson-pager"]')!;
    expect(pager.className).toContain('flex-wrap');
    for (const id of ['lesson-previous', 'lesson-next']) {
      const link = element.querySelector(`[data-testid="${id}"]`)!;
      expect(link.className).toContain('min-w-0');
      expect(link.className).toContain('max-w-full');
      expect(link.querySelector('.break-words')).not.toBeNull();
    }
  });

  it('keeps the tail of the article inside the reading measure', async () => {
    fake.lessons.set('signals', lesson());
    fake.trackDetails.set('angular', track());
    const element = (await render()).nativeElement as HTMLElement;

    // A two-ended row of links pinned to the full page width under a capped
    // column reads as belonging to the page rather than to the article.
    const measure = element.querySelector('.max-w-\\[70ch\\]')!;
    expect(measure.querySelector('[data-testid="lesson-complete-toggle"]')).not.toBeNull();
    expect(measure.querySelector('[data-testid="lesson-toggle-error"]')).not.toBeNull();
    expect(measure.querySelector('[data-testid="lesson-position"]')).not.toBeNull();
    expect(measure.querySelector('[data-testid="lesson-pager"]')).not.toBeNull();
  });
});
