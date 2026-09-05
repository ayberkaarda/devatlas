import { Routes } from '@angular/router';

/**
 * Every feature route is loaded on demand. The shell is what the first paint
 * needs; a track the user has not asked for is not.
 *
 * Content is addressed by slug throughout, because a slug is what a deep link
 * carries and what survives a lesson being moved between modules. Identifiers
 * stay inside the platform layer.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'tracks' },
  {
    path: 'tracks',
    loadComponent: () => import('./features/tracks/track-list.page').then((m) => m.TrackListPage),
  },
  {
    path: 'tracks/:trackSlug',
    loadComponent: () =>
      import('./features/tracks/track-detail.page').then((m) => m.TrackDetailPage),
  },
  {
    path: 'tracks/:trackSlug/mindmap',
    loadComponent: () => import('./features/mind-map/mind-map.page').then((m) => m.MindMapPage),
  },
  {
    path: 'tracks/:trackSlug/lessons/:lessonSlug',
    loadComponent: () => import('./features/lessons/lesson.page').then((m) => m.LessonPage),
  },
  {
    path: 'downloads',
    loadComponent: () => import('./features/downloads/downloads.page').then((m) => m.DownloadsPage),
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
