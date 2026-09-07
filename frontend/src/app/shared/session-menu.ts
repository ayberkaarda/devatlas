import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from '../core/auth/auth-session';

/**
 * Who is signed in, and the way out.
 *
 * It shows the shortened address rather than the whole one: what a person
 * checks here is *which* account they are on, not their own email address
 * read back to them. Nothing is rendered while nobody is signed in — reading
 * and downloading need no account, so an anonymous visitor is in a complete
 * state, not an incomplete one, and the screen showing this control offers a
 * way in rather than a report of something missing.
 */
@Component({
  selector: 'app-session-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    @if (session.user(); as user) {
      <div class="flex items-center gap-2">
        <span class="text-sm text-text-muted">
          {{ 'auth.signedInAs' | translate: { email: user.displayName } }}
        </span>
        <button
          type="button"
          class="rounded-md border border-border bg-surface px-3 py-2 text-sm text-text hover:bg-surface-raised"
          (click)="signOut()"
        >
          {{ 'auth.signOut' | translate }}
        </button>
      </div>
    }
  `,
})
export class SessionMenu {
  protected readonly session = inject(AuthSession);

  protected signOut(): void {
    void this.session.signOut();
  }
}
