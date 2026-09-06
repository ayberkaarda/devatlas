import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../platform/api';
import { AUTH_TOKEN_DELIVERY, type Role, type TokenDelivery } from './auth-models';
import { AuthSession } from './auth-session';

const BASE = 'https://api.example.test/api/v1';

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

  function configure(delivery: TokenDelivery): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        { provide: AUTH_TOKEN_DELIVERY, useValue: delivery },
      ],
    });
    session = TestBed.inject(AuthSession);
    http = TestBed.inject(HttpTestingController);
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

  it('picks the session back up from the cookie the browser still holds', async () => {
    const promise = session.restore();

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

  it('stays anonymous when there is no session to pick up', async () => {
    const promise = session.restore();
    http
      .expectOne(`${BASE}/auth/refresh`)
      .flush(
        { code: 'REFRESH_TOKEN_INVALID', message: 'Unknown refresh token.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await promise;

    expect(session.user()).toBeNull();
  });

  it('makes no restore call on the body channel, where a restart holds nothing', async () => {
    configure('BODY');

    await session.restore();

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
