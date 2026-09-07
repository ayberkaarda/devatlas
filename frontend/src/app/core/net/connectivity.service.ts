import { DOCUMENT, DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

/**
 * Whether the server is reachable right now.
 *
 * This is shared runtime state, not a platform capability: it changes second
 * to second and it behaves identically in both builds, so a capability flag
 * for it would be dead configuration that invited components to branch on
 * something that never varies between the two implementations.
 *
 * Two sources feed it, and neither is sufficient alone:
 *
 * - The browser's `online` / `offline` events, which are immediate and free
 *   but describe the presence of a network interface rather than of a route
 *   to the server. Inside a desktop webview in particular they report online
 *   for a machine that can reach nothing.
 * - The outcome of real requests. A response — any response, including a
 *   `500` or a `429` — proves a route exists; only a transport failure, one
 *   that produced no response at all, proves the opposite.
 *
 * The combination is why a false positive from the browser is short-lived:
 * the first request that fails to reach anything corrects it. It is also why
 * a server that is failing is never reported as an absent network. Saying
 * "you are offline" while the server returns errors is its own bug, and it
 * teaches people to distrust the message.
 */
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  private readonly document = inject(DOCUMENT);

  private readonly reachable = signal(true);

  /** True while the server is believed reachable. */
  readonly online = this.reachable.asReadonly();

  /** The same fact from the other side, so templates read the positive form. */
  readonly offline = computed(() => !this.reachable());

  constructor() {
    const view = this.document.defaultView;
    if (!view) {
      return;
    }

    // An unknown value is treated as online. Starting offline would show an
    // offline notice to someone who is not, and the first request corrects a
    // wrong optimistic guess within one round trip.
    this.reachable.set(view.navigator?.onLine ?? true);

    const goOffline = (): void => this.reachable.set(false);
    const goOnline = (): void => this.reachable.set(true);
    view.addEventListener('offline', goOffline);
    view.addEventListener('online', goOnline);
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('offline', goOffline);
      view.removeEventListener('online', goOnline);
    });
  }

  /**
   * Records that a request reached the server, whatever the server then said
   * about it.
   */
  reportReachable(): void {
    this.reachable.set(true);
  }

  /**
   * Records that a request produced no response at all — a connection that
   * never opened, or one that hung past the request timeout.
   */
  reportUnreachable(): void {
    this.reachable.set(false);
  }
}
