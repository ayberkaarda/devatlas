import { Component, computed, input } from '@angular/core';

/**
 * A presentational component whose whole job is a derivation.
 *
 * `percent` and `fill` are computed rather than assigned, so a parent that
 * changes `done` sixty times a second changes two inputs and nothing else; the
 * two derived values follow because they are questions, not fields.
 *
 * `percent` is `null` while the total is unknown. That is a deliberate third
 * state: a bar filled from a nought total would report either no progress or
 * complete progress for a transfer whose size has not arrived yet, and both
 * are inventions.
 */
@Component({
  selector: 'app-progress-bar',
  template: `
    <div
      role="progressbar"
      [attr.aria-valuemin]="0"
      [attr.aria-valuemax]="100"
      [attr.aria-valuenow]="percent()"
    >
      <div class="fill" [style.transform]="fill()"></div>
    </div>
  `,
})
export class ProgressBar {
  readonly done = input.required<number>();
  readonly total = input.required<number>();

  protected readonly percent = computed<number | null>(() => {
    const total = this.total();
    if (total <= 0) {
      return null;
    }
    return Math.min(100, Math.round((this.done() / total) * 100));
  });

  protected readonly fill = computed(() => `scaleX(${(this.percent() ?? 0) / 100})`);
}
