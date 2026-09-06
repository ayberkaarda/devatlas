import { Routes } from '@angular/router';

import { requireRole } from '../../core/auth/role.guard';

/**
 * Everything under `/admin`, nested inside the tab shell
 * (`admin-shell.page.ts`) so every screen here shares the same Posts /
 * Review / Sources navigation.
 *
 * The outer `EDITOR | ADMIN` gate lives one level up, on the `admin` route
 * in `app.routes.ts` — an anonymous visitor or a plain `USER` never reaches
 * this file. `sources*` carries its own extra `ADMIN`-only gate, because an
 * `EDITOR` is allowed into every other screen here but not into whitelist
 * source administration.
 */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin-shell.page').then((m) => m.AdminShellPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'blog' },
      {
        path: 'blog',
        loadComponent: () => import('./blog/blog-post-list.page').then((m) => m.BlogPostListPage),
      },
      {
        path: 'blog/new',
        loadComponent: () =>
          import('./blog/blog-post-editor.page').then((m) => m.BlogPostEditorPage),
      },
      {
        path: 'blog/:id',
        loadComponent: () =>
          import('./blog/blog-post-editor.page').then((m) => m.BlogPostEditorPage),
      },
      {
        path: 'blog/:id/audit',
        loadComponent: () => import('./blog/audit-log.page').then((m) => m.AuditLogPage),
      },
      {
        path: 'review',
        loadComponent: () => import('./review/review-queue.page').then((m) => m.ReviewQueuePage),
      },
      {
        path: 'review/:postId',
        loadComponent: () => import('./review/review-detail.page').then((m) => m.ReviewDetailPage),
      },
      {
        path: 'source-updates/:id',
        loadComponent: () => import('./sources/source-update.page').then((m) => m.SourceUpdatePage),
      },
      {
        path: 'sources',
        canActivate: [requireRole(['ADMIN'])],
        loadComponent: () =>
          import('./sources/whitelist-source-list.page').then((m) => m.WhitelistSourceListPage),
      },
      {
        path: 'sources/new',
        canActivate: [requireRole(['ADMIN'])],
        loadComponent: () =>
          import('./sources/whitelist-source-editor.page').then((m) => m.WhitelistSourceEditorPage),
      },
      {
        path: 'sources/:id',
        canActivate: [requireRole(['ADMIN'])],
        loadComponent: () =>
          import('./sources/whitelist-source-editor.page').then((m) => m.WhitelistSourceEditorPage),
      },
      {
        path: 'forbidden',
        loadComponent: () => import('./admin-forbidden.page').then((m) => m.AdminForbiddenPage),
      },
    ],
  },
];
