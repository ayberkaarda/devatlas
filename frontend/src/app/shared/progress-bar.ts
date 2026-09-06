import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { progressPercent } from './progress';

/**
 * A download's completed share, as a bar.
 *
 * Purely presentational: it is given two numbers and renders them, and it
 * never reads the queue itself, so the same bar serves a single lesson, a
 * container and a whole batch.
 *
 * It accompanies the numeric readout rather than replacing it. A bar answers
 * "roughly how far along" at a glance and a byte count answers "how much is
 * left", and neither substitutes for the other.
 *
 * When the total is not known yet the bar renders empty and carries no
 * `aria-valuenow`, which is how ARIA expresses an indeterminate progress bar.
 * Filling it, or claiming a value derived from a zero total, would report
 * completion for a download that has not started.
 *
 * The fill is scaled with a transform rather than animated by width, matching
 * the application's rule of animating transform and opacity only — which is
 * also what makes the global reduced-motion switch cover it.
 */
@Component({
  selector: 'app-progress-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  host: { class: 'block' },
  template: `
    <div
      class="h-1.5 w-full overflow-hidden rounded-sm bg-border"
      role="progressbar"
      data-testid="progress-bar"
      [attr.aria-label]="labelKey() | translate"
      [attr.aria-valuemin]="0"
      [attr.aria-valuemax]="100"
      [attr.aria-valuenow]="percent()"
      [attr.aria-valuetext]="percent() === null ? null : percent() + '%'"
    >
      <div class="progress-bar-fill h-full w-full bg-accent" [style.transform]="fill()"></div>
    </div>
  `,
  styles: `
    .progress-bar-fill {
      transform-origin: left center;
      transition: transform var(--motion-base) var(--ease-standard);
    }
  `,
})
export class ProgressBar {
  readonly done = input.required<number>();
  readonly total = input.required<number>();
  /** Translation key for the bar's accessible name; every caller has one. */
  readonly labelKey = input<string>('download.progressLabel');

  protected readonly percent = computed(() => progressPercent(this.done(), this.total()));

  protected readonly fill = computed(() => `scaleX(${(this.percent() ?? 0) / 100})`);
}
