import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { ConnectivityService } from '../../core/net/connectivity.service';
import { PlatformError } from '../../core/platform/errors';
import type { BlogPostSummary, Page } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { BlogListCache } from './blog-list-cache';
import { BlogListPage } from './blog-list.page';

function summary(overrides: Partial<BlogPostSummary> = {}): BlogPostSummary {
  return {
    id: 'post-1',
    slug: 'spring-boot-4-1-1-released',
    title: 'Spring Boot 4.1.1 Released',
    excerpt: 'Spring Boot 4.1.1 ships 43 fixes.',
    source: 'AUTO',
    sourceUrl: 'https://www.spring.io/blog/2026/08/20/available-now',
    publishedAt: '2026-08-20T13:05:00.000Z',
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

function pageOf(
  items: readonly BlogPostSummary[],
  overrides: Partial<Page<BlogPostSummary>> = {},
): Page<BlogPostSummary> {
  return {
    items,
    page: 0,
    size: 10,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    ...overrides,
  };
}

describe('BlogListPage', () => {
  let platform: FakePlatformService;

  beforeEach(async () => {
    platform = new FakePlatformService();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {} },
        { provide: PlatformService, useValue: platform },
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
    const fixture = TestBed.createComponent(BlogListPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('reads the first page through the platform and renders a row per post', async () => {
    platform.blogList = pageOf([summary(), summary({ id: 'post-2', slug: 'other' })]);

    const element = (await render()).nativeElement as HTMLElement;

    expect(platform.blogListQueries).toEqual([{ page: 0, size: 10 }]);
    expect(element.querySelectorAll('[data-testid="blog-list"] li').length).toBe(2);
  });

  it('shows the host of a source link rather than the whole URL', async () => {
    platform.blogList = pageOf([summary()]);

    const element = (await render()).nativeElement as HTMLElement;
    const via = element.querySelector('[data-testid="blog-row-source"]') as HTMLElement;

    // The `www.` prefix identifies nothing, and the path is noise in a list row.
    expect(via.textContent).toContain('spring.io');
    expect(via.textContent).not.toContain('https://');
  });

  it('renders no source line for a post that carries no link', async () => {
    platform.blogList = pageOf([summary({ source: 'MANUAL', sourceUrl: null })]);

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-row-source"]')).toBeNull();
  });

  it('offers an empty state, not an error, when there is nothing published', async () => {
    platform.blogList = pageOf([]);

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-empty"]')).not.toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });

  it('treats a server failure as an error, never as being offline', async () => {
    // A failing server is not an absent network. Saying "you are offline"
    // here is the bug this branch exists to prevent.
    platform.listBlogPosts = async () => {
      throw new PlatformError('SERVICE_UNAVAILABLE', 'down');
    };

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-error"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });

  it('treats a rate limit as an error, never as being offline', async () => {
    platform.listBlogPosts = async () => {
      throw new PlatformError('RATE_LIMITED', 'slow down');
    };

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-error"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });

  it('shows the offline state, with a way out, when nothing answered', async () => {
    platform.listBlogPosts = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'unreachable');
    };

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-offline"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="blog-error"]')).toBeNull();
    expect(element.querySelector('[data-testid="blog-offline-retry"]')).not.toBeNull();
    const library = element.querySelector(
      '[data-testid="blog-offline"] a',
    ) as HTMLAnchorElement | null;
    expect(library?.getAttribute('href')).toBe('/tracks');
  });

  it('keeps showing the last list it read, dated, when a later read cannot reach the server', async () => {
    platform.blogList = pageOf([summary()]);
    await render();

    TestBed.inject(PlatformService).listBlogPosts = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'unreachable');
    };
    const second = await render();
    const element = second.nativeElement as HTMLElement;

    expect(element.querySelectorAll('[data-testid="blog-list"] li').length).toBe(1);
    expect(element.querySelector('[data-testid="blog-stale-band"]')).not.toBeNull();
    // The cached list replaces the offline panel; it is not shown beside it.
    expect(element.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });

  it('keeps the band when the page it has to fall back on was read empty', async () => {
    // The band is the only thing that tells a page read before an outage
    // apart from a server that has published nothing, so it cannot be the
    // thing that goes missing exactly when the page in hand is empty.
    platform.blogList = pageOf([]);
    await render();

    TestBed.inject(PlatformService).listBlogPosts = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'unreachable');
    };
    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-stale-band"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="blog-offline"]')).not.toBeNull();
    expect(element.querySelector('[data-testid="blog-empty"]')).toBeNull();
  });

  it('stops presenting an empty list as current once the server cannot be reached', async () => {
    // The read succeeded and the blog really was empty. What changes is the
    // connection, not the answer, and from that moment "there are no posts to
    // show yet" is a claim the screen has no way to stand behind.
    platform.blogList = pageOf([]);
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="blog-stale-band"]')).toBeNull();

    TestBed.inject(ConnectivityService).reportUnreachable();
    fixture.detectChanges();

    expect(element.querySelector('[data-testid="blog-stale-band"]')).not.toBeNull();
  });

  it('reads the list again by itself when the server comes back', async () => {
    platform.blogList = pageOf([]);
    const fixture = await render();
    const connectivity = TestBed.inject(ConnectivityService);

    connectivity.reportUnreachable();
    fixture.detectChanges();
    connectivity.reportReachable();
    fixture.detectChanges();
    await fixture.whenStable();

    // Recovering must not depend on the reader leaving the screen and
    // coming back to it.
    expect(platform.blogListQueries.length).toBe(2);
  });

  it('offers a way to re-read the list without leaving the screen', async () => {
    platform.blogList = pageOf([summary()]);
    const fixture = await render();
    TestBed.inject(ConnectivityService).reportUnreachable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const retry = element.querySelector(
      '[data-testid="blog-stale-retry"]',
    ) as HTMLButtonElement | null;
    expect(retry).not.toBeNull();

    retry?.click();
    await fixture.whenStable();

    expect(platform.blogListQueries.length).toBe(2);
  });

  it('dates nothing while the list on screen is the one it just read', async () => {
    platform.blogList = pageOf([summary()]);

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="blog-stale-band"]')).toBeNull();
  });

  it('hides paging while there is only one page of posts', async () => {
    platform.blogList = pageOf([summary()]);

    const element = (await render()).nativeElement as HTMLElement;

    expect(element.querySelector('app-pagination')).toBeNull();
  });

  it('puts the chosen page in the URL rather than in its own state', async () => {
    platform.blogList = pageOf([summary()], { totalElements: 24, totalPages: 3 });
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const element = (await render()).nativeElement as HTMLElement;
    const next = Array.from(element.querySelectorAll('app-pagination button')).at(
      -1,
    ) as HTMLButtonElement;
    next.click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: '1' }, queryParamsHandling: 'merge' }),
    );
  });
});

/**
 * These drive the page through a real navigation with the same input binding
 * `app.config.ts` installs, because that is the only way to observe what the
 * router does with a query parameter that is simply absent: it calls the
 * input's transform with `undefined` instead of leaving the declared default
 * in place. A test that sets the input itself never takes that path, and the
 * screen it never takes is the one every reader arrives at first.
 */
describe('BlogListPage reached through real router navigation', () => {
  let platform: FakePlatformService;

  beforeEach(async () => {
    platform = new FakePlatformService();
    platform.blogList = {
      items: [summary()],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'blog', component: BlogListPage }], withComponentInputBinding()),
        { provide: PlatformService, useValue: platform },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function open(url: string) {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness;
  }

  it('asks for the first page on a bare /blog and renders the list', async () => {
    const harness = await open('/blog');
    const root = harness.routeNativeElement as HTMLElement;

    expect(platform.blogListQueries).toEqual([{ page: 0, size: 10 }]);
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.querySelectorAll('[data-testid="blog-list"] li').length).toBe(1);
  });

  it('reads nothing again when the entry for the page on screen is selected once more', async () => {
    await open('/blog');
    await TestBed.inject(Router).navigateByUrl('/blog');

    // The router treats a navigation to the URL it is already on as nothing
    // to do, so the obvious way to ask for a fresh read -- selecting the same
    // navigation entry again -- reaches neither this component nor the
    // server, and whatever the screen was showing keeps standing. That is
    // what the control on the band exists for, and this records why.
    expect(platform.blogListQueries).toEqual([{ page: 0, size: 10 }]);
  });

  it('honours a page carried in the URL', async () => {
    await open('/blog?page=2');
    expect(platform.blogListQueries).toEqual([{ page: 2, size: 10 }]);
  });

  it('falls back to the first page for a value that is not one', async () => {
    await open('/blog?page=not-a-number');
    expect(platform.blogListQueries).toEqual([{ page: 0, size: 10 }]);
  });

  it('falls back to the first page for a negative index', async () => {
    // The server would reject it; a reader who typed it wanted a list.
    await open('/blog?page=-3');
    expect(platform.blogListQueries).toEqual([{ page: 0, size: 10 }]);
  });
});

describe('BlogListCache', () => {
  it('returns nothing for a page it never saw', () => {
    expect(new BlogListCache().read(0)).toBeNull();
  });

  it('keys what it holds by the page that was asked for', () => {
    const cache = new BlogListCache();
    const list: Page<BlogPostSummary> = {
      items: [summary()],
      // A server that echoed a different index must not decide where this
      // lands, or a later read for the same page would miss it.
      page: 0,
      size: 10,
      totalElements: 30,
      totalPages: 3,
    };
    cache.store(2, list, '2026-09-07T10:00:00.000Z');

    expect(cache.read(2)).toEqual({ list, loadedAt: '2026-09-07T10:00:00.000Z' });
    expect(cache.read(0)).toBeNull();
  });
});
