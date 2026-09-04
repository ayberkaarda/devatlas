import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageSwitcher } from './shared/language-switcher';
import { ThemeToggle } from './shared/theme-toggle';

/**
 * The application shell: navigation, the two global controls, and the outlet
 * everything else renders into.
 *
 * It has no platform knowledge and no data of its own. Which implementation
 * answered the reads below it is decided once at startup and is not a question
 * this component — or any component — can ask.
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
    ThemeToggle,
  ],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
