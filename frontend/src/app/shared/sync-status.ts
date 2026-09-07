import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from '../core/auth/auth-session';
import { ConnectivityService } from '../core/net/connectivity.service';
import { ProgressSyncService } from '../core/sync/progress-sync.service';
import { DateTimePipe } from './date-time.pipe';

/**
 * A quiet strip that says what the application knows about its connection and
 * its last synchronisation.
 *
 * It is the shell's status bar and sits at the bottom edge, which is where a
 * desktop application keeps standing state: a band under the header would
 * push every screen down by its own height to report something a reader
 * glances at rather than acts on. Hence the top border rather than a bottom
 * one — the strip draws the line between itself and the content above it.
 *
 * It is a status layer, not a feature: it never blocks anything, it offers no
 * primary action, and it renders nothing at all when there is nothing to
 * report. Three independent facts can appear in it —
 *
 * - the server is not reachable, which is an ordinary state here rather than
 *   a fault: reading downloaded content and recording progress both keep
 *   working, and the note exists so that a blog page that cannot load is
 *   explained rather than mysterious;
 * - the stored credential was refused, which is local-only mode. Nothing
 *   local is deleted and nothing stops working; the way back in is offered
 *   as a link rather than as a modal, because a person reading a lesson has
 *   not asked to sign in;
 * - when the device keeps a local store, when progress last reached the
 *   server. That is the last time a batch was *accepted*, so an interface
 *   showing it cannot claim a failed sync succeeded.
 */
@Component({
  selector: 'app-sync-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe],
  template: `
    @if (hasSomethingToSay()) {
      <div class="border-t border-border bg-surface">
        <div
          class="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2"
          role="status"
          data-testid="sync-status"
          [attr.aria-label]="'sync.statusLabel' | translate"
        >
          @if (offline()) {
            <span
              class="inline-flex items-center gap-1.5 rounded-full bg-warning-soft px-2 py-0.5 text-xs font-medium text-warning-strong"
              data-testid="sync-offline"
              [title]="'sync.offlineHint' | translate"
            >
              <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true"></span>
              {{ 'sync.offline' | translate }}
            </span>
          }

          @if (localOnly()) {
            <a
              class="text-xs font-medium text-accent underline-offset-2 hover:underline"
              routerLink="/login"
              data-testid="sync-sign-in"
              [title]="'sync.signInToSyncHint' | translate"
            >
              {{ 'sync.signInToSync' | translate }}
            </a>
          }

          @if (lastSyncAt(); as at) {
            <span
              class="ml-auto text-xs text-text-muted tabular-nums"
              data-testid="sync-last-synchronised"
            >
              {{ 'sync.lastSynchronised' | translate: { when: at | dateTime } }}
            </span>
          }
        </div>
      </div>
    }
  `,
})
export class SyncStatus {
  private readonly connectivity = inject(ConnectivityService);
  private readonly progressSync = inject(ProgressSyncService);
  private readonly session = inject(AuthSession);

  protected readonly offline = this.connectivity.offline;
  protected readonly localOnly = this.session.localOnly;

  /**
   * Null wherever there is no local queue to synchronise, which is what keeps
   * this row off a build where "last synchronised" would mean nothing: on the
   * web a completion is written to the server as it is made.
   */
  protected readonly lastSyncAt = this.progressSync.lastSyncAt;

  protected readonly hasSomethingToSay = computed(
    () => this.offline() || this.localOnly() || this.lastSyncAt() !== null,
  );
}
