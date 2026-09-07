import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakeAuthSession } from '../../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { AdminBlogPost, ReviewDetail, VerifyCheck } from '../../../core/admin/admin-models';
import { AuthSession } from '../../../core/auth/auth-session';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformService } from '../../../core/platform/platform.service';
import { ReviewDetailPage } from './review-detail.page';

/**
 * Some of the keys this page uses (`admin.review.approve`,
 * `admin.review.editorReadOnlyNote`, `admin.review.noSourceUpdate`, ...) are
 * not in the locale catalogue yet — a parallel pass adds them to all four
 * locale files. This file must stay green with no changes once that lands,
 * so it never asserts against the *text* those keys currently render as
 * (the raw key, per `DefaultMissingTranslationHandler`) or will render as
 * once translated: wherever only a branch's presence matters, it is found by
 * `data-testid`/`id`, not by content. Real English text is asserted only for
 * keys already stable in `assets/i18n/en.json` before this task
 * (`admin.review.check.*`, `admin.review.verifyStatus.*`, `error.*`,
 * `admin.post.source.*`, `admin.common.*`).
 */

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

function check(overrides: Partial<VerifyCheck> = {}): VerifyCheck {
  return { check: 'SOURCE_WHITELISTED', passed: true, detail: null, ...overrides };
}

function withSourceUpdate(): ReviewDetail {
  return {
    post: post(),
    sourceUpdate: {
      id: 'update-1',
      whitelistSource: {
        id: 'source-1',
        name: 'Spring Blog',
        feedUrl: 'https://spring.io/blog.atom',
      },
      versionString: '4.1.1',
      contentHash: '9f2c1b7d5a3e08c4b6d1f0a29e7c48b53d61fa0c8e29d7b4a51c30f6e8d92b17',
      fetchedAt: '2026-08-20T11:58:42.310Z',
      verifyStatus: 'VERIFIED',
      verifyChecks: [
        check({ check: 'SOURCE_WHITELISTED', passed: true }),
        check({ check: 'VERSION_CONFIRMED', passed: false, detail: 'HTTP 404 from verify URL' }),
        check({ check: 'HASH_NOT_SEEN', passed: false, detail: 'Already seen' }),
        check({ check: 'CONTENT_SANITY', passed: true }),
      ],
      rawContent: 'Spring Boot 4.1.1 has been released and is available from Maven Central.',
    },
  };
}

function withoutSourceUpdate(): ReviewDetail {
  return { post: post({ source: 'MANUAL', sourceUpdateId: null }), sourceUpdate: null };
}

describe('ReviewDetailPage', () => {
  let api: FakeAdminApiClient;
  let session: FakeAuthSession;

  beforeEach(async () => {
    api = new FakeAdminApiClient();
    session = new FakeAuthSession();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AdminApiClient, useValue: api },
        { provide: AuthSession, useValue: session },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({ loader: BundledTranslateLoader, fallbackLang: 'en', lang: 'en' }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function render(detail: ReviewDetail) {
    api.getReviewDetailCalls.mockResolvedValue(detail);
    const fixture = TestBed.createComponent(ReviewDetailPage);
    fixture.componentRef.setInput('postId', detail.post.id);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renders the raw source and the generated draft as two panels, plus the diff and verify chain', async () => {
    session.setRole('ADMIN');
    const fixture = await render(withSourceUpdate());
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Spring Boot 4.1.1 has been released');
    expect(element.textContent).toContain('4.1.1');
    expect(element.querySelector('app-diff-view')).not.toBeNull();
    expect(element.querySelector('app-verify-checks')).not.toBeNull();
  });

  it('highlights the first failing verification check, not every failing one', async () => {
    session.setRole('ADMIN');
    const fixture = await render(withSourceUpdate());
    const element = fixture.nativeElement as HTMLElement;

    // VERSION_CONFIRMED is the first failure in execution order; HASH_NOT_SEEN
    // also fails but is not the reason the update was rejected — only the
    // first failing row gets the callout. Rows are found by the check they
    // report rather than by their rendered wording, which is translated and
    // moves independently of this behaviour.
    const rowFor = (check: string) =>
      element.querySelector(`[data-testid="verify-check"][data-check="${check}"]`);
    expect(
      rowFor('VERSION_CONFIRMED')?.querySelector('[data-testid="first-failing-note"]'),
    ).not.toBeNull();
    expect(rowFor('HASH_NOT_SEEN')?.querySelector('[data-testid="first-failing-note"]')).toBeNull();
  });

  it('shows an editor a read-only note instead of the approve/reject controls', async () => {
    session.setRole('EDITOR');
    const fixture = await render(withSourceUpdate());
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[data-testid="review-editor-note"]')).not.toBeNull();
    expect(element.querySelector('#review-reason')).toBeNull();
    expect(element.querySelector('#review-approve-button')).toBeNull();
    expect(element.querySelector('#review-reject-button')).toBeNull();
  });

  it('renders one panel and an explanatory note when the post has no source update', async () => {
    session.setRole('ADMIN');
    const fixture = await render(withoutSourceUpdate());
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('app-diff-view')).toBeNull();
    expect(element.querySelector('app-verify-checks')).toBeNull();
    expect(element.querySelector('[data-testid="review-no-source-update"]')).not.toBeNull();
  });

  describe('the reason panel (ADMIN only)', () => {
    async function renderAsAdmin() {
      session.setRole('ADMIN');
      const fixture = await render(withSourceUpdate());
      const element = fixture.nativeElement as HTMLElement;
      return {
        fixture,
        element,
        textarea: element.querySelector('#review-reason') as HTMLTextAreaElement,
        approveButton: element.querySelector('#review-approve-button') as HTMLButtonElement,
        rejectButton: element.querySelector('#review-reject-button') as HTMLButtonElement,
      };
    }

    function type(
      fixture: Awaited<ReturnType<typeof render>>,
      textarea: HTMLTextAreaElement,
      value: string,
    ) {
      textarea.value = value;
      textarea.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('leaves approve enabled with an empty reason, and never sends an empty reason field', async () => {
      const { fixture, approveButton } = await renderAsAdmin();

      // `reasonRequired('approve')` is false (`blog-lifecycle.ts`): an
      // administrator can approve with no note at all.
      expect(approveButton.getAttribute('aria-disabled')).toBeNull();

      api.transitionBlogPostCalls.mockResolvedValue(post({ status: 'PUBLISHED' }));
      approveButton.click();
      await fixture.whenStable();

      const [, , input] = api.transitionBlogPostCalls.lastArgs!;
      expect(input).toEqual({ expectedStatus: 'PENDING_REVIEW' });
      expect('reason' in input).toBe(false);
    });

    it('marks approve unavailable once a reason is started but is still too short', async () => {
      const { fixture, textarea, approveButton } = await renderAsAdmin();

      type(fixture, textarea, 'short');
      expect(approveButton.getAttribute('aria-disabled')).toBe('true');
    });

    it('refuses an approve pressed while the reason is too short, and keeps its focus', async () => {
      const { fixture, textarea, approveButton } = await renderAsAdmin();

      type(fixture, textarea, 'short');
      approveButton.focus();
      approveButton.click();
      await fixture.whenStable();

      // The control is announced as unavailable, not made inert, so the click
      // reaches the handler and the handler is what refuses it.
      expect(api.transitionBlogPostCalls.calls.length).toBe(0);
      // And because it is not inert it still holds the focus, instead of
      // having dropped it to the document body.
      expect(document.activeElement).toBe(approveButton);
    });

    it('keeps approve focused while its decision is in flight, and refuses a second press', async () => {
      const { fixture, approveButton } = await renderAsAdmin();
      // A transition that never settles, so the in-flight state can be
      // inspected.
      const transition = jest
        .spyOn(api, 'transitionBlogPost')
        .mockReturnValue(new Promise<never>(() => undefined));

      approveButton.focus();
      approveButton.click();
      fixture.detectChanges();

      expect(approveButton.getAttribute('aria-disabled')).toBe('true');
      expect(document.activeElement).toBe(approveButton);

      approveButton.click();
      fixture.detectChanges();

      expect(transition).toHaveBeenCalledTimes(1);
      expect(document.activeElement).toBe(approveButton);
    });

    it('re-enables approve once the reason reaches the minimum length, and sends it', async () => {
      const { fixture, textarea, approveButton } = await renderAsAdmin();

      type(fixture, textarea, 'Version string verified against the release tag.');
      expect(approveButton.getAttribute('aria-disabled')).toBeNull();

      api.transitionBlogPostCalls.mockResolvedValue(post({ status: 'PUBLISHED' }));
      approveButton.click();
      await fixture.whenStable();

      expect(api.transitionBlogPostCalls.lastArgs).toEqual([
        'post-1',
        'approve',
        {
          expectedStatus: 'PENDING_REVIEW',
          reason: 'Version string verified against the release tag.',
        },
      ]);
    });

    it('keeps reject unavailable with an empty reason, unlike approve', async () => {
      const { approveButton, rejectButton } = await renderAsAdmin();

      // `reasonRequired('reject')` is true: a rejection with nothing
      // recorded in the audit log is exactly what this panel prevents.
      expect(rejectButton.getAttribute('aria-disabled')).toBe('true');
      expect(approveButton.getAttribute('aria-disabled')).toBeNull();
    });

    it('enables reject once a real reason is entered, and sends it', async () => {
      const { fixture, textarea, rejectButton } = await renderAsAdmin();

      type(fixture, textarea, 'HTTP 404 from the verify URL, could not confirm the version.');
      expect(rejectButton.getAttribute('aria-disabled')).toBeNull();

      api.transitionBlogPostCalls.mockResolvedValue(post({ status: 'REJECTED' }));
      rejectButton.click();
      await fixture.whenStable();

      expect(api.transitionBlogPostCalls.lastArgs).toEqual([
        'post-1',
        'reject',
        {
          expectedStatus: 'PENDING_REVIEW',
          reason: 'HTTP 404 from the verify URL, could not confirm the version.',
        },
      ]);
    });
  });
});
