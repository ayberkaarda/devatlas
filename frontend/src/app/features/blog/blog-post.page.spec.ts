import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformError } from '../../core/platform/errors';
import type { BlogPost } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { ThemeService } from '../../core/theme/theme.service';
import { BlogPostPage } from './blog-post.page';

function post(overrides: Partial<BlogPost> = {}): BlogPost {
  return {
    id: 'post-1',
    slug: 'spring-boot-4-1-1-released',
    title: 'Spring Boot 4.1.1 Released',
    bodyMarkdown: '## Highlights\n\n- 43 bug fixes\n',
    source: 'AUTO',
    sourceUrl: 'https://spring.io/blog/2026/08/20/available-now',
    publishedAt: '2026-08-20T13:05:00.000Z',
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
    ...overrides,
  };
}

describe('BlogPostPage', () => {
  let platform: FakePlatformService;

  beforeEach(async () => {
    platform = new FakePlatformService();

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'blog/:slug', component: BlogPostPage }],
          withComponentInputBinding(),
        ),
        { provide: PlatformService, useValue: platform },
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

  /**
   * Every case goes through a real navigation, because a deep link is the
   * point of addressing a post by slug: somebody has to be able to paste the
   * URL and land on the post.
   */
  async function open(url: string) {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    // Markdown rendering resolves a promise chain of its own after the read
    // settles; a macrotask boundary lets the rest of it drain.
    await new Promise((resolve) => setTimeout(resolve, 0));
    harness.detectChanges();
    return harness;
  }

  it('opens the post named by the URL and renders its body as markup, not as source', async () => {
    platform.blogPosts.set('spring-boot-4-1-1-released', post());

    const harness = await open('/blog/spring-boot-4-1-1-released');
    const root = harness.routeNativeElement as HTMLElement;

    expect(root.querySelector('h1')?.textContent).toContain('Spring Boot 4.1.1 Released');
    const body = root.querySelector('app-markdown-view .markdown-body') as HTMLElement;
    // Shifted down a level by the renderer so it nests under the post title
    // above rather than competing with it.
    expect(body.querySelector('h3')).not.toBeNull();
    expect(body.querySelector('h1')).toBeNull();
    expect(body.querySelectorAll('li').length).toBe(1);
  });

  it('renders the source link above the body, as a link a reader can follow', async () => {
    platform.blogPosts.set('spring-boot-4-1-1-released', post());

    const harness = await open('/blog/spring-boot-4-1-1-released');
    const root = harness.routeNativeElement as HTMLElement;

    const panel = root.querySelector('[data-testid="blog-source"]') as HTMLElement;
    const link = panel.querySelector('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('https://spring.io/blog/2026/08/20/available-now');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link.textContent).toContain('spring.io');

    // Prominent means before the body, not a footnote after it.
    const article = root.querySelector('article') as HTMLElement;
    const view = article.querySelector('app-markdown-view') as HTMLElement;
    expect(panel.compareDocumentPosition(view) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders no source panel for a post that has no source link', async () => {
    platform.blogPosts.set('hand-written', post({ source: 'MANUAL', sourceUrl: null }));

    const harness = await open('/blog/hand-written');
    const root = harness.routeNativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="blog-source"]')).toBeNull();
  });

  it('marks a post that came back in English because no translation exists', async () => {
    platform.blogPosts.set(
      'spring-boot-4-1-1-released',
      post({ translation: { locale: 'en', requestedLocale: 'tr', isFallback: true } }),
    );

    const harness = await open('/blog/spring-boot-4-1-1-released');
    const root = harness.routeNativeElement as HTMLElement;

    expect(root.querySelector('app-fallback-badge span')).not.toBeNull();
  });

  it('explains an unpublished or unknown slug rather than showing the raw code', async () => {
    const harness = await open('/blog/never-published');
    const root = harness.routeNativeElement as HTMLElement;

    const alert = root.querySelector('[data-testid="blog-error"]') as HTMLElement;
    // Taken from the catalogue rather than written here, so the assertion
    // survives a rewording. The second expectation keeps the first honest: a
    // missing key resolves to the key itself, and the two would then agree
    // while the screen showed a raw code.
    const message = TestBed.inject(TranslateService).instant('error.BLOG_POST_NOT_FOUND');
    expect(message).not.toBe('error.BLOG_POST_NOT_FOUND');
    expect(alert.textContent).toContain(message);
    expect(root.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });

  it('shows the offline state when nothing answered at all', async () => {
    platform.getBlogPost = async () => {
      throw new PlatformError('NETWORK_UNAVAILABLE', 'unreachable');
    };

    const harness = await open('/blog/spring-boot-4-1-1-released');
    const root = harness.routeNativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="blog-offline"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="blog-error"]')).toBeNull();
  });

  it('treats a failing server as an error rather than as an absent network', async () => {
    platform.getBlogPost = async () => {
      throw new PlatformError('INTERNAL_ERROR', 'boom');
    };

    const harness = await open('/blog/spring-boot-4-1-1-released');
    const root = harness.routeNativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="blog-error"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="blog-offline"]')).toBeNull();
  });
});
