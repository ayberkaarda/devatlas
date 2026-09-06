import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { ReviewDetail, TransitionAction } from '../../../core/admin/admin-models';
import { AuthSession } from '../../../core/auth/auth-session';
import { errorKey } from '../../../core/platform/error-key';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { MarkdownView } from '../../../shared/markdown-view';
import { reasonRequired } from '../blog/blog-lifecycle';
import { DiffView } from './diff-view';
import { VerifyChecks } from './verify-checks';

/**
 * A reason shorter than this cannot be submitted at all.
 *
 * Whether a reason is required in the first place is not decided here:
 * `reasonRequired` (`blog-lifecycle.ts`) is the single source of that rule —
 * `reject`/`unpublish` require one, `approve` does not (`rest-api.md` §5.5.1,
 * `AdminBlogPostService.requireReason`) — and this screen defers to it rather
 * than keeping a second copy that could drift from the server's own check.
 * This constant only says how long a reason must be *once one is given or
 * required*.
 */
const MIN_REASON_LENGTH = 10;

/**
 * The side-by-side review screen (§5.7): the raw fetched source next to the
 * generated draft, the client-computed diff between them, the verification
 * chain, and — for an `ADMIN` — the approve/reject decision.
 *
 * `sourceUpdate` is `null` for a post that reached the queue through an
 * ordinary human "submit" rather than the ingest pipeline. That is a normal
 * state, not an error: this screen renders one panel instead of two and
 * says so, rather than treating the absence as a loading failure.
 *
 * `EDITOR` never sees the approve/reject controls (D4) — not disabled,
 * absent — because approving or rejecting is an `ADMIN`-only transition
 * (`rest-api.md` §5.5.1 / `SecurityConfig.java`). An `EDITOR` sees a note
 * explaining that instead.
 */
@Component({
  selector: 'app-review-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, MarkdownView, DiffView, VerifyChecks],
  templateUrl: './review-detail.page.html',
})
export class ReviewDetailPage {
  private readonly api = inject(AdminApiClient);
  protected readonly session = inject(AuthSession);

  readonly postId = input.required<string>();

  protected readonly detail = signal<ReviewDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  protected readonly reason = signal('');
  protected readonly submitting = signal(false);
  protected readonly actionFailure = signal<string | null>(null);

  /**
   * Whether the current reason blocks submitting `action`, per
   * `reasonRequired` — the one place that decides which actions need a
   * reason at all. An action that does not require one accepts an empty
   * field; a reason that is present but under `MIN_REASON_LENGTH` is always
   * rejected, required or not, because a note too short to read is not
   * meaningfully different from no note.
   */
  protected reasonInvalidFor(action: TransitionAction): boolean {
    const length = this.reason().trim().length;
    if (length === 0) {
      return reasonRequired(action);
    }
    return length < MIN_REASON_LENGTH;
  }

  protected readonly approveDisabled = computed(
    () => this.submitting() || this.reasonInvalidFor('approve'),
  );

  protected readonly rejectDisabled = computed(
    () => this.submitting() || this.reasonInvalidFor('reject'),
  );

  /** Shown once there is something typed that is not yet long enough. */
  protected readonly showTooShortHint = computed(() => {
    const length = this.reason().trim().length;
    return length > 0 && length < MIN_REASON_LENGTH;
  });

  constructor() {
    // Deferred to an effect, not called straight from the constructor body:
    // `postId` is a required route-bound input, and reading it before Angular
    // has applied the route binding (or, in a test, before
    // `componentRef.setInput` runs) throws rather than returning a value.
    // `effect()` defers its first run past that point.
    effect(() => {
      const postId = this.postId();
      void this.load(postId);
    });
  }

  protected retry(): void {
    void this.load(this.postId());
  }

  protected onReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
  }

  protected async approve(): Promise<void> {
    await this.transition('approve');
  }

  protected async reject(): Promise<void> {
    await this.transition('reject');
  }

  private async load(postId: string): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      this.detail.set(await this.api.getReviewDetail(postId));
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }

  private async transition(action: TransitionAction): Promise<void> {
    const current = this.detail();
    if (current === null || this.reasonInvalidFor(action)) {
      return;
    }
    const trimmedReason = this.reason().trim();
    this.submitting.set(true);
    this.actionFailure.set(null);
    try {
      const updated = await this.api.transitionBlogPost(current.post.id, action, {
        expectedStatus: current.post.status,
        // The `reason` field is omitted entirely when empty — not sent as
        // `undefined`, which would still be an own property and (at the
        // wire layer) not the same as leaving it out — so an optional-reason
        // action (e.g. `approve`) is never turned into one the server's
        // `@Size(min = 10)` on `BlogTransitionRequest.reason` rejects with
        // `VALIDATION_FAILED`. `@Size` skips `null`/absent, but not `""`.
        ...(trimmedReason.length > 0 ? { reason: trimmedReason } : {}),
      });
      this.detail.set({ ...current, post: updated });
      this.reason.set('');
    } catch (error) {
      this.actionFailure.set(errorKey(error));
    } finally {
      this.submitting.set(false);
    }
  }
}
