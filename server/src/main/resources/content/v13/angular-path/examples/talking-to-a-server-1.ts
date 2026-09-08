import { HttpInterceptorFn } from '@angular/common/http';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, InjectionToken, inject } from '@angular/core';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
export const ACTIVE_LOCALE = new InjectionToken<() => string>('ACTIVE_LOCALE');

/**
 * An interceptor is a function. It receives the outgoing request and the next
 * step in the chain, and it returns whatever that step returns.
 *
 * The request is not modified in place — most of `HttpRequest` is immutable —
 * so a header is added by cloning. `inject` works here because an interceptor
 * runs in the injection context of the injector that registered it.
 */
export const localeHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);
  if (!request.url.startsWith(baseUrl)) {
    return next(request);
  }
  const locale = inject(ACTIVE_LOCALE)();
  return next(request.clone({ setHeaders: { 'Accept-Language': locale } }));
};

/**
 * A deadline, placed deliberately at the end of the array.
 *
 * Interceptors run in the order they are listed, so this one is innermost: the
 * timeout applies per network attempt rather than to the whole exchange, and a
 * request that is retried after a token refresh gets a full allowance of its
 * own rather than the remains of one.
 */
export const timeoutInterceptor: HttpInterceptorFn = (request, next) => next(request);

export const appConfig: ApplicationConfig = {
  providers: [provideHttpClient(withInterceptors([localeHeaderInterceptor, timeoutInterceptor]))],
};
