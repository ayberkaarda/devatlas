import { Routes } from '@angular/router';

/**
 * A route table read top to bottom.
 *
 * Matching is first-match-wins, so order is behaviour rather than tidiness: the
 * two-segment lesson route has to precede nothing in particular here, but the
 * wildcard has to come last or it answers for every path beneath it.
 *
 * Every screen is loaded on demand. `loadComponent` takes a function returning
 * a promise of a component, and the standard dynamic `import` is what produces
 * that promise — the module is a separate chunk the router requests when the
 * route is activated, not part of the first download.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tracks' },
  {
    path: 'tracks',
    loadComponent: () => import('./lazy-loading-routes-2').then((m) => m.TrackListPage),
  },
  {
    path: 'tracks/:trackSlug/lessons/:lessonSlug',
    loadComponent: () => import('./lazy-loading-routes-2').then((m) => m.LessonPage),
  },
  {
    // A whole section, with its own routes and its own guard, in one chunk.
    path: 'admin',
    loadChildren: () => import('./lazy-loading-routes-2').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '**',
    loadComponent: () => import('./lazy-loading-routes-2').then((m) => m.NotFoundPage),
  },
];
