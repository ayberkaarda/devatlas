import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../platform/api';
import { connectivityInterceptor } from './connectivity.interceptor';
import { ConnectivityService } from './connectivity.service';

const BASE = 'https://api.example.test/api/v1';

describe('ConnectivityService', () => {
  let connectivity: ConnectivityService;
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([connectivityInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
      ],
    });
    connectivity = TestBed.inject(ConnectivityService);
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('starts from what the browser reports', () => {
    expect(connectivity.online()).toBe(true);
    expect(connectivity.offline()).toBe(false);
  });

  it('follows the browser saying the interface went away, and came back', () => {
    window.dispatchEvent(new Event('offline'));
    expect(connectivity.offline()).toBe(true);

    window.dispatchEvent(new Event('online'));
    expect(connectivity.offline()).toBe(false);
  });

  it('corrects a false positive from the browser with a failed request', () => {
    http.get(`${BASE}/tracks`).subscribe({ error: () => undefined });
    controller
      .expectOne(`${BASE}/tracks`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    // The browser reported an interface; the request found no route. This is
    // exactly the case a desktop webview produces, and why the browser's own
    // answer is never the only source.
    expect(connectivity.offline()).toBe(true);
  });

  it('treats a failing server as reachable, not as an absent network', () => {
    window.dispatchEvent(new Event('offline'));

    http.get(`${BASE}/tracks`).subscribe({ error: () => undefined });
    controller
      .expectOne(`${BASE}/tracks`)
      .flush(
        { code: 'INTERNAL_ERROR', message: 'Boom.' },
        { status: 500, statusText: 'Server Error' },
      );

    // A response of any status proves a route exists. Telling someone they
    // are offline while the server is failing is its own bug, and it teaches
    // them to distrust the message.
    expect(connectivity.online()).toBe(true);
  });

  it('counts a rate limit as reachable too', () => {
    window.dispatchEvent(new Event('offline'));

    http.get(`${BASE}/sync/progress`).subscribe({ error: () => undefined });
    controller
      .expectOne(`${BASE}/sync/progress`)
      .flush(
        { code: 'RATE_LIMITED', message: 'Slow down.' },
        { status: 429, statusText: 'Too Many Requests' },
      );

    expect(connectivity.online()).toBe(true);
  });

  it('recovers on the first successful request', () => {
    connectivity.reportUnreachable();

    http.get(`${BASE}/tracks`).subscribe();
    controller.expectOne(`${BASE}/tracks`).flush({ items: [] });

    expect(connectivity.online()).toBe(true);
  });

  it('ignores a request to somewhere that is not this API', () => {
    connectivity.reportUnreachable();

    http.get('https://elsewhere.test/thing').subscribe();
    controller.expectOne('https://elsewhere.test/thing').flush({});

    // Another host answering says nothing about whether this application's
    // server can be reached.
    expect(connectivity.offline()).toBe(true);
  });
});
