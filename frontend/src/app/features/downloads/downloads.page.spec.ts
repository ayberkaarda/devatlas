import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import type { QueueEntry } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { DownloadsPage } from './downloads.page';

function queueEntry(overrides: Partial<QueueEntry> = {}): QueueEntry {
  return {
    entityId: 'lesson-1',
    entityType: 'LESSON',
    title: 'Introduction to signals',
    batchId: 'batch-1',
    state: 'DONE',
    receivedBytes: 41233,
    totalBytes: 41233,
    attempt: 1,
    pauseReason: null,
    errorCode: null,
    locales: [],
    trackId: 'track-1',
    trackTitle: 'Angular Fundamentals',
    ...overrides,
  };
}

describe('DownloadsPage', () => {
  let fake: FakePlatformService;
  let rows: QueueEntry[];

  beforeEach(async () => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };
    rows = [];
    fake.queueState = async () => rows;

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

  async function render() {
    const fixture = TestBed.createComponent(DownloadsPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('lists the locales a stored package holds, as short codes', async () => {
    rows = [queueEntry({ locales: ['en', 'fr', 'tr'] })];
    const element = (await render()).nativeElement as HTMLElement;

    // The set, not one language: a package delivers a lesson and all of its
    // translations together, and a user deciding whether to keep a download
    // is deciding about all of it.
    expect(element.querySelector('[data-testid="entry-locales"]')?.textContent?.trim()).toBe(
      'EN · FR · TR',
    );
  });

  it('renders no locale element at all for a row that holds nothing yet', async () => {
    rows = [queueEntry({ locales: [] })];
    const element = (await render()).nativeElement as HTMLElement;

    // Not an empty span with a stray separator in it: there is nothing to say.
    expect(element.querySelector('[data-testid="entry-locales"]')).toBeNull();
  });

  it('shows a bar and a percentage for a batch in flight', async () => {
    rows = [
      queueEntry({ state: 'DOWNLOADING', receivedBytes: 25, totalBytes: 100 }),
      queueEntry({ entityId: 'lesson-2', state: 'QUEUED', receivedBytes: 0, totalBytes: 100 }),
    ];
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="batch-percent"]')?.textContent?.trim()).toContain(
      '13%',
    );

    const bars = element.querySelectorAll('[data-testid="progress-bar"]');
    // One for the batch total, one per entry in it.
    expect(bars.length).toBe(3);
    expect(bars[0].getAttribute('aria-label')).toBe('Total download progress');
    expect(bars[0].getAttribute('aria-valuenow')).toBe('13');
  });

  it('states no percentage for a batch whose size is not known yet', async () => {
    rows = [queueEntry({ state: 'QUEUED', receivedBytes: 0, totalBytes: 0 })];
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="batch-percent"]')).toBeNull();
    expect(
      element.querySelector('[data-testid="progress-bar"]')?.getAttribute('aria-valuenow'),
    ).toBeNull();
  });

  it('groups downloaded rows by track, each headed by its own title', async () => {
    rows = [
      queueEntry({
        entityId: 'lesson-1',
        trackId: 'track-1',
        trackTitle: 'Angular Fundamentals',
      }),
      queueEntry({
        entityId: 'lesson-2',
        trackId: 'track-2',
        trackTitle: 'Rust Basics',
      }),
    ];
    const element = (await render()).nativeElement as HTMLElement;

    const groups = element.querySelectorAll('[data-testid="track-group"]');
    expect(groups.length).toBe(2);

    const titles = [...element.querySelectorAll('[data-testid="track-group-title"]')].map((node) =>
      node.textContent?.trim(),
    );
    expect(titles).toEqual(['Angular Fundamentals', 'Rust Basics']);
  });

  it('renders a mind map row inside its track group', async () => {
    rows = [
      queueEntry({
        entityId: 'lesson-1',
        entityType: 'LESSON',
        trackId: 'track-1',
        trackTitle: 'Angular Fundamentals',
      }),
      queueEntry({
        entityId: 'mind-map-1',
        entityType: 'MIND_MAP',
        title: null,
        trackId: 'track-1',
        trackTitle: 'Angular Fundamentals',
      }),
    ];
    const element = (await render()).nativeElement as HTMLElement;

    const group = element.querySelector('[data-testid="track-group"]');
    expect(group?.textContent).toContain('Mind map');
    expect(group?.textContent).toContain('mind-map-1');

    // A mind map row carries no delete button of its own — it is covered by
    // the group's track-level action instead.
    expect(group?.querySelectorAll('[data-testid="track-delete"]').length).toBe(1);
  });

  it('deletes a track only after the track-level confirmation is accepted', async () => {
    rows = [queueEntry({ trackId: 'track-1', trackTitle: 'Angular Fundamentals' })];
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    const deleteButton = element.querySelector<HTMLButtonElement>('[data-testid="track-delete"]');
    expect(deleteButton).not.toBeNull();
    expect(fake.deletedScopes.length).toBe(0);

    deleteButton!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Clicking once only asks for confirmation, nothing is deleted yet.
    expect(fake.deletedScopes.length).toBe(0);
    const confirmButton = element.querySelector<HTMLButtonElement>(
      '[data-testid="track-confirm-delete"]',
    );
    expect(confirmButton).not.toBeNull();

    confirmButton!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fake.deletedScopes).toEqual([{ kind: 'TRACK', id: 'track-1' }]);
  });

  it('does not let a lesson-level and a track-level confirmation activate each other', async () => {
    rows = [
      queueEntry({
        entityId: 'lesson-1',
        entityType: 'LESSON',
        trackId: 'track-1',
        trackTitle: 'Angular Fundamentals',
      }),
    ];
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    // Arm the track-level confirmation first.
    element.querySelector<HTMLButtonElement>('[data-testid="track-delete"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="track-confirm-delete"]')).not.toBeNull();

    // Requesting the lesson-level delete must cancel the track confirmation
    // rather than leaving both armed at once.
    element.querySelector<HTMLButtonElement>('[data-testid="lesson-delete"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="track-confirm-delete"]')).toBeNull();
    expect(element.querySelector('[data-testid="lesson-confirm-delete"]')).not.toBeNull();

    // Confirming now must only ever delete the lesson, never the track.
    element.querySelector<HTMLButtonElement>('[data-testid="lesson-confirm-delete"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fake.deletedScopes).toEqual([{ kind: 'LESSON', id: 'lesson-1' }]);
  });
});
