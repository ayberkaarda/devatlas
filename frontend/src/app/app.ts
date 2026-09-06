import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from './core/auth/auth-session';
import { PlatformService } from './core/platform/platform.service';
import { LanguageSwitcher } from './shared/language-switcher';
import { SessionMenu } from './shared/session-menu';
import { ThemeToggle } from './shared/theme-toggle';

/**
 * The application shell: navigation, the two global controls, and the outlet
 * everything else renders into.
 *
 * It has no platform knowledge and no data of its own. Which implementation
 * answered the reads below it is decided once at startup and is not a question
 * this component — or any component — can ask. The one thing it does ask is
 * the capability that decides whether the downloads link exists at all: on
 * the web there is no queue to show, so there is no link to a screen for it,
 * rather than a link to a screen that would explain that it cannot help.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    LanguageSwitcher,
    SessionMenu,
    ThemeToggle,
  ],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly platform = inject(PlatformService);

  protected readonly canDownload = this.platform.capabilities.canDownload;
  protected readonly session = inject(AuthSession);
}
