import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from '../../core/auth/auth-session';
import { LanguageSwitcher } from '../../shared/language-switcher';
import { SessionMenu } from '../../shared/session-menu';
import { ThemeToggle } from '../../shared/theme-toggle';

/**
 * Where the preferences a person sets once actually live.
 *
 * The screen owns no preference logic of its own. Each control already knows
 * how to read and write the thing it names — the language switcher through
 * the locale service, the toggle through the theme service, the session menu
 * through the session — so this page is a place for them rather than a second
 * implementation of them. A copy of that logic here would be a second answer
 * to the same question, and the two would disagree the first time either
 * moved.
 *
 * The theme toggle appears here as well as in the header on purpose: the
 * header keeps it because it is flipped with the time of day, and this page
 * lists it because someone looking for "where do I change the theme" looks in
 * settings. Both render the same control, so there is no state to keep in
 * step.
 */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, LanguageSwitcher, SessionMenu, ThemeToggle],
  template: `
    <h1 class="text-2xl font-semibold tracking-tight">{{ 'settings.heading' | translate }}</h1>
    <p class="mt-2 text-text-muted">{{ 'settings.intro' | translate }}</p>

    <div class="mt-8 flex flex-col gap-4">
      <section
        class="rounded-lg border border-border bg-surface p-4"
        data-testid="settings-language"
        aria-labelledby="settings-language-heading"
      >
        <h2 id="settings-language-heading" class="text-sm font-semibold">
          {{ 'settings.language' | translate }}
        </h2>
        <div class="mt-3">
          <app-language-switcher />
        </div>
      </section>

      <section
        class="rounded-lg border border-border bg-surface p-4"
        data-testid="settings-theme"
        aria-labelledby="settings-theme-heading"
      >
        <h2 id="settings-theme-heading" class="text-sm font-semibold">
          {{ 'settings.theme' | translate }}
        </h2>
        <div class="mt-3">
          <app-theme-toggle />
        </div>
      </section>

      <section
        class="rounded-lg border border-border bg-surface p-4"
        data-testid="settings-account"
        aria-labelledby="settings-account-heading"
      >
        <h2 id="settings-account-heading" class="text-sm font-semibold">
          {{ 'settings.account' | translate }}
        </h2>
        <div class="mt-3">
          <!--
            Nobody signed in is a complete state rather than an incomplete
            one — reading and downloading need no account — so this offers a
            way in instead of reporting something missing.
          -->
          @if (session.user() === null) {
            <a
              class="inline-block rounded-md border border-border bg-surface px-3 py-2 text-sm text-text no-underline hover:bg-surface-raised"
              routerLink="/login"
              data-testid="settings-sign-in"
            >
              {{ 'nav.signIn' | translate }}
            </a>
          } @else {
            <app-session-menu />
          }
        </div>
      </section>
    </div>
  `,
})
export class SettingsPage {
  protected readonly session = inject(AuthSession);
}
