import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { ThemeService } from '../core/theme/theme.service';

/**
 * A two-state control over a three-value preference.
 *
 * It reports what is on screen, not what was stored: with the preference set
 * to follow the system, `aria-pressed` still answers the question a user
 * actually asked, which is whether the dark palette is currently on.
 */
@Component({
  selector: 'app-theme-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    <button
      type="button"
      class="rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text hover:bg-surface-raised"
      [attr.aria-label]="'theme.toggle' | translate"
      [attr.aria-pressed]="theme.resolved() === 'dark'"
      [title]="'theme.toggle' | translate"
      (click)="toggle()"
    >
      {{ (theme.resolved() === 'dark' ? 'theme.dark' : 'theme.light') | translate }}
    </button>
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);

  protected toggle(): void {
    void this.theme.toggle();
  }
}
