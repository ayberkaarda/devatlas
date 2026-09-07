import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakeAuthSession } from '../../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import type { AdminBlogPost } from '../../../core/admin/admin-models';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import { AuthSession } from '../../../core/auth/auth-session';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformError } from '../../../core/platform/errors';
import { PlatformService } from '../../../core/platform/platform.service';
import { BlogPostEditorPage } from './blog-post-editor.page';

function post(overrides: Partial<AdminBlogPost>): AdminBlogPost {
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

describe('BlogPostEditorPage', () => {
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
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
    jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    jest.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  async function render(id: string | undefined, role: 'EDITOR' | 'ADMIN' = 'ADMIN') {
    session.setRole(role);
    const fixture = TestBed.createComponent(BlogPostEditorPage);
    if (id !== undefined) {
      fixture.componentRef.setInput('id', id);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function value(fixture: Awaited<ReturnType<typeof render>>, selector: string): string {
    return (
      (fixture.nativeElement as HTMLElement).querySelector(selector) as
        HTMLInputElement | HTMLTextAreaElement
    ).value;
  }

  it('loads an existing post and fills the form from it', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ slug: 'existing', title: 'Existing Post' }));
    const fixture = await render('post-1');

    expect(value(fixture, '#editor-slug')).toBe('existing');
    expect(value(fixture, '#editor-title')).toBe('Existing Post');
  });

  it('keeps the body and source url editable for an editor on a MANUAL post', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ source: 'MANUAL' }));
    const fixture = await render('post-1', 'EDITOR');
    const element = fixture.nativeElement as HTMLElement;

    expect((element.querySelector('#editor-body') as HTMLTextAreaElement).disabled).toBe(false);
    expect((element.querySelector('#editor-source-url') as HTMLInputElement).disabled).toBe(false);
  });

  it('makes the body and source url readonly for an editor on an AUTO post, and hides approve/reject', async () => {
    api.getBlogPostCalls.mockResolvedValue(
      post({ source: 'AUTO', status: 'PENDING_REVIEW', sourceUrl: 'https://example.test/a' }),
    );
    const fixture = await render('post-1', 'EDITOR');
    const element = fixture.nativeElement as HTMLElement;

    expect((element.querySelector('#editor-body') as HTMLTextAreaElement).disabled).toBe(true);
    expect((element.querySelector('#editor-source-url') as HTMLInputElement).disabled).toBe(true);
    expect(element.textContent).not.toContain('admin.post.action.approve');
    const actionButtons = Array.from(element.querySelectorAll('section button')).map((button) =>
      button.textContent?.trim(),
    );
    expect(actionButtons).not.toContain('Approve');
    expect(actionButtons).not.toContain('Reject');
  });

  it('lets an admin edit the body of an AUTO post', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ source: 'AUTO', status: 'PENDING_REVIEW' }));
    const fixture = await render('post-1', 'ADMIN');
    const element = fixture.nativeElement as HTMLElement;

    expect((element.querySelector('#editor-body') as HTMLTextAreaElement).disabled).toBe(false);
    const actionButtons = Array.from(element.querySelectorAll('section button')).map((button) =>
      button.textContent?.trim(),
    );
    expect(actionButtons).toContain('Approve');
    expect(actionButtons).toContain('Reject');
  });

  it('requires at least 10 characters of reason before the reject confirm button is enabled', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ status: 'PENDING_REVIEW' }));
    const fixture = await render('post-1', 'ADMIN');
    const element = fixture.nativeElement as HTMLElement;

    const rejectButton = Array.from(element.querySelectorAll('section button')).find(
      (button) => button.textContent?.trim() === 'Reject',
    ) as HTMLButtonElement;
    rejectButton.click();
    fixture.detectChanges();

    const confirmButton = element
      .querySelector('#editor-reason')!
      .closest('div')!
      .querySelector('button') as HTMLButtonElement;
    expect(confirmButton.disabled).toBe(true);

    const textarea = element.querySelector('#editor-reason') as HTMLTextAreaElement;
    textarea.value = 'a very good reason';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(confirmButton.disabled).toBe(false);
  });

  it('preserves form values and refreshes the version on a VERSION_CONFLICT', async () => {
    api.getBlogPostCalls.mockResolvedValueOnce(post({ version: 1 }));
    const fixture = await render('post-1', 'ADMIN');
    const element = fixture.nativeElement as HTMLElement;

    const titleInput = element.querySelector('#editor-title') as HTMLInputElement;
    titleInput.value = 'A New Unsaved Title';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    api.updateBlogPostCalls.mockRejectedValueOnce(
      new PlatformError('VERSION_CONFLICT', 'stale version'),
    );
    api.getBlogPostCalls.mockResolvedValueOnce(
      post({ version: 2, title: 'Someone Else Renamed It' }),
    );

    const form = element.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(value(fixture, '#editor-title')).toBe('A New Unsaved Title');
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Someone else changed this while you were editing it.',
    );
  });

  it('renders a script tag in the preview as inert text, not markup', async () => {
    api.getBlogPostCalls.mockResolvedValue(
      post({ bodyMarkdown: '<script>window.x = 1;</script>' }),
    );
    const fixture = await render('post-1', 'ADMIN');

    const preview = (fixture.nativeElement as HTMLElement).querySelector(
      '.markdown-body',
    ) as HTMLElement;
    expect(preview.querySelector('script')).toBeNull();
  });
  it('keeps the screen heading above the error card when the load fails', async () => {
    api.getBlogPostCalls.mockRejectedValue(new PlatformError('INTERNAL_ERROR', 'Boom.'));
    const fixture = await render('post-1');
    const element = fixture.nativeElement as HTMLElement;

    // The wording of the heading never depended on the fetch, so a failed
    // fetch should not leave the document starting at its second level.
    expect(element.querySelectorAll('h1')).toHaveLength(1);
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('ties each field error to the field it is about', async () => {
    const fixture = await render(undefined);
    const element = fixture.nativeElement as HTMLElement;

    (element.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    fixture.detectChanges();

    const slug = element.querySelector('#editor-slug') as HTMLInputElement;
    expect(slug.getAttribute('aria-invalid')).toBe('true');
    // An error announced but not associated is read as "invalid" with no
    // reason when focus lands on the field.
    const describedBy = slug.getAttribute('aria-describedby');
    expect(describedBy).not.toBeNull();
    expect(element.querySelector('#' + describedBy)).not.toBeNull();
  });

  it('says so in a live region when a save succeeds', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ slug: 'existing', title: 'Existing' }));
    api.updateBlogPostCalls.mockResolvedValue(post({ slug: 'existing', title: 'Existing' }));
    const fixture = await render('post-1');
    const element = fixture.nativeElement as HTMLElement;

    const region = element.querySelector('[data-testid="editor-outcome"]') as HTMLElement;
    // Present before the change, which is what makes it a live region rather
    // than a paragraph that appears.
    expect(region.getAttribute('role')).toBe('status');
    expect(region.textContent?.trim()).toBe('');

    (element.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(region.textContent?.trim()).not.toBe('');
  });

  it('moves focus into the reason panel and back to the button that opened it', async () => {
    api.getBlogPostCalls.mockResolvedValue(post({ status: 'PENDING_REVIEW', source: 'MANUAL' }));
    const fixture = await render('post-1', 'ADMIN');
    const element = fixture.nativeElement as HTMLElement;

    const reject = element.querySelector('[data-action="reject"]') as HTMLButtonElement;
    expect(reject).not.toBeNull();
    reject.focus();
    reject.click();
    fixture.detectChanges();

    // Opening the panel destroys the button that was focused, and a destroyed
    // element takes the focus with it to the top of the document.
    expect(document.activeElement).toBe(element.querySelector('#editor-reason'));

    const cancel = Array.from(element.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Cancel',
    ) as HTMLButtonElement;
    cancel.click();
    fixture.detectChanges();

    expect(document.activeElement).toBe(element.querySelector('[data-action="reject"]'));
  });
});
