import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

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

/**
 * These keys are not yet in the locale catalogue — this file's own screen
 * owns defining them, but only the shared four locale JSON files (owned by
 * another agent this wave) can actually carry them. Merging them into the
 * translate service here lets these tests assert on the real English text
 * the finished screen will show, without waiting on that other file to land.
 */
function setPendingTranslations(): void {
  const translate = TestBed.inject(TranslateService);
  const pairs: Record<string, string> = {
    'admin.editor.headingNew': 'New post',
    'admin.editor.headingEdit': 'Edit post',
    'admin.editor.slug': 'Slug',
    'admin.editor.title': 'Title',
    'admin.editor.sourceUrl': 'Source link',
    'admin.editor.body': 'Body',
    'admin.editor.preview': 'Preview',
    'admin.editor.save': 'Save',
    'admin.editor.saving': 'Saving…',
    'admin.editor.actionsHeading': 'Actions',
    'admin.editor.reasonLabel': 'Reason',
    'admin.editor.reasonPlaceholder': 'Explain why',
    'admin.editor.confirmAction': 'Confirm',
    'admin.editor.error.slugRequired': 'A slug is required.',
    'admin.editor.error.slugLength': 'The slug must be 3 to 80 characters.',
    'admin.editor.error.slugPattern':
      'The slug may only contain lowercase letters, digits and hyphens.',
    'admin.editor.error.titleRequired': 'A title is required.',
    'admin.editor.error.titleLength': 'The title must be 200 characters or fewer.',
    'admin.editor.error.bodyRequired': 'A body is required.',
    'admin.editor.error.bodyLength': 'The body must be 200,000 characters or fewer.',
    'admin.editor.error.sourceUrlLength': 'The source link must be 2,000 characters or fewer.',
    'admin.editor.error.sourceUrlInsecure': 'The source link must be an absolute https:// address.',
    'admin.editor.error.reasonTooShort': 'The reason must be at least 10 characters.',
    'admin.post.hint.autoBodyReadOnly':
      'This post was created automatically; only an administrator can edit its body.',
    'admin.post.hint.autoSourceUrlReadOnly':
      'The source link of an automatically created post cannot be changed.',
    'admin.post.action.submit': 'Submit',
    'admin.post.action.approve': 'Approve',
    'admin.post.action.reject': 'Reject',
    'admin.post.action.publish': 'Publish',
    'admin.post.action.unpublish': 'Unpublish',
    'admin.post.action.delete': 'Delete',
  };
  const root: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(pairs)) {
    const parts = key.split('.');
    let node = root;
    for (let index = 0; index < parts.length - 1; index += 1) {
      node = (node[parts[index]] ??= {}) as Record<string, unknown>;
    }
    node[parts.at(-1) as string] = value;
  }
  translate.setTranslation('en', root, true);
}

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
    setPendingTranslations();
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
});
