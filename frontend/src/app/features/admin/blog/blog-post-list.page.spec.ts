import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import type { AdminBlogPostSummary, Page } from '../../../core/admin/admin-models';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformError } from '../../../core/platform/errors';
import { PlatformService } from '../../../core/platform/platform.service';
import { BlogPostListPage } from './blog-post-list.page';

function summary(overrides: Partial<AdminBlogPostSummary>): AdminBlogPostSummary {
  return {
    id: 'post-1',
    slug: 'a-post',
    title: 'A Post',
    bodyMarkdown: 'Body.',
    status: 'DRAFT',
    source: 'MANUAL',
    sourceUrl: null,
    sourceUpdateId: null,
    publishedAt: null,
    createdBy: 'user-1',
    createdAt: '2026-09-01T00:00:00.000Z',
    updatedAt: '2026-09-01T00:00:00.000Z',
    version: 0,
    ...overrides,
  };
}

function pageOf(items: readonly AdminBlogPostSummary[]): Page<AdminBlogPostSummary> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

describe('BlogPostListPage', () => {
  let api: FakeAdminApiClient;
  let navigateSpy: jest.SpyInstance;

  beforeEach(async () => {
    api = new FakeAdminApiClient();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {} },
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
    navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  async function render() {
    api.listBlogPostsCalls.mockResolvedValue(pageOf([summary({ title: 'A Post' })]));
    const fixture = TestBed.createComponent(BlogPostListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('loads the first page on construction and renders a row per post', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    expect(api.listBlogPostsCalls.lastArgs).toEqual([
      {
        status: undefined,
        source: undefined,
        q: undefined,
        sort: ['created_at,desc'],
        page: 0,
        size: 20,
      },
    ]);
    expect(element.querySelectorAll('tbody tr').length).toBe(1);
    expect(element.textContent).toContain('A Post');
  });

  it('renders the mapped error message instead of a raw code on failure', async () => {
    api.listBlogPostsCalls.mockRejectedValue(new PlatformError('INTERNAL_ERROR', 'boom'));
    const fixture = TestBed.createComponent(BlogPostListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'An unexpected error occurred. Please try again.',
    );
  });

  it('navigates with the chosen status and resets the page, merging other query params', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    const select = element.querySelector('#posts-filter-status') as HTMLSelectElement;
    select.value = 'PUBLISHED';
    select.dispatchEvent(new Event('change'));

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { status: 'PUBLISHED', page: null },
        queryParamsHandling: 'merge',
      }),
    );
  });

  it('debounces search input before navigating', async () => {
    jest.useFakeTimers();
    try {
      const fixture = await render();
      const element = fixture.nativeElement as HTMLElement;
      const input = element.querySelector('#posts-filter-search') as HTMLInputElement;
      input.value = 'spring';
      input.dispatchEvent(new Event('input'));

      expect(navigateSpy).not.toHaveBeenCalled();
      jest.advanceTimersByTime(299);
      expect(navigateSpy).not.toHaveBeenCalled();
      jest.advanceTimersByTime(1);
      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({ queryParams: { q: 'spring', page: null } }),
      );
    } finally {
      jest.useRealTimers();
    }
  });

  it('navigates to the requested page from the pagination control', async () => {
    api.listBlogPostsCalls.mockResolvedValue({
      items: [summary({})],
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    });
    const fixture = TestBed.createComponent(BlogPostListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const nextButton = Array.from(element.querySelectorAll('nav button')).find((button) =>
      button.textContent?.includes('Next'),
    ) as HTMLButtonElement;
    nextButton.click();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: '1' } }),
    );
  });
});

/**
 * These tests drive the page through an actual `Router.navigateByUrl`, with
 * `withComponentInputBinding()` wired up exactly as `app.config.ts` wires it,
 * instead of relying on `input()`'s own defaults staying in place. A
 * component test that never navigates never observes what the router does
 * to a query parameter that is simply absent from the URL: it calls the
 * input's setter with `undefined` rather than leaving the default alone, and
 * only a real navigation to a bare `/admin/blog` reproduces that.
 */
describe('BlogPostListPage reached through real router navigation', () => {
  let api: FakeAdminApiClient;

  beforeEach(async () => {
    api = new FakeAdminApiClient();

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'admin/blog', component: BlogPostListPage }],
          withComponentInputBinding(),
        ),
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  it('loads the first page with no filters and shows no error banner on a bare /admin/blog', async () => {
    api.listBlogPostsCalls.mockResolvedValue(pageOf([summary({ title: 'A Post' })]));

    const harness = await RouterTestingHarness.create('/admin/blog');
    await harness.fixture.whenStable();
    harness.detectChanges();
    const root = harness.routeNativeElement as HTMLElement;

    expect(api.listBlogPostsCalls.lastArgs).toEqual([
      {
        status: undefined,
        source: undefined,
        q: undefined,
        sort: ['created_at,desc'],
        page: 0,
        size: 20,
      },
    ]);
    expect(root.querySelector('[role="alert"]')).toBeNull();
    const searchBox = root.querySelector('#posts-filter-search') as HTMLInputElement;
    expect(searchBox.value).not.toContain('undefined');
    expect(searchBox.value).toBe('');
  });
});
