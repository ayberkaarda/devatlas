import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Routes } from '@angular/router';

interface Post {
  readonly id: string;
  readonly title: string;
}

abstract class PostGateway {
  abstract load(id: string): Promise<Post>;
}

/**
 * One component behind two routes, told apart by an input that is sometimes
 * absent.
 *
 * `id` is declared optional, because `blog/new` carries no `:id` segment and
 * the router sets an input with no matching route data to `undefined`. That
 * absence is the mode switch, so it is compared with `undefined` explicitly
 * rather than tested for truthiness: an empty segment is a malformed
 * identifier, not a request to create something.
 */
@Component({
  selector: 'app-post-editor',
  template: `
    <h1>{{ isCreateMode() ? 'New post' : 'Edit post' }}</h1>
    @if (post(); as loaded) {
      <p>{{ loaded.title }}</p>
    }
  `,
})
export class PostEditorPage {
  private readonly gateway = inject(PostGateway);

  /** Absent on the `blog/new` route; present on `blog/:id`. */
  readonly id = input<string>();

  protected readonly isCreateMode = computed(() => this.id() === undefined);

  protected readonly post = signal<Post | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      if (id === undefined) {
        this.post.set(null);
        return;
      }
      void this.gateway.load(id).then((loaded) => this.post.set(loaded));
    });
  }
}

/**
 * The routes that produce both cases. Binding route data to inputs is not on by
 * default: without `withComponentInputBinding()` in the router's providers,
 * `id` stays `undefined` on both routes and the editor silently believes every
 * post is a new one.
 */
export const routes: Routes = [
  { path: 'blog/new', component: PostEditorPage },
  { path: 'blog/:id', component: PostEditorPage },
];
