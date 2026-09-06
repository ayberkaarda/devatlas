import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { FakeAuthSession } from '../../../testing/fake-auth-session';
import type { Role } from './auth-models';
import { AuthSession } from './auth-session';
import { requireRole } from './role.guard';

describe('requireRole', () => {
  let session: FakeAuthSession;
  let router: Router;

  beforeEach(() => {
    session = new FakeAuthSession();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthSession, useValue: session }],
    });
    router = TestBed.inject(Router);
  });

  async function decide(roles: readonly Role[], url = '/admin/blog'): Promise<string | true> {
    const guard = requireRole(roles);
    const outcome = await TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
    return outcome === true ? true : router.serializeUrl(outcome as UrlTree);
  }

  it('sends an anonymous visitor to sign in, carrying where they were going', async () => {
    expect(await decide(['EDITOR', 'ADMIN'], '/admin/review/42')).toBe(
      '/login?returnUrl=%2Fadmin%2Freview%2F42',
    );
  });

  it('tells a reader whose role is too narrow, rather than pretending nothing is there', async () => {
    session.setRole('USER');
    // Not a 404-shaped denial: the authorization contract reports an
    // insufficient role honestly, and the interface says the same thing.
    expect(await decide(['EDITOR', 'ADMIN'])).toBe('/admin/forbidden');
  });

  it('admits an editor to the shared screens and refuses the admin-only ones', async () => {
    session.setRole('EDITOR');
    expect(await decide(['EDITOR', 'ADMIN'])).toBe(true);
    expect(await decide(['ADMIN'], '/admin/sources')).toBe('/admin/forbidden');
  });

  it('admits an admin everywhere', async () => {
    session.setRole('ADMIN');
    expect(await decide(['EDITOR', 'ADMIN'])).toBe(true);
    expect(await decide(['ADMIN'], '/admin/sources')).toBe(true);
  });
});
