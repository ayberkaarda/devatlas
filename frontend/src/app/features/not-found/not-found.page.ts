import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  template: `
    <h1 class="text-3xl font-semibold leading-tight tracking-tight">
      {{ 'notFound.heading' | translate }}
    </h1>
    <p class="mt-2 text-text-muted">{{ 'notFound.description' | translate }}</p>
    <a
      class="mt-6 inline-block rounded-md bg-accent px-3 py-2 text-sm font-medium text-accent-contrast no-underline"
      routerLink="/tracks"
    >
      {{ 'notFound.backHome' | translate }}
    </a>
  `,
})
export class NotFoundPage {}
