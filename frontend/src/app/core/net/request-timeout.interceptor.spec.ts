import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TimeoutError } from 'rxjs';

import { API_BASE_URL } from '../platform/api';
import { connectivityInterceptor } from './connectivity.interceptor';
import { ConnectivityService } from './connectivity.service';
import {
  REQUEST_TIMEOUT_MS,
  requestTimeout,
  requestTimeoutInterceptor,
} from './request-timeout.interceptor';

const BASE = 'https://api.example.test/api/v1';

describe('requestTimeoutInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let connectivity: ConnectivityService;

  beforeEach(() => {
    jest.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        // The same order the application registers: connectivity wraps the
        // deadline, so an expired deadline is an error the observer sees
        // rather than a cancellation it never hears about.
        provideHttpClient(withInterceptors([connectivityInterceptor, requestTimeoutInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    connectivity = TestBed.inject(ConnectivityService);
  });

  afterEach(() => {
    // A request dropped by its own deadline is left open and cancelled, which
    // is the outcome under test rather than a request the test forgot.
    controller.verify({ ignoreCancelled: true });
    jest.useRealTimers();
  });

  it('fails a request the server never answers', () => {
    let failure: unknown = null;
    http.get(`${BASE}/tracks`).subscribe({ error: (error: unknown) => (failure = error) });
    controller.expectOne(`${BASE}/tracks`);

    jest.advanceTimersByTime(REQUEST_TIMEOUT_MS - 1);
    expect(failure).toBeNull();

    jest.advanceTimersByTime(1);
    expect(failure).toBeInstanceOf(TimeoutError);
  });

  it('reports the server unreachable when it accepts the request and never answers', () => {
    // This is the regression the deadline was moved into the chain to close.
    // A server that hangs is the case a person most needs told about, and it
    // was the one case the offline indicator stayed silent for.
    http.get(`${BASE}/tracks`).subscribe({ error: () => undefined });
    controller.expectOne(`${BASE}/tracks`);
    expect(connectivity.online()).toBe(true);

    jest.advanceTimersByTime(REQUEST_TIMEOUT_MS);

    expect(connectivity.offline()).toBe(true);
  });

  it('honours a longer deadline asked for by the request', () => {
    let failure: unknown = null;
    http
      .post(`${BASE}/admin/whitelist-sources/s1/fetch`, {}, { context: requestTimeout(60_000) })
      .subscribe({ error: (error: unknown) => (failure = error) });
    const request = controller.expectOne(`${BASE}/admin/whitelist-sources/s1/fetch`);

    jest.advanceTimersByTime(REQUEST_TIMEOUT_MS * 2);
    expect(failure).toBeNull();

    request.flush({ outcome: 'ACCEPTED' });
    expect(failure).toBeNull();
  });

  it('leaves an answered request alone once its response has arrived', () => {
    let value: unknown = null;
    http.get(`${BASE}/tracks`).subscribe((response) => (value = response));
    controller.expectOne(`${BASE}/tracks`).flush({ items: [] });

    jest.advanceTimersByTime(REQUEST_TIMEOUT_MS * 4);

    expect(value).toEqual({ items: [] });
  });
});
