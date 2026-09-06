import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { type Role } from './auth-models';
import { AuthSession } from './auth-session';

/**
 * Admits a route to the roles that are allowed to see it.
 *
 * The two refusals are deliberately different screens. An anonymous visitor is
 * sent to sign in, carrying where they were going so the trip survives; a
 * signed-in reader whose role is too narrow is told so. Neither is answered
 * with "not found": the authorization contract reports an insufficient role
 * honestly rather than hiding that the resource exists, and an interface that
 * pretended otherwise would teach people the page was broken.
 *
 * The guard waits for the startup session restore before it decides. Without
 * that wait, reloading a deep link would read an empty session — the restore
 * is a network round trip — and bounce a signed-in reader to the sign-in
 * screen every time.
 */
export function requireRole(roles: readonly Role[]): CanActivateFn {
  return async (route, state) => {
    const session = inject(AuthSession);
    const router = inject(Router);

    await session.whenReady();

    const role = session.role();
    if (role === null) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    return roles.includes(role) ? true : router.createUrlTree(['/admin/forbidden']);
  };
}
