import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { Page, SourceFetchResult, WhitelistSource } from '../../../core/admin/admin-models';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformError } from '../../../core/platform/errors';
import { PlatformService } from '../../../core/platform/platform.service';
import { WhitelistSourceListPage } from './whitelist-source-list.page';

function source(overrides: Partial<WhitelistSource> = {}): WhitelistSource {
  return {
    id: 'source-1',
    name: 'Spring Blog',
    feedUrl: 'https://spring.io/blog.atom',
    verifyUrlPattern: 'https://spring.io/blog/{version}',
    enabled: true,
    lastFetchedAt: null,
    createdAt: '2026-08-01T00:00:00.000Z',
    updatedAt: '2026-08-01T00:00:00.000Z',
    version: 0,
    ...overrides,
  };
}

function page(items: readonly WhitelistSource[]): Page<WhitelistSource> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function fetchResult(overrides: Partial<SourceFetchResult> = {}): SourceFetchResult {
  return {
    whitelistSourceId: 'source-1',
    fetched: 5,
    created: 3,
    duplicates: 1,
    rejected: 1,
    createdSourceUpdateIds: ['update-1', 'update-2', 'update-3'],
    rejections: [{ versionString: '4.1.0', failedCheck: 'HASH_NOT_SEEN', detail: 'Already seen.' }],
    durationMs: 3200,
    ...overrides,
  };
}

describe('WhitelistSourceListPage', () => {
  let api: FakeAdminApiClient;

  beforeEach(async () => {
    api = new FakeAdminApiClient();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({ loader: BundledTranslateLoader, fallbackLang: 'en', lang: 'en' }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function render() {
    api.listSourcesCalls.mockResolvedValue(page([source()]));
    const fixture = TestBed.createComponent(WhitelistSourceListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function findButton(element: HTMLElement, text: string): HTMLButtonElement {
    const button = Array.from(element.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.trim().includes(text),
    );
    if (button === undefined) {
      throw new Error(`No button found containing "${text}"`);
    }
    return button as HTMLButtonElement;
  }

  it('loads the default-sorted first page and renders a row per source', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(api.listSourcesCalls.lastArgs).toEqual([{ sort: ['name,asc'], page: 0, size: 20 }]);
    expect(element.querySelector('[data-testid="source-row-source-1"]')).not.toBeNull();
    expect(element.textContent).toContain('Spring Blog');
  });

  it('shows the fetch result once a manual fetch completes, with the three counters summing to fetched', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    api.fetchSourceNowCalls.mockResolvedValueOnce(fetchResult());
    api.getSourceCalls.mockResolvedValueOnce(source({ lastFetchedAt: '2026-09-05T10:00:00.000Z' }));
    findButton(element, 'Fetch now').click();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const result = element.querySelector('[data-testid="fetch-result"]') as HTMLElement;
    expect(result).not.toBeNull();
    const fetched = Number(
      result.querySelector('[data-testid="fetch-result-fetched"]')?.textContent,
    );
    const created = Number(
      result.querySelector('[data-testid="fetch-result-created"]')?.textContent,
    );
    const duplicates = Number(
      result.querySelector('[data-testid="fetch-result-duplicates"]')?.textContent,
    );
    const rejected = Number(
      result.querySelector('[data-testid="fetch-result-rejected"]')?.textContent,
    );
    expect(created + duplicates + rejected).toBe(fetched);
  });

  it('surfaces PIPELINE_RUN_IN_PROGRESS under the row when a fetch is already running', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    api.fetchSourceNowCalls.mockRejectedValueOnce(
      new PlatformError('PIPELINE_RUN_IN_PROGRESS', 'already running'),
    );
    findButton(element, 'Fetch now').click();
    await fixture.whenStable();
    fixture.detectChanges();

    const row = element.querySelector('[data-testid="source-row-source-1"]') as HTMLElement;
    expect(row.textContent).toContain('A fetch for this source is already running');
  });

  it('shows the disable-instead hint when deleting a source fails with PARENT_NOT_EMPTY', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    findButton(element, 'Delete').click();
    fixture.detectChanges();
    api.deleteSourceCalls.mockRejectedValueOnce(new PlatformError('PARENT_NOT_EMPTY', 'not empty'));
    findButton(element, 'Yes, delete').click();
    await fixture.whenStable();
    fixture.detectChanges();

    const row = element.querySelector('[data-testid="source-row-source-1"]') as HTMLElement;
    expect(row.textContent).toContain("can't be deleted");
    // The source is still in the list — the delete was refused, not applied.
    expect(element.textContent).toContain('Spring Blog');
  });
  it('moves focus onto the delete confirmation and back when it is dismissed', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    const trigger = element.querySelector('[data-delete-for="source-1"]') as HTMLButtonElement;
    trigger.focus();
    trigger.click();
    fixture.detectChanges();

    // Opening the confirmation replaces the button that was focused, and a
    // destroyed element takes the focus with it to the top of the document.
    const confirm = document.activeElement as HTMLButtonElement;
    expect(confirm.tagName).toBe('BUTTON');
    expect(confirm).not.toBe(trigger);

    findButton(element, 'Cancel').click();
    fixture.detectChanges();

    expect(document.activeElement).toBe(element.querySelector('[data-delete-for="source-1"]'));
  });

  it('sends focus to the fetch result, which can arrive a minute after the click', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    api.fetchSourceNowCalls.mockResolvedValueOnce(fetchResult());
    findButton(element, 'Fetch now').click();
    await fixture.whenStable();
    fixture.detectChanges();

    const heading = element.querySelector('h2') as HTMLElement;
    expect(heading.getAttribute('tabindex')).toBe('-1');
    expect(document.activeElement).toBe(heading);
  });
});
