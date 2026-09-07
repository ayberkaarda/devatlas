import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import type { BlogStatus } from '../core/admin/admin-models';

/**
 * Colors each status a token, not a hex/rgb value baked in here — the same
 * `--color-*` custom properties every other component consumes
 * (`frontend/src/styles/tokens.css`), so a badge changes with the palette
 * automatically in both themes instead of carrying its own copy of a color
 * decision.
 */
const STATUS_TEXT_CLASS: Record<BlogStatus, string> = {
  DRAFT: 'text-text-muted',
  PENDING_REVIEW: 'text-warning',
  PUBLISHED: 'text-success',
  REJECTED: 'text-danger',
};

/**
 * A blog post's lifecycle status, as a small labeled marker.
 *
 * The label comes from `admin.post.status.<STATUS>` — one translation key
 * per enum member rather than a formatted string built here, because the
 * value is a fixed vocabulary of four states and every locale needs its own
 * word for each of them, not a template applied to the enum name.
 */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <span class="inline-flex items-center gap-1.5 text-xs font-medium" [class]="textClass()">
      <span class="inline-block h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></span>
      {{ 'admin.post.status.' + status() | translate }}
    </span>
  `,
})
export class StatusBadge {
  readonly status = input.required<BlogStatus>();

  protected readonly textClass = computed(() => STATUS_TEXT_CLASS[this.status()]);
}
