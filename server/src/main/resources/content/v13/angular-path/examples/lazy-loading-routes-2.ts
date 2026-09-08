import { Component, inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

@Component({ selector: 'app-track-list', template: `<h1>Tracks</h1>` })
export class TrackListPage {}

@Component({ selector: 'app-lesson', template: `<h1>Lesson</h1>` })
export class LessonPage {}

@Component({ selector: 'app-not-found', template: `<h1>Not found</h1>` })
export class NotFoundPage {}

@Component({ selector: 'app-admin-shell', template: `<h1>Administration</h1>` })
export class AdminShellPage {}

abstract class Session {
  abstract canAdminister(): boolean;
}

/**
 * A guard as a function, built by a factory so the allowed roles are an
 * argument rather than a constant baked into the guard.
 *
 * `inject` works here because the router runs a guard inside an injection
 * context, which is the same reason a `loadComponent` function may call it.
 */
export function requireAdministrator(): CanActivateFn {
  return () => {
    const session = inject(Session);
    return session.canAdminister() ? true : inject(Router).parseUrl('/login');
  };
}

/**
 * The child routes of a lazily loaded section.
 *
 * Guarding here refuses entry after the chunk has been downloaded. Putting the
 * same guard on the parent route in the main table refuses entry before, so
 * nothing is fetched for a reader who cannot enter — at the price of a static
 * import of the guard, which is why a guard belongs in a small shared file
 * rather than in the section it protects.
 */
export const ADMIN_ROUTES: Routes = [
  { path: '', component: AdminShellPage, canActivate: [requireAdministrator()] },
];
