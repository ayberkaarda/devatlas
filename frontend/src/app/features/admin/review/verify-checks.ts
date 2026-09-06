import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import type { VerifyCheck } from '../../../core/admin/admin-models';

/**
 * One row per verification-chain entry (§5.7), in the order the pipeline
 * executed them: `SOURCE_WHITELISTED`, `VERSION_CONFIRMED`, `HASH_NOT_SEEN`,
 * `CONTENT_SANITY`.
 *
 * The first failing entry is highlighted separately from the rest of the
 * list, because it is the specific reason a `REJECTED` update never became a
 * draft — scanning a short list for the first `false` is exactly the kind of
 * parsing task a reviewer should not have to do by eye.
 */
@Component({
  selector: 'app-verify-checks',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <ul class="flex flex-col gap-2">
      @for (check of checks(); track check.check) {
        <li
          class="rounded-md border p-2 text-sm"
          [class.border-danger]="check === firstFailing()"
          [class.border-border]="check !== firstFailing()"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-medium">{{ 'admin.review.check.' + check.check | translate }}</span>
            <span [class.text-success]="check.passed" [class.text-danger]="!check.passed">
              {{
                (check.passed ? 'admin.review.checkPassed' : 'admin.review.checkFailed') | translate
              }}
            </span>
          </div>
          @if (check.detail) {
            <p class="mt-1 text-text-muted">{{ check.detail }}</p>
          }
          @if (check === firstFailing()) {
            <p class="mt-1 text-xs font-medium text-danger" role="alert">
              {{ 'admin.review.firstFailingNote' | translate }}
            </p>
          }
        </li>
      }
    </ul>
  `,
})
export class VerifyChecks {
  readonly checks = input.required<readonly VerifyCheck[]>();

  /** The first failed entry in execution order, or `null` when every check passed. */
  protected readonly firstFailing = computed<VerifyCheck | null>(
    () => this.checks().find((check) => !check.passed) ?? null,
  );
}
