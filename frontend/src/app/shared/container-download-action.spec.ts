import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../testing/fake-platform.service';
import type { DownloadUnit } from '../core/library/aggregate';
import { LocaleService } from '../core/i18n/locale.service';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { contentAvailability } from '../core/platform/models';
import type { Availability, LessonSummary } from '../core/platform/models';
import { PlatformService } from '../core/platform/platform.service';
import { ContainerDownloadAction } from './container-download-action';

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

function mindMap(availability: Availability): DownloadUnit {
  return { id: 'map-1', availability: contentAvailability(availability) };
}

describe('ContainerDownloadAction', () => {
  let fake: FakePlatformService;

  beforeEach(async () => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };

    TestBed.configureTestingModule({
      providers: [
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

  function render(lessons: readonly LessonSummary[], extraUnits: readonly DownloadUnit[] = []) {
    const fixture = TestBed.createComponent(ContainerDownloadAction);
    fixture.componentRef.setInput('scope', { kind: 'TRACK', id: 'track-1' });
    fixture.componentRef.setInput('title', 'Signals');
    fixture.componentRef.setInput('lessons', lessons);
    fixture.componentRef.setInput('extraUnits', extraUnits);
    fixture.detectChanges();
    return fixture;
  }

  function wrapper(fixture: ReturnType<typeof render>): HTMLElement {
    const element = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="container-download-action"]',
    );
    if (element === null) {
      throw new Error('Container action wrapper not found.');
    }
    return element as HTMLElement;
  }

  const fourDownloaded = [
    lesson('lesson-1', 'DOWNLOADED'),
    lesson('lesson-2', 'DOWNLOADED'),
    lesson('lesson-3', 'DOWNLOADED'),
    lesson('lesson-4', 'DOWNLOADED'),
  ];

  it('counts a missing mind map as a fifth unit and keeps offering the download', () => {
    const fixture = render(fourDownloaded, [mindMap('NOT_DOWNLOADED')]);
    const element = wrapper(fixture);

    expect(element.textContent).toContain('4 of 5 downloaded');

    const button = element.querySelector('button');
    expect(button).not.toBeNull();
    // The visible word, plus the container's title carried in a
    // screen-reader-only span so the accessible name says which container this
    // button is for without replacing the words a person can read.
    expect(button?.textContent?.trim()).toBe('Download Signals');
    expect(element.textContent).not.toContain('Downloaded (');
  });

  it('keeps the container action as the primary, accent-filled control', () => {
    // This is the one primary action a track or module page shows; a
    // per-lesson Download/Update nested inside it renders as secondary
    // instead, so the accent fill never appears twice in the same view.
    const fixture = render(fourDownloaded, [mindMap('NOT_DOWNLOADED')]);
    const button = wrapper(fixture).querySelector('button');

    expect(button?.className).toContain('bg-accent');
  });

  it('enqueues the whole container when that button is pressed', async () => {
    const fixture = render(fourDownloaded, [mindMap('NOT_DOWNLOADED')]);
    wrapper(fixture).querySelector('button')?.click();
    await fixture.whenStable();

    expect(fake.enqueued).toEqual([{ kind: 'TRACK', id: 'track-1' }]);
  });

  it('reports the container complete once the mind map is stored too', () => {
    const fixture = render(fourDownloaded, [mindMap('DOWNLOADED')]);
    const element = wrapper(fixture);

    expect(element.textContent).toContain('5 of 5 downloaded');
    expect(element.querySelector('button')).toBeNull();
  });

  it('is unchanged for a container that has no extra units, such as a module', () => {
    const fixture = render(fourDownloaded);
    const element = wrapper(fixture);

    expect(element.textContent).toContain('4 of 4 downloaded');
    expect(element.querySelector('button')).toBeNull();
  });
});
