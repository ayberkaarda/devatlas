import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { AdminBlogPost, Page } from '../../../core/admin/admin-models';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformService } from '../../../core/platform/platform.service';
import { ReviewQueuePage } from './review-queue.page';

function post(overrides: Partial<AdminBlogPost> = {}): AdminBlogPost {
  return {
    id: 'post-1',
    slug: 'spring-boot-4-1-1-released',
    title: 'Spring Boot 4.1.1 Released',
    bodyMarkdown: 'Spring Boot **4.1.1** is available.',
    status: 'PENDING_REVIEW',
    source: 'AUTO',
    sourceUrl: 'https://spring.io/blog/spring-boot-4-1-1',
    sourceUpdateId: 'update-1',
    publishedAt: null,
    createdBy: null,
    createdAt: '2026-08-20T12:00:11.004Z',
    updatedAt: '2026-08-20T12:00:11.004Z',
    version: 1,
    ...overrides,
  };
}

function page(items: readonly AdminBlogPost[], overrides: Partial<Page<AdminBlogPost>> = {}) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, ...overrides };
}

describe('ReviewQueuePage', () => {
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
    api.listReviewQueueCalls.mockResolvedValue(page([post()]));
    const fixture = TestBed.createComponent(ReviewQueuePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('loads the pending-review queue with no sort parameter and renders its rows', async () => {
    const fixture = await render();

    expect(api.listReviewQueueCalls.lastArgs).toEqual([{ source: undefined, page: 0, size: 20 }]);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Spring Boot 4.1.1 Released');
  });

  it('reloads at page 0 with the chosen source filter when the filter changes', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    api.listReviewQueueCalls.mockResolvedValue(page([post({ source: 'MANUAL' })]));
    const select = element.querySelector('#review-source-filter') as HTMLSelectElement;
    select.value = 'MANUAL';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(api.listReviewQueueCalls.lastArgs).toEqual([{ source: 'MANUAL', page: 0, size: 20 }]);
  });

  it('reloads at the requested page when pagination changes it', async () => {
    api.listReviewQueueCalls.mockResolvedValue(
      page([post()], { page: 0, totalPages: 3, totalElements: 60 }),
    );
    const fixture = TestBed.createComponent(ReviewQueuePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    api.listReviewQueueCalls.mockResolvedValue(
      page([post()], { page: 1, totalPages: 3, totalElements: 60 }),
    );
    const element = fixture.nativeElement as HTMLElement;
    const nextButton = Array.from(element.querySelectorAll('button')).find((button) =>
      button.textContent?.trim().includes('Next'),
    ) as HTMLButtonElement;
    nextButton.click();
    await fixture.whenStable();

    expect(api.listReviewQueueCalls.lastArgs).toEqual([{ source: undefined, page: 1, size: 20 }]);
  });
});
