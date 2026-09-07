import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';
import { TimeoutError } from 'rxjs';

import { ActiveLocale } from '../i18n/active-locale';
import { PlatformError } from './errors';
import type { WireError } from './rest-wire';

/**
 * Absolute base of the REST API, `/api/v1` included and no trailing slash.
 * A token rather than a direct environment read so a test can point the same
 * code at a fake host without a build.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

/**
 * Adds the active interface locale to every outgoing request.
 *
 * The header is set here rather than in each caller because content locale
 * negotiation is a property of the connection, not of any one screen, and a
 * request that forgot it would silently render English inside a translated
 * page.
 *
 * It reads a dependency-free holder rather than the translation service so
 * that the interceptor does not pull the whole i18n layer — and the platform
 * service underneath it — into the construction of the first HTTP request.
 */
export const localeHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  const locale = inject(ActiveLocale).value();
  return next(
    request.clone({
      setHeaders: { 'Accept-Language': locale },
    }),
  );
};

/**
 * Turns a failed response into the one error type the interface handles.
 *
 * The server's `message` is developer-facing and is never shown to a user; the
 * `code` is what maps to a translation key. A transport-level failure has no
 * body at all, and it is reported as its own code rather than as a server
 * error, because telling someone they are offline while the server is failing
 * is its own bug and teaches them to distrust the message.
 *
 * A request that hung until the timeout expired is the same condition seen
 * from the other end: no response arrived, so nothing is known about the
 * server beyond the fact that it could not be reached in time. It maps to the
 * same code as a connection that never opened, rather than to the generic
 * failure it would otherwise fall through to.
 */
export function toPlatformError(error: unknown): PlatformError {
  if (error instanceof PlatformError) {
    return error;
  }
  if (error instanceof TimeoutError) {
    return new PlatformError('NETWORK_UNAVAILABLE', 'The server did not answer in time.');
  }
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return new PlatformError('NETWORK_UNAVAILABLE', 'The server could not be reached.', {
        url: error.url,
      });
    }
    const body = (error.error ?? {}) as WireError;
    return new PlatformError(body.code ?? 'INTERNAL_ERROR', body.message ?? error.message, {
      status: error.status,
    });
  }
  return new PlatformError(
    'INTERNAL_ERROR',
    error instanceof Error ? error.message : String(error),
  );
}
