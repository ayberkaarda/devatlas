import { Component, effect, inject, input, signal } from '@angular/core';

interface BlogPost {
  readonly slug: string;
  readonly title: string;
}

abstract class PostGateway {
  abstract fetch(slug: string, signal: AbortSignal): Promise<BlogPost>;
}

/**
 * An effect whose whole reason to exist is that a fetch is not a derivation.
 *
 * The effect reads exactly one signal — the slug — so it runs again when the
 * router hands this component a different one, and for no other reason. The
 * three signals it writes are not read inside it, which is what keeps the run
 * from re-triggering itself.
 *
 * `onCleanup` runs before the next run and when the component is destroyed, so
 * the request belonging to a slug the reader has already navigated away from is
 * aborted rather than left to arrive last and win.
 */
@Component({
  selector: 'app-blog-post-page',
  template: `
    @if (loading()) {
      <p>Loading…</p>
    } @else if (failure()) {
      <p role="alert">{{ failure() }}</p>
    } @else if (post(); as loaded) {
      <h1>{{ loaded.title }}</h1>
    }
  `,
})
export class BlogPostPage {
  private readonly gateway = inject(PostGateway);

  readonly slug = input.required<string>();

  protected readonly post = signal<BlogPost | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const slug = this.slug();
      const controller = new AbortController();
      onCleanup(() => {
        controller.abort();
      });
      void this.load(slug, controller.signal);
    });
  }

  private async load(slug: string, abort: AbortSignal): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      this.post.set(await this.gateway.fetch(slug, abort));
    } catch {
      this.post.set(null);
      this.failure.set('error.BLOG_POST_NOT_FOUND');
    } finally {
      this.loading.set(false);
    }
  }
}
