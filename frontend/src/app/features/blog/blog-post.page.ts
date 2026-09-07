import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { errorKey } from '../../core/platform/error-key';
import { PlatformError } from '../../core/platform/errors';
import type { BlogPost } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { DateTimePipe } from '../../shared/date-time.pipe';
import { FallbackBadge } from '../../shared/fallback-badge';
import { MarkdownView } from '../../shared/markdown-view';
import { BlogOffline } from './blog-offline';
import { sourceHost } from './blog-source';

/**
 * See the list page for why this is the one condition that means the network
 * rather than the server.
 */
function isTransportFailure(error: unknown): boolean {
  return error instanceof PlatformError && error.code === 'NETWORK_UNAVAILABLE';
}

/**
 * One published post, addressed by slug so the URL a reader shares is the
 * URL that opens the post.
 *
 * A post whose source is the ingest pipeline carries a mandatory link to
 * what it summarises, and that link is rendered above the body rather than
 * beneath it: it is how a reader checks a claim, and a footnote at the end
 * of a long post is a place to put something you would rather nobody
 * followed.
 *
 * The body is markdown, rendered by the shared view that sanitises before it
 * trusts. Nothing in this file touches `innerHTML`.
 */
@Component({
  selector: 'app-blog-post-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, DateTimePipe, FallbackBadge, MarkdownView, BlogOffline],
  templateUrl: './blog-post.page.html',
})
export class BlogPostPage {
  private readonly platform = inject(PlatformService);

  readonly slug = input.required<string>();

  protected readonly post = signal<BlogPost | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly offline = signal(false);

  /**
   * The host of the source link, or `null` where there is none. A post the
   * pipeline created always has one; a hand-written post may not, and the
   * whole panel is absent rather than empty in that case.
   */
  protected readonly host = computed(() => {
    const url = this.post()?.sourceUrl ?? null;
    return url === null ? null : sourceHost(url);
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      void this.load(slug);
    });
  }

  protected retry(): void {
    void this.load(this.slug());
  }

  private async load(slug: string): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    this.offline.set(false);
    try {
      this.post.set(await this.platform.getBlogPost(slug));
    } catch (error) {
      this.post.set(null);
      if (isTransportFailure(error)) {
        this.offline.set(true);
      } else {
        // A post that is not published does not exist on the public surface,
        // so a draft, a withdrawn post and a mistyped slug all arrive here as
        // the same not-found code and get the same answer.
        this.failure.set(errorKey(error));
      }
    } finally {
      this.loading.set(false);
    }
  }
}
