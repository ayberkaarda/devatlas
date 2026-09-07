import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { TimeoutError } from 'rxjs';
import { tap } from 'rxjs/operators';

import { API_BASE_URL } from '../platform/api';
import { ConnectivityService } from './connectivity.service';

/**
 * Feeds the outcome of every API request into the connectivity state.
 *
 * The distinction it draws is the whole point: a response of any status
 * proves a route to the server exists, so a `500` and a `429` both count as
 * reachable. Only a failure that produced no response says the server could
 * not be reached. Reporting a failing server as an absent network would show
 * "you are offline" to someone whose connection is fine.
 *
 * There are two ways to end up with no response, and both count. A status of
 * `0` is a connection that never opened. A deadline that expired is a server
 * that accepted the connection and then said nothing — the case a person most
 * needs told about, because from the outside it is indistinguishable from a
 * frozen application. Seeing the second one at all is why the deadline is
 * applied by an interceptor nested inside this one rather than by each
 * caller: a deadline imposed from outside the chain cancels this observer
 * instead of failing it, and the hang passes unobserved.
 *
 * Only requests to the API are observed. A request to somewhere else says
 * nothing about whether this application's server is reachable.
 */
export const connectivityInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);
  if (!request.url.startsWith(baseUrl)) {
    return next(request);
  }

  const connectivity = inject(ConnectivityService);
  return next(request).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          connectivity.reportReachable();
        }
      },
      error: (error: unknown) => {
        if (error instanceof TimeoutError) {
          connectivity.reportUnreachable();
          return;
        }
        if (!(error instanceof HttpErrorResponse)) {
          return;
        }
        if (error.status === 0) {
          connectivity.reportUnreachable();
        } else {
          connectivity.reportReachable();
        }
      },
    }),
  );
};
