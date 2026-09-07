import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { API_BASE_URL } from '../platform/api';
import { PlatformService } from '../platform/platform.service';
import { AUTH_TOKEN_DELIVERY } from './auth-models';
import { AuthSession } from './auth-session';
import { authInterceptor } from './auth.interceptor';

const BASE = 'https://api.example.test/api/v1';
const PROTECTED = `${BASE}/admin/blog/posts`;

/** Lets the promise chain inside the refresh-and-retry hop settle. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

function authResponse(accessToken: string) {
  return {
    access_token: accessToken,
    access_token_expires_at: '2026-09-05T10:15:00.000Z',
    refresh_token: null,
    refresh_token_expires_at: '2026-11-04T10:00:00.000Z',
    token_type: 'Bearer',
    user: {
      id: 'user-1',
      email: 'eda.demir@example.com',
      role: 'ADMIN',
      locale: 'en',
      theme: 'SYSTEM',
      created_at: '2026-08-11T14:02:59.117Z',
    },
  };
}

function unauthorized(code: string) {
  return {
    body: { code, message: `${code} for the test.` },
    options: { status: 401, statusText: 'Unauthorized' },
  };
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let session: AuthSession;
  let platform: FakePlatformService;

  beforeEach(async () => {
    platform = new FakePlatformService();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        { provide: AUTH_TOKEN_DELIVERY, useValue: 'COOKIE' },
        { provide: PlatformService, useValue: platform },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSession);

    const signIn = session.signIn('eda@example.com', 'pw');
    const login = controller.expectOne(`${BASE}/auth/login`);
    // The session endpoints are anonymous: the sign-in request itself must not
    // carry a bearer token.
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush(authResponse('access-1'));
    await signIn;
  });

  afterEach(() => controller.verify());

  it('carries the access token on an API request', () => {
    http.get(PROTECTED).subscribe();

    const request = controller.expectOne(PROTECTED);
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-1');
    request.flush({ items: [] });
  });

  it('leaves a request to another host alone', () => {
    http.get('https://elsewhere.test/thing').subscribe();

    const request = controller.expectOne('https://elsewhere.test/thing');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('refreshes once and retries once when the access token has expired', async () => {
    let result: unknown = null;
    http.get(PROTECTED).subscribe({ next: (value) => (result = value) });

    const first = controller.expectOne(PROTECTED);
    expect(first.request.headers.get('Authorization')).toBe('Bearer access-1');
    const expired = unauthorized('ACCESS_TOKEN_EXPIRED');
    first.flush(expired.body, expired.options);

    // Second in order, and on the cookie channel, so the browser is asked to
    // attach the credential this application never sees.
    const refresh = controller.expectOne(`${BASE}/auth/refresh`);
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush(authResponse('access-2'));
    await settle();

    // Third and last: the original request, replayed with the new token.
    const retry = controller.expectOne(PROTECTED);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush({ items: ['ok'] });
    await settle();

    expect(result).toEqual({ items: ['ok'] });
    expect(session.accessToken()).toBe('access-2');
  });

  it('treats an invalid access token the same way as an expired one', async () => {
    http.get(PROTECTED).subscribe({ error: () => undefined });

    const invalid = unauthorized('ACCESS_TOKEN_INVALID');
    controller.expectOne(PROTECTED).flush(invalid.body, invalid.options);
    controller.expectOne(`${BASE}/auth/refresh`).flush(authResponse('access-3'));
    await settle();

    const retry = controller.expectOne(PROTECTED);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-3');
    retry.flush({});
    await settle();
  });

  it('reports a second 401 instead of refreshing again', async () => {
    let failure: unknown = null;
    http.get(PROTECTED).subscribe({ error: (error: unknown) => (failure = error) });

    const expired = unauthorized('ACCESS_TOKEN_EXPIRED');
    controller.expectOne(PROTECTED).flush(expired.body, expired.options);
    controller.expectOne(`${BASE}/auth/refresh`).flush(authResponse('access-2'));
    await settle();
    controller.expectOne(PROTECTED).flush(expired.body, expired.options);
    await settle();

    // Exactly three requests were made; a fourth would be the start of a loop.
    expect(controller.match(() => true).length).toBe(0);
    expect((failure as { status: number }).status).toBe(401);
    // A failed retry says nothing about the refresh token, so the session stands.
    expect(session.user()).not.toBeNull();
  });

  it('enters local-only mode when the refresh token itself is refused', async () => {
    let failure: unknown = null;
    http.get(PROTECTED).subscribe({ error: (error: unknown) => (failure = error) });

    const expired = unauthorized('ACCESS_TOKEN_EXPIRED');
    controller.expectOne(PROTECTED).flush(expired.body, expired.options);
    const reused = unauthorized('REFRESH_TOKEN_REUSED');
    controller.expectOne(`${BASE}/auth/refresh`).flush(reused.body, reused.options);
    await settle();

    expect(session.user()).toBeNull();
    expect(session.accessToken()).toBeNull();
    // The credential is gone; what the device remembers is not. Local content
    // and local progress belong to that user, a re-login is still possible
    // from the remembered row, and the interface says so rather than wiping.
    expect(session.localOnly()).toBe(true);
    expect(session.userId()).toBe('user-1');
    expect(platform.forgottenSessionCount).toBe(0);
    // The failure is delivered rather than swallowed: the call the caller made
    // did not happen, and it has to learn that.
    expect((failure as { code: string }).code).toBe('REFRESH_TOKEN_REUSED');
  });

  it('keeps the session through a transport failure', async () => {
    let failure: unknown = null;
    http.get(PROTECTED).subscribe({ error: (error: unknown) => (failure = error) });

    controller
      .expectOne(PROTECTED)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await settle();

    // No refresh was attempted, and nothing was cleared: an unreachable server
    // says nothing about whether the credentials on the wire were good.
    expect(controller.match(() => true).length).toBe(0);
    expect(session.user()).not.toBeNull();
    expect(session.accessToken()).toBe('access-1');
    expect((failure as { status: number }).status).toBe(0);
  });
});
