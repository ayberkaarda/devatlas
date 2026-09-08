import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface Track {
  readonly slug: string;
  readonly title: string;
}

/** The error contract the server answers with: a stable code and a message. */
interface WireError {
  readonly code?: string;
  readonly message?: string;
}

/**
 * One failure type for the whole application, carrying a code rather than a
 * sentence. The interface maps the code to a translated string; a server's
 * English developer message is never what a reader sees.
 */
export class PlatformError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = 'PlatformError';
  }
}

/**
 * The translation from transport to domain, done once, at the edge.
 *
 * A status of nought is not a server answer at all — it is a request that never
 * arrived — so it becomes a network code rather than being reported as
 * whatever the last body said. Everything else takes the code the server sent.
 */
function toPlatformError(error: unknown): PlatformError {
  if (!(error instanceof HttpErrorResponse)) {
    return new PlatformError('INTERNAL_ERROR');
  }
  if (error.status === 0) {
    return new PlatformError('NETWORK_UNAVAILABLE');
  }
  const body = (error.error ?? {}) as WireError;
  return new PlatformError(body.code ?? 'INTERNAL_ERROR');
}

@Injectable({ providedIn: 'root' })
export class TrackApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  /**
   * `firstValueFrom` subscribes once and resolves with the first value.
   *
   * The subscription is what dispatches the request: an observable returned by
   * `HttpClient` and never subscribed sends nothing, and one subscribed twice
   * sends two requests.
   */
  async getTrack(slug: string): Promise<Track> {
    try {
      return await firstValueFrom(this.http.get<Track>(`${this.baseUrl}/tracks/${slug}`));
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}
