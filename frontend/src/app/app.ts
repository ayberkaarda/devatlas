import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from './core/auth/auth-session';
import { DownloadStore } from './core/library/download-store';
import { ConnectivityService } from './core/net/connectivity.service';
import { PlatformService } from './core/platform/platform.service';
import { SyncStatus } from './shared/sync-status';
import { ThemeToggle } from './shared/theme-toggle';

/**
 * The application shell: navigation, the one control that belongs on every
 * screen, the outlet everything else renders into, and a status bar.
 *
 * It has no platform knowledge and no data of its own. Which implementation
 * answered the reads below it is decided once at startup and is not a question
 * this component — or any component — can ask. What it does ask are two
 * capabilities: whether there is a download queue at all, which decides
 * whether the downloads link exists rather than leading to a screen that
 * would only explain that it cannot help; and whether the device keeps a
 * local store, which decides whether the queue is worth reading once at
 * startup.
 *
 * The header holds the theme toggle and nothing else that is a preference.
 * Light and dark is flipped with the time of day; a language and an account
 * are chosen once, so both live on the settings screen where they cost
 * attention only when someone went looking for them.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, SyncStatus, ThemeToggle],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly platform = inject(PlatformService);
  private readonly store = inject(DownloadStore);

  protected readonly canDownload = this.platform.capabilities.canDownload;
  protected readonly session = inject(AuthSession);

  /**
   * Whether the server is reachable. Runtime state rather than a capability:
   * it changes second to second and it is the same fact in both builds. The
   * navigation uses it to mark the one entry whose screen is read live, and
   * for nothing else — no link is added or removed by it.
   */
  protected readonly offline = inject(ConnectivityService).offline;

  /**
   * How many queue entries have not finished. Everything that is not `DONE`
   * counts, paused and failed rows included: the badge answers "is there
   * anything left for me to deal with", and a download that failed is very
   * much left to deal with.
   */
  protected readonly activeDownloads = computed(
    () => this.store.queue().filter((entry) => entry.state !== 'DONE').length,
  );

  constructor() {
    // The queue signal is filled by an explicit read, not by the progress
    // stream on its own — the store subscribes and reconciles only once
    // something asks it for a live value. Without this call the badge would
    // stay empty until the user opened the downloads screen, which is the one
    // place they no longer need it. Reading the queue once here also starts
    // the reconciliation, so every later change arrives on its own.
    if (this.platform.capabilities.hasLocalStore) {
      void this.store.refreshQueue();
    }
  }
}
