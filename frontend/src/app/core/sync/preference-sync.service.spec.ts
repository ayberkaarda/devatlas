import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FakeAuthSession } from '../../../testing/fake-auth-session';
import { FakePlatformService } from '../../../testing/fake-platform.service';
import { AuthSession } from '../auth/auth-session';
import { ConnectivityService } from '../net/connectivity.service';
import { API_BASE_URL } from '../platform/api';
import { PlatformService } from '../platform/platform.service';
import { PreferenceSyncService } from './preference-sync.service';
import { PreferenceWriter } from './preference-writer';

const BASE = 'https://api.example.test/api/v1';
const ACCOUNT = `${BASE}/auth/me`;

/** Lets the writes inside one push reach their request. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('PreferenceSyncService', () => {
  let platform: FakePlatformService;
  let session: FakeAuthSession;
  let controller: HttpTestingController;
  let connectivity: ConnectivityService;
  let writer: PreferenceWriter;

  beforeEach(() => {
    platform = new FakePlatformService();
    session = new FakeAuthSession();
    session.setRole('USER');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        { provide: PlatformService, useValue: platform },
        { provide: AuthSession, useValue: session },
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    connectivity = TestBed.inject(ConnectivityService);
    writer = TestBed.inject(PreferenceWriter);
  });

  afterEach(() => controller.verify());

  it('applies a change locally and records it before anything is sent', async () => {
    await writer.write({ theme: 'DARK' });

    expect(platform.preferences.theme).toBe('DARK');
    expect(platform.syncState.preferencesDirtyAt).not.toBeNull();
  });

  it('pushes the final state and clears the record', async () => {
    const service = TestBed.inject(PreferenceSyncService);
    await writer.write({ theme: 'DARK' });

    const flush = service.flush();
    await settle();

    const request = controller.expectOne(ACCOUNT);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ locale: 'en', theme: 'DARK' });
    // The server answers with the stored account. It is read by nobody: the
    // device is the authority on its own preferences.
    request.flush({ id: 'user-1', locale: 'fr', theme: 'LIGHT' });
    await flush;

    expect(platform.preferences).toEqual({ locale: 'en', theme: 'DARK' });
    expect(platform.syncState.preferencesDirtyAt).toBeNull();
    expect(writer.dirtyAt()).toBeNull();
  });

  it('collapses several offline changes into one push of the final state', async () => {
    const service = TestBed.inject(PreferenceSyncService);
    connectivity.reportUnreachable();

    await writer.write({ theme: 'DARK' });
    await writer.write({ locale: 'tr' });
    await writer.write({ theme: 'LIGHT' });
    await service.flush();

    // Nothing left while there was no route, and the record survived all
    // three changes rather than being replaced by a queue of them.
    controller.expectNone(ACCOUNT);
    expect(writer.dirtyAt()).not.toBeNull();

    connectivity.reportReachable();
    const flush = service.flush();
    await settle();

    const request = controller.expectOne(ACCOUNT);
    expect(request.request.body).toEqual({ locale: 'tr', theme: 'LIGHT' });
    request.flush({});
    await flush;

    expect(writer.dirtyAt()).toBeNull();
  });

  it('keeps the change outstanding when the push fails', async () => {
    const service = TestBed.inject(PreferenceSyncService);
    await writer.write({ theme: 'DARK' });

    const flush = service.flush();
    await settle();
    controller
      .expectOne(ACCOUNT)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await flush;

    expect(writer.dirtyAt()).not.toBeNull();
    expect(platform.syncState.preferencesDirtyAt).not.toBeNull();
    // A push that reached nothing is evidence about the network, and the next
    // reconnection is what tries again.
    expect(connectivity.offline()).toBe(true);
  });

  it('sends nothing while nobody is signed in', async () => {
    const service = TestBed.inject(PreferenceSyncService);
    session.setRole(null);

    await writer.write({ locale: 'de' });
    await service.flush();

    controller.expectNone(ACCOUNT);
    // The record stays, so the change travels at the next sign-in rather than
    // being lost for having been made too early.
    expect(writer.dirtyAt()).not.toBeNull();
  });

  it('sends nothing in local-only mode', async () => {
    const service = TestBed.inject(PreferenceSyncService);
    session.enterLocalOnly();

    await writer.write({ locale: 'de' });
    await service.flush();

    controller.expectNone(ACCOUNT);
    expect(platform.preferences.locale).toBe('de');
  });

  it('makes no request when there is nothing outstanding', async () => {
    const service = TestBed.inject(PreferenceSyncService);

    await service.flush();

    controller.expectNone(ACCOUNT);
  });

  it('pushes on its own once a change, a session and a route all exist', async () => {
    TestBed.inject(PreferenceSyncService);
    connectivity.reportUnreachable();
    await writer.write({ theme: 'DARK' });
    TestBed.tick();
    controller.expectNone(ACCOUNT);

    connectivity.reportReachable();
    TestBed.tick();
    await settle();

    // No caller asked for this: the change was outstanding and the route came
    // back, which is the whole trigger.
    controller.expectOne(ACCOUNT).flush({});
    await settle();
    expect(writer.dirtyAt()).toBeNull();
  });
});
