import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { API_BASE_URL } from '../platform/api';
import type { WireError } from '../platform/rest-wire';
import { ACCESS_TOKEN_FAILURES, REFRESH_TOKEN_FAILURES } from './auth-models';
import { AuthSession } from './auth-session';

/**
 * The session endpoints, which carry no bearer token and are never retried.
 *
 * Refresh in particular ignores the `Authorization` header entirely, and
 * sign-out must work for the account that most needs it: one whose access
 * token expired while the machine was offline. Attaching a token here would
 * be noise at best; retrying a failed sign-in would be a second password
 * attempt nobody asked for.
 */
const SESSION_PATHS: readonly string[] = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
];

function authorize(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token === null
    ? request
    : request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Reads the error code out of a failed response.
 *
 * A transport failure has no body and therefore no code, and it is reported as
 * such rather than guessed at: a connection that never reached the server says
 * nothing about whether the credentials on it were good.
 */
function codeOf(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.status === 0) {
    return null;
  }
  const body = (error.error ?? {}) as WireError;
  return body.code ?? null;
}

/**
 * Attaches the access token, and replaces it exactly once when it has expired.
 *
 * The three rules this encodes are each one that has cost somebody a session:
 *
 * - An expired or invalid access token is refreshed once and the original
 *   request is retried once. A second failure is reported, never refreshed
 *   again — a retry loop against a request that will never succeed burns the
 *   refresh rate limit and ends in a sign-out that looks arbitrary.
 * - Only the refresh-token failures end a session. They are the server saying
 *   the credential itself is gone.
 * - A transport failure ends nothing. Being unreachable is the normal state of
 *   a desktop client, and treating it as a sign-out would delete a working
 *   session because a laptop lid closed.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);
  const session = inject(AuthSession);

  if (!request.url.startsWith(baseUrl)) {
    return next(request);
  }
  const path = request.url.slice(baseUrl.length);
  if (SESSION_PATHS.some((sessionPath) => path.startsWith(sessionPath))) {
    return next(request);
  }

  return next(authorize(request, session.accessToken())).pipe(
    catchError((error: unknown) => {
      const code = codeOf(error);

      if (code !== null && REFRESH_TOKEN_FAILURES.includes(code)) {
        session.clearSession();
        return throwError(() => error);
      }
      if (code === null || !ACCESS_TOKEN_FAILURES.includes(code)) {
        return throwError(() => error);
      }

      // The retry is built inside this handler, so a failure of the retried
      // request is not seen by it again. That is what bounds the exchange to
      // one refresh and one retry, structurally rather than by a counter.
      return from(session.refreshAccessToken()).pipe(
        switchMap((token) =>
          token === null ? throwError(() => error) : next(authorize(request, token)),
        ),
      );
    }),
  );
};
