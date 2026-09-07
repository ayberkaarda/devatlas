import {
  ChangeDetectionStrategy,
  Component,
  type ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { AdminBlogPost, UpdateBlogPostInput } from '../../../core/admin/admin-models';
import { AuthSession } from '../../../core/auth/auth-session';
import { errorKey } from '../../../core/platform/error-key';
import { PlatformError } from '../../../core/platform/errors';
import { MarkdownView } from '../../../shared/markdown-view';
import { StatusBadge } from '../../../shared/status-badge';
import {
  availableActions,
  canEditBody,
  canEditSourceUrl,
  reasonRequired,
  type LifecycleAction,
} from './blog-lifecycle';

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const SLUG_MIN_LENGTH = 3;
const SLUG_MAX_LENGTH = 80;
const TITLE_MAX_LENGTH = 200;
const BODY_MAX_LENGTH = 200_000;
const SOURCE_URL_MAX_LENGTH = 2000;
const MIN_REASON_LENGTH = 10;

/**
 * Create-or-edit screen for one blog post.
 *
 * With no `:id` (the `blog/new` route) this is a create form for a `MANUAL`
 * draft — the only kind `AdminApiClient.createBlogPost` can produce, the
 * server never accepts `source` from a client. With an `:id` it loads that
 * post and becomes an editor plus the lifecycle action bar
 * (`blog-lifecycle.ts`) for whatever transitions the signed-in role can see
 * on it.
 */
@Component({
  selector: 'app-blog-post-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, MarkdownView, StatusBadge],
  templateUrl: './blog-post-editor.page.html',
})
export class BlogPostEditorPage {
  private readonly api = inject(AdminApiClient);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  /** Absent on the `blog/new` route; present on `blog/:id`. */
  readonly id = input<string>();

  protected readonly isCreateMode = computed(() => this.id() === undefined);

  protected readonly loading = signal(true);
  protected readonly loadFailureKey = signal<string | null>(null);

  /** The last-loaded/last-saved server record; `null` in create mode until the first save. */
  protected readonly post = signal<AdminBlogPost | null>(null);
  private loadedId: string | null = null;

  // ---- Form fields, independent of `post` so a version conflict never overwrites them ----------
  protected readonly slug = signal('');
  protected readonly title = signal('');
  protected readonly bodyMarkdown = signal('');
  protected readonly sourceUrl = signal('');
  protected readonly attempted = signal(false);

  protected readonly saving = signal(false);
  protected readonly saveFailureKey = signal<string | null>(null);

  /**
   * What just went right, for the polite live region in the template.
   *
   * A save in edit mode changes nothing a reader can perceive: the record is
   * replaced by an identical-looking one and the button label flips back. A
   * transition changes a status badge somewhere else on the screen. Both are
   * outcomes of an action the person took, and neither of them said so.
   */
  protected readonly outcomeKey = signal<string | null>(null);

  private readonly reasonField = viewChild<ElementRef<HTMLTextAreaElement>>('reasonField');
  private readonly actionGroup = viewChild<ElementRef<HTMLElement>>('actionGroup');

  /**
   * The action whose button opened the reason panel, held so focus can be put
   * back on it. Opening the panel destroys that button, and a destroyed
   * element takes the focus with it to the top of the document.
   */
  private readonly focusActionAfterCancel = signal<LifecycleAction | null>(null);

  protected readonly role = computed(() => this.session.role());

  protected readonly canEditBodyNow = computed(() => {
    const currentPost = this.post();
    const role = this.role();
    if (currentPost === null || role === null) {
      return true;
    }
    return canEditBody(currentPost, role);
  });

  protected readonly canEditSourceUrlNow = computed(() => {
    const currentPost = this.post();
    if (currentPost === null) {
      return true;
    }
    return canEditSourceUrl(currentPost);
  });

  protected readonly formEditable = computed(
    () => this.isCreateMode() || this.canEditBodyNow() || this.canEditSourceUrlNow(),
  );

  protected readonly actions = computed<readonly LifecycleAction[]>(() => {
    const currentPost = this.post();
    const role = this.role();
    if (currentPost === null || role === null) {
      return [];
    }
    return availableActions(currentPost, role);
  });

  // ---- Validation --------------------------------------------------------------------------

  protected readonly slugErrorKey = computed<string | null>(() => {
    const value = this.slug().trim();
    if (value.length === 0) {
      return 'admin.editor.error.slugRequired';
    }
    if (value.length < SLUG_MIN_LENGTH || value.length > SLUG_MAX_LENGTH) {
      return 'admin.editor.error.slugLength';
    }
    if (!SLUG_PATTERN.test(value)) {
      return 'admin.editor.error.slugPattern';
    }
    return null;
  });

  protected readonly titleErrorKey = computed<string | null>(() => {
    const value = this.title().trim();
    if (value.length === 0) {
      return 'admin.editor.error.titleRequired';
    }
    if (value.length > TITLE_MAX_LENGTH) {
      return 'admin.editor.error.titleLength';
    }
    return null;
  });

  protected readonly bodyErrorKey = computed<string | null>(() => {
    const value = this.bodyMarkdown();
    if (value.trim().length === 0) {
      return 'admin.editor.error.bodyRequired';
    }
    if (value.length > BODY_MAX_LENGTH) {
      return 'admin.editor.error.bodyLength';
    }
    return null;
  });

  protected readonly sourceUrlErrorKey = computed<string | null>(() => {
    const value = this.sourceUrl().trim();
    if (value.length === 0) {
      return null;
    }
    if (value.length > SOURCE_URL_MAX_LENGTH) {
      return 'admin.editor.error.sourceUrlLength';
    }
    if (!isAbsoluteHttpsUrl(value)) {
      return 'admin.editor.error.sourceUrlInsecure';
    }
    return null;
  });

  protected readonly formValid = computed(
    () =>
      this.slugErrorKey() === null &&
      this.titleErrorKey() === null &&
      this.bodyErrorKey() === null &&
      this.sourceUrlErrorKey() === null,
  );

  // ---- Lifecycle actions --------------------------------------------------------------------

  protected readonly pendingAction = signal<LifecycleAction | null>(null);
  protected readonly reasonDraft = signal('');
  protected readonly transitioning = signal(false);
  protected readonly transitionFailureKey = signal<string | null>(null);

  protected readonly reasonTooShort = computed(
    () => this.reasonDraft().trim().length < MIN_REASON_LENGTH,
  );

  constructor() {
    // Focus follows the panel, in both directions. The reference is a signal,
    // so the effect runs again once the @if has actually put the textarea in
    // the document rather than at the moment the flag was set.
    effect(() => {
      const field = this.reasonField();
      if (this.pendingAction() !== null && field) {
        field.nativeElement.focus();
      }
    });

    effect(() => {
      const action = this.focusActionAfterCancel();
      const group = this.actionGroup();
      if (action === null || !group) {
        return;
      }
      this.focusActionAfterCancel.set(null);
      group.nativeElement.querySelector<HTMLButtonElement>(`[data-action="${action}"]`)?.focus();
    });

    effect(() => {
      const id = this.id();
      if (id === undefined) {
        this.loading.set(false);
        return;
      }
      if (id === this.loadedId) {
        return;
      }
      void this.loadExisting(id);
    });
  }

  protected retryLoad(): void {
    const id = this.id();
    if (id !== undefined) {
      void this.loadExisting(id);
    }
  }

  protected onSlugInput(event: Event): void {
    this.slug.set((event.target as HTMLInputElement).value);
  }

  protected onTitleInput(event: Event): void {
    this.title.set((event.target as HTMLInputElement).value);
  }

  protected onBodyInput(event: Event): void {
    this.bodyMarkdown.set((event.target as HTMLTextAreaElement).value);
  }

  protected onSourceUrlInput(event: Event): void {
    this.sourceUrl.set((event.target as HTMLInputElement).value);
  }

  protected onReasonInput(event: Event): void {
    this.reasonDraft.set((event.target as HTMLTextAreaElement).value);
  }

  protected async onAction(action: LifecycleAction): Promise<void> {
    this.transitionFailureKey.set(null);
    if (reasonRequired(action)) {
      this.pendingAction.set(action);
      this.reasonDraft.set('');
      return;
    }
    await this.runAction(action, undefined);
  }

  protected async confirmPendingAction(): Promise<void> {
    const action = this.pendingAction();
    if (action === null || this.reasonTooShort()) {
      return;
    }
    await this.runAction(action, this.reasonDraft().trim());
  }

  protected cancelPendingAction(): void {
    this.focusActionAfterCancel.set(this.pendingAction());
    this.pendingAction.set(null);
    this.reasonDraft.set('');
  }

  protected async onSave(): Promise<void> {
    this.attempted.set(true);
    if (!this.formValid()) {
      return;
    }
    this.saving.set(true);
    this.saveFailureKey.set(null);
    this.outcomeKey.set(null);
    try {
      if (this.isCreateMode()) {
        const created = await this.api.createBlogPost({
          slug: this.slug().trim(),
          title: this.title().trim(),
          bodyMarkdown: this.bodyMarkdown(),
          sourceUrl: this.sourceUrl().trim() === '' ? null : this.sourceUrl().trim(),
        });
        this.applyPost(created);
        await this.router.navigate(['/admin/blog', created.id]);
      } else {
        const current = this.post();
        if (current === null) {
          return;
        }
        const updated = await this.api.updateBlogPost(current.id, this.buildUpdatePayload(current));
        this.applyPost(updated);
        this.outcomeKey.set('admin.editor.saved');
      }
    } catch (error) {
      if (error instanceof PlatformError && error.code === 'VERSION_CONFLICT') {
        await this.refreshServerFieldsAfterConflict();
      }
      this.saveFailureKey.set(errorKey(error));
    } finally {
      this.saving.set(false);
    }
  }

  private buildUpdatePayload(current: AdminBlogPost): UpdateBlogPostInput {
    const payload: {
      slug?: string;
      title?: string;
      bodyMarkdown?: string;
      sourceUrl?: string | null;
      version: number;
    } = { version: current.version };

    if (this.canEditBodyNow()) {
      const slug = this.slug().trim();
      const title = this.title().trim();
      const body = this.bodyMarkdown();
      if (slug !== current.slug) {
        payload.slug = slug;
      }
      if (title !== current.title) {
        payload.title = title;
      }
      if (body !== current.bodyMarkdown) {
        payload.bodyMarkdown = body;
      }
    }

    if (this.canEditSourceUrlNow()) {
      const url = this.sourceUrl().trim();
      const normalized = url === '' ? null : url;
      if (normalized !== current.sourceUrl) {
        payload.sourceUrl = normalized;
      }
    }

    return payload;
  }

  private async runAction(action: LifecycleAction, reason: string | undefined): Promise<void> {
    const current = this.post();
    if (current === null) {
      return;
    }
    this.transitioning.set(true);
    this.transitionFailureKey.set(null);
    this.outcomeKey.set(null);
    try {
      if (action === 'delete') {
        await this.api.deleteBlogPost(current.id);
        await this.router.navigateByUrl('/admin/blog');
        return;
      }
      const updated = await this.api.transitionBlogPost(current.id, action, {
        expectedStatus: current.status,
        reason,
      });
      this.applyPost(updated);
      this.pendingAction.set(null);
      this.reasonDraft.set('');
      this.outcomeKey.set('admin.editor.transitionApplied');
    } catch (error) {
      if (error instanceof PlatformError && error.code === 'VERSION_CONFLICT') {
        await this.refreshServerFieldsAfterConflict();
      }
      this.transitionFailureKey.set(errorKey(error));
    } finally {
      this.transitioning.set(false);
    }
  }

  /**
   * Refreshes `post` (status, version, timestamps…) after a `VERSION_CONFLICT`
   * without touching the form signals — the text the user is looking at stays
   * exactly as they left it, because overwriting it with the server's copy
   * would silently discard whatever they had not saved yet.
   */
  private async refreshServerFieldsAfterConflict(): Promise<void> {
    const current = this.post();
    if (current === null) {
      return;
    }
    try {
      this.post.set(await this.api.getBlogPost(current.id));
    } catch {
      // The save/transition error already surfaced; leaving the stale post in
      // place here is preferable to losing the form's unsaved edits over a
      // second failed request.
    }
  }

  private async loadExisting(id: string): Promise<void> {
    this.loading.set(true);
    this.loadFailureKey.set(null);
    try {
      const loaded = await this.api.getBlogPost(id);
      this.applyPost(loaded);
      this.loadedId = id;
    } catch (error) {
      this.loadFailureKey.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Adopts a server response as the new baseline: both `post` (status,
   * version, timestamps) and the form fields, since a create or a successful
   * save can come back with server-normalized text (§7.8 sanitizing) that the
   * form should now display.
   */
  private applyPost(loaded: AdminBlogPost): void {
    this.post.set(loaded);
    this.slug.set(loaded.slug);
    this.title.set(loaded.title);
    this.bodyMarkdown.set(loaded.bodyMarkdown);
    this.sourceUrl.set(loaded.sourceUrl ?? '');
  }
}

function isAbsoluteHttpsUrl(value: string): boolean {
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}
