import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { API_BASE_URL } from '../platform/api';
import { PlatformService } from '../platform/platform.service';
import { AUTH_TOKEN_DELIVERY, type Role, type TokenDelivery } from './auth-models';
import { AuthSession } from './auth-session';

const BASE = 'https://api.example.test/api/v1';

/**
 * Lets the promise chain settle.
 *
 * Both restoring and signing out read or write what the device remembers
 * before they touch the network, so the request they eventually make is one
 * turn of the event loop away from the call that started them.
 */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function authResponse(role: Role, refreshToken: string | null, accessToken = 'access-1') {
  return {
    access_token: accessToken,
    access_token_expires_at: '2026-09-05T10:15:00.000Z',
    refresh_token: refreshToken,
    refresh_token_expires_at: '2026-11-04T10:00:00.000Z',
    token_type: 'Bearer',
    user: {
      id: 'user-1',
      email: 'eda.demir@example.com',
      role,
      locale: 'tr',
      theme: 'DARK',
      created_at: '2026-08-11T14:02:59.117Z',
    },
  };
}

describe('AuthSession', () => {
  let session: AuthSession;
  let http: HttpTestingController;
  let platform: FakePlatformService;

  function configure(delivery: TokenDelivery, remembered = platformWithNoSession()): void {
    TestBed.resetTestingModule();
    platform = remembered;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        { provide: AUTH_TOKEN_DELIVERY, useValue: delivery },
        { provide: PlatformService, useValue: platform },
      ],
    });
    session = TestBed.inject(AuthSession);
    http = TestBed.inject(HttpTestingController);
  }

  function platformWithNoSession(): FakePlatformService {
    return new FakePlatformService();
  }

  /** A browser that has signed someone in before, and holds no token for it. */
  function rememberingWeb(userId: string): FakePlatformService {
    const fake = new FakePlatformService();
    fake.storedSession = {
      userId,
      accessToken: null,
      refreshToken: null,
      accessTokenExpiresAt: null,
    };
    return fake;
  }

  /** A desktop store that kept the whole pair across a restart. */
  function rememberingDesktop(refreshToken: string): FakePlatformService {
    const fake = new FakePlatformService();
    fake.storedSession = {
      userId: 'user-1',
      accessToken: 'access-stored',
      refreshToken,
      accessTokenExpiresAt: '2026-09-05T10:15:00.000Z',
    };
    return fake;
  }

  beforeEach(() => configure('COOKIE'));
  afterEach(() => http.verify());

  it('starts anonymous, with no token and no role', () => {
    expect(session.user()).toBeNull();
    expect(session.role()).toBeNull();
    expect(session.accessToken()).toBeNull();
    expect(session.isAdmin()).toBe(false);
    expect(session.canAdminister()).toBe(false);
  });

  it('signs in, and shortens the address the header will show', async () => {
    const promise = session.signIn('Eda.Demir@example.com', 'kayseri-uzun-parola-2026');

    const request = http.expectOne(`${BASE}/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'Eda.Demir@example.com',
      password: 'kayseri-uzun-parola-2026',
      token_delivery: 'COOKIE',
    });
    // The browser has to be told to keep the cookie the server is about to set.
    expect(request.request.withCredentials).toBe(true);
    request.flush(authResponse('EDITOR', null));
    await promise;

    expect(session.user()).toEqual({
      id: 'user-1',
      email: 'eda.demir@example.com',
      displayName: 'eda.demir',
      role: 'EDITOR',
      preferredLocale: 'tr',
      theme: 'DARK',
    });
    expect(session.accessToken()).toBe('access-1');
  });

  it('derives every role question from the one role it was told', async () => {
    const promise = session.signIn('admin@example.com', 'pw');
    http.expectOne(`${BASE}/auth/login`).flush(authResponse('ADMIN', null));
    await promise;

    expect(session.role()).toBe('ADMIN');
    expect(session.isAdmin()).toBe(true);
    expect(session.isEditor()).toBe(false);
    expect(session.canAdminister()).toBe(true);
  });

  it('signs out locally even though the revoke call fails', async () => {
    const signIn = session.signIn('editor@example.com', 'pw');
    http.expectOne(`${BASE}/auth/login`).flush(authResponse('EDITOR', null));
    await signIn;

    const promise = session.signOut();
    await settle();
    const request = http.expectOne(`${BASE}/auth/logout`);
    expect(request.request.method).toBe('POST');
    // An unreachable server is the ordinary case for a desktop client; it must
    // not be the reason someone stays signed in.
    request.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await promise;

    expect(session.user()).toBeNull();
    expect(session.role()).toBeNull();
    expect(session.accessToken()).toBeNull();
  });

  it('offers no local-only mode to a visitor who never signed in', () => {
    session.enterLocalOnly();

    // Only a device that had a session can lose one; a "sign in to sync"
    // offer in front of an anonymous visitor points at nothing.
    expect(session.localOnly()).toBe(false);
    expect(session.userId()).toBeNull();
  });

  it('remembers the session it just signed in with', async () => {
    const promise = session.signIn('eda@example.com', 'pw');
    http.expectOne(`${BASE}/auth/login`).flush(authResponse('USER', null));
    await promise;

    // The same four fields go to both platforms; which of them survive is the
    // implementation's decision, not this one's.
    expect(platform.storedSession).toEqual({
      userId: 'user-1',
      accessToken: 'access-1',
      refreshToken: null,
      accessTokenExpiresAt: '2026-09-05T10:15:00.000Z',
    });
  });

  it('makes no refresh call for a visitor this browser has never signed in', async () => {
    await session.restore();
    await settle();

    // Without the remembered row, every first page load by an anonymous
    // visitor would spend a request learning there was never a session.
    http.expectNone(`${BASE}/auth/refresh`);
    expect(session.user()).toBeNull();
    expect(session.userId()).toBeNull();
  });

  it('picks the session back up from what the browser remembers', async () => {
    configure('COOKIE', rememberingWeb('user-1'));

    const promise = session.restore();
    await settle();
    const request = http.expectOne(`${BASE}/auth/refresh`);
    // Nothing is sent in the body: supplying the token there while the browser
    // also attaches a cookie is refused as an ambiguous channel.
    expect(request.request.body).toEqual({});
    expect(request.request.withCredentials).toBe(true);
    request.flush(authResponse('ADMIN', null, 'access-2'));
    await promise;

    expect(session.role()).toBe('ADMIN');
    expect(session.accessToken()).toBe('access-2');
  });

  it('enters local-only mode when the remembered session is refused', async () => {
    configure('COOKIE', rememberingWeb('user-1'));

    const promise = session.restore();
    await settle();
    http
      .expectOne(`${BASE}/auth/refresh`)
      .flush(
        { code: 'REFRESH_TOKEN_INVALID', message: 'Unknown refresh token.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await promise;

    expect(session.user()).toBeNull();
    expect(session.localOnly()).toBe(true);
    // Nothing local is deleted, and the identity behind the local rows
    // survives, because a re-login as the same person has to find them.
    expect(session.userId()).toBe('user-1');
    expect(platform.forgottenSessionCount).toBe(0);
  });

  it('adopts a remembered desktop session and presents the token it holds', async () => {
    configure('BODY', rememberingDesktop('refresh-stored'));

    const promise = session.restore();
    await settle();
    const request = http.expectOne(`${BASE}/auth/refresh`);
    expect(request.request.body).toEqual({ refresh_token: 'refresh-stored' });
    request.flush(authResponse('USER', 'refresh-next', 'access-5'));
    await promise;

    expect(session.accessToken()).toBe('access-5');
    expect(platform.storedSession?.refreshToken).toBe('refresh-next');
  });

  it('opens with the credential it remembers when the server cannot be reached', async () => {
    configure('BODY', rememberingDesktop('refresh-stored'));

    const promise = session.restore();
    await settle();
    http
      .expectOne(`${BASE}/auth/refresh`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await promise;

    // Unreachable is not refused. The adopted credential stands, the device
    // knows whose progress it is holding, and this is not a sign-out.
    expect(session.userId()).toBe('user-1');
    expect(session.accessToken()).toBe('access-stored');
    expect(session.localOnly()).toBe(false);
  });

  it('forgets the remembered session on the way out', async () => {
    const signIn = session.signIn('eda@example.com', 'pw');
    http.expectOne(`${BASE}/auth/login`).flush(authResponse('USER', null));
    await signIn;

    const promise = session.signOut();
    await settle();
    http.expectOne(`${BASE}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' });
    await promise;

    expect(platform.forgottenSessionCount).toBe(1);
    expect(platform.storedSession).toBeNull();
    expect(session.userId()).toBeNull();
  });

  it('makes no restore call on the body channel, where a restart holds nothing', async () => {
    configure('BODY');

    await session.restore();
    await settle();

    http.expectNone(`${BASE}/auth/refresh`);
    expect(session.user()).toBeNull();
  });

  it('sends the token it holds in the body when that is the channel it signed in on', async () => {
    configure('BODY');

    const signIn = session.signIn('eda@example.com', 'pw');
    const login = http.expectOne(`${BASE}/auth/login`);
    expect(login.request.body).toMatchObject({ token_delivery: 'BODY' });
    expect(login.request.withCredentials).toBe(false);
    login.flush(authResponse('USER', 'refresh-1'));
    await signIn;

    const rotation = session.refreshAccessToken();
    const refresh = http.expectOne(`${BASE}/auth/refresh`);
    expect(refresh.request.body).toEqual({ refresh_token: 'refresh-1' });
    refresh.flush(authResponse('USER', 'refresh-2', 'access-9'));

    expect(await rotation).toBe('access-9');
    expect(session.accessToken()).toBe('access-9');
  });
});
