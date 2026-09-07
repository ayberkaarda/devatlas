import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { PlatformService } from '../../core/platform/platform.service';

/**
 * What the blog shows when the server could not be reached at all.
 *
 * It is deliberately not the error box. A failing server and an absent
 * network are different facts, and telling someone they are offline while
 * the server is returning errors teaches them to distrust the message; a
 * `5xx` or a rate limit therefore takes the ordinary error path and never
 * reaches this component. What lands here is a request that produced no
 * response — a connection that never opened, or one that hung past the
 * request timeout.
 *
 * The second sentence exists only where there is a local store, because
 * only there is it true. On a client that keeps a replica, the blog being
 * unreachable leaves the rest of the application working; on one that reads
 * everything live it does not, and a reassurance that is false is worse than
 * no reassurance. The component asks the capability, never the platform.
 */
@Component({
  selector: 'app-blog-offline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  template: `
    <div class="mt-10 flex flex-col items-center text-center" data-testid="blog-offline">
      <!-- A cloud struck through: what is missing is the connection, not the
           content. Drawn inline in currentColor because there is no icon
           package in this project and the mark has to follow the theme. -->
      <svg
        class="h-8 w-8 text-text-muted"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M6.5 18.5h9.2a4 4 0 0 0 .9-7.9 5.6 5.6 0 0 0-8.4-3.6" />
        <path d="M3.5 3.5l17 17" />
      </svg>

      <!--
        Which level this heading is belongs to whoever renders the component,
        not to the component. On the list screen it sits beneath that screen's
        own <h1>; on the post screen it is the only heading there is, and an
        <h2> with no <h1> above it describes a document that begins halfway
        down. Two elements rather than a dynamic tag name, because a heading
        level is not something a template can compute.
      -->
      @if (headingLevel() === 1) {
        <h1 class="mt-4 text-lg font-semibold tracking-tight">
          {{ 'blog.offline.heading' | translate }}
        </h1>
      } @else {
        <h2 class="mt-4 text-lg font-semibold tracking-tight">
          {{ 'blog.offline.heading' | translate }}
        </h2>
      }
      <p class="mt-2 max-w-prose text-sm text-text-muted">
        {{ 'blog.offline.body' | translate }}
      </p>
      @if (hasLocalStore) {
        <p class="mt-1 max-w-prose text-sm text-text-muted">
          {{ 'blog.offline.localHint' | translate }}
        </p>
      }

      <div class="mt-6 flex flex-wrap items-center justify-center gap-4">
        <button
          type="button"
          class="rounded-md border border-border-strong bg-surface-raised px-3 py-2 text-sm font-medium text-text hover:bg-surface-hover"
          data-testid="blog-offline-retry"
          (click)="retry.emit()"
        >
          {{ 'common.retry' | translate }}
        </button>
        <a class="text-sm text-accent underline-offset-2 hover:underline" routerLink="/tracks">
          {{ 'blog.offline.library' | translate }}
        </a>
      </div>
    </div>
  `,
})
export class BlogOffline {
  private readonly platform = inject(PlatformService);

  /**
   * Whether anything is held on this device. A capability, not a platform:
   * the component would learn which build it was in by asking the second
   * question, and it has no business knowing.
   */
  protected readonly hasLocalStore = this.platform.capabilities.hasLocalStore;

  /**
   * The heading level this component should render at. It defaults to 2,
   * which is right wherever the screen already has a heading of its own.
   */
  readonly headingLevel = input<1 | 2>(2);

  readonly retry = output<void>();
}
