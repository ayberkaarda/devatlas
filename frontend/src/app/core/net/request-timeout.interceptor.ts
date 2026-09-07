import { HttpContext, HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { timeout } from 'rxjs';

/**
 * How long a request may hang before it is treated as a transport failure.
 *
 * A timeout is mandatory rather than nice to have: an indeterminate spinner is
 * exactly how an unreachable server looks like a frozen application.
 */
export const REQUEST_TIMEOUT_MS = 8000;

/**
 * Per-request override of the deadline above, for the rare call that is
 * legitimately allowed to take longer.
 *
 * A context token rather than an argument threaded through every client
 * method: the deadline is a property of the request, it travels with the
 * request object into the interceptor chain, and a caller that says nothing
 * gets the general allowance.
 */
export const REQUEST_TIMEOUT = new HttpContextToken<number>(() => REQUEST_TIMEOUT_MS);

/** Builds the request context that asks for a longer deadline than the default. */
export function requestTimeout(milliseconds: number): HttpContext {
  return new HttpContext().set(REQUEST_TIMEOUT, milliseconds);
}

/**
 * Applies the request deadline in the HTTP layer instead of in each client.
 *
 * Where this sits is the whole point of it existing. `timeout` cancels its
 * source rather than pushing an error back through it, so a deadline applied
 * by a caller — on the observable `HttpClient` handed back — expires *outside*
 * the interceptor chain and unsubscribes it silently. Every interceptor that
 * watches request outcomes then sees nothing at all: the screen still gets its
 * error, but the connectivity state never learns that the server stopped
 * answering, which is precisely the failure a person most needs told about.
 *
 * Registered as the innermost interceptor, the deadline expires *inside* the
 * chain, so the interceptors wrapped around it observe the failure the way
 * they observe any other. One place owns the number, and a client is a client
 * again rather than a client that also has to remember to time out.
 */
export const requestTimeoutInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(timeout(request.context.get(REQUEST_TIMEOUT)));
