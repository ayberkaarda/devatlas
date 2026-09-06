import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { SourceUpdateDetail } from '../../../core/admin/admin-models';
import { errorKey } from '../../../core/platform/error-key';
import { DateTimePipe } from '../../../shared/date-time.pipe';
import { VerifyChecks } from '../review/verify-checks';

/** How much of `content_hash` shows inline; the full value is always available in the `title`. */
const HASH_PREVIEW_LENGTH = 12;

/**
 * `GET /admin/source-updates/{id}` (§5.7): the fetched-source provenance
 * record on its own, reachable from the review screen's "view source
 * update" link, or directly for a source update whose post has moved past
 * review.
 */
@Component({
  selector: 'app-source-update-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, DateTimePipe, VerifyChecks],
  templateUrl: './source-update.page.html',
})
export class SourceUpdatePage {
  private readonly api = inject(AdminApiClient);

  readonly id = input.required<string>();

  protected readonly detail = signal<SourceUpdateDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  /** `content_hash` is long; this is what shows inline, the full value lives in the `title`. */
  protected readonly hashPreview = computed(() => {
    const hash = this.detail()?.contentHash ?? '';
    return hash.length > HASH_PREVIEW_LENGTH ? `${hash.slice(0, HASH_PREVIEW_LENGTH)}…` : hash;
  });

  constructor() {
    // Deferred to an effect, not called straight from the constructor body:
    // `id` is a required route-bound input, and reading it before Angular has
    // applied the route binding (or, in a test, before
    // `componentRef.setInput` runs) throws rather than returning a value.
    // `effect()` defers its first run past that point.
    effect(() => {
      const id = this.id();
      void this.load(id);
    });
  }

  protected retry(): void {
    void this.load(this.id());
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      this.detail.set(await this.api.getSourceUpdate(id));
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }
}
