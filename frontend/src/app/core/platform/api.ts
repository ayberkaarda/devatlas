import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';

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
 * How long a request may hang before it is treated as a transport failure.
 *
 * A timeout is mandatory rather than nice to have: an indeterminate spinner is
 * exactly how an unreachable server looks like a frozen application.
 */
export const REQUEST_TIMEOUT_MS = 8000;

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
 */
export function toPlatformError(error: unknown): PlatformError {
  if (error instanceof PlatformError) {
    return error;
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
