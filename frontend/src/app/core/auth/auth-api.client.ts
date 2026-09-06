import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

import { API_BASE_URL, REQUEST_TIMEOUT_MS, toPlatformError } from '../platform/api';
import { AUTH_TOKEN_DELIVERY, type Role, type SessionUser } from './auth-models';

/** The account as the sign-in response carries it. */
interface WireUserSummary {
  readonly id: string;
  readonly email: string;
  readonly role: Role;
  readonly locale: string;
  readonly theme: string;
}

/** A successful sign-in or rotation. */
interface WireAuthResponse {
  readonly access_token: string;
  readonly refresh_token: string | null;
  readonly user: WireUserSummary;
}

/**
 * One issued pair, plus who it belongs to.
 *
 * `refreshToken` is null whenever the server answered on the cookie channel:
 * the value exists, it is simply not something this application is allowed to
 * see, and the distinction matters because a null here is not an error.
 */
export interface AuthTokens {
  readonly accessToken: string;
  readonly refreshToken: string | null;
  readonly user: SessionUser;
}

/**
 * Shortens an address to the part a header can show.
 *
 * The account resource carries no display name, and a full address in a
 * navigation bar wraps on the first narrow window. The local part is what a
 * person recognises as themselves; an address with no local part is left
 * whole rather than rendered as nothing.
 */
function displayNameOf(email: string): string {
  const localPart = email.split('@')[0];
  return localPart === '' ? email : localPart;
}

function toSessionUser(user: WireUserSummary): SessionUser {
  return {
    id: user.id,
    email: user.email,
    displayName: displayNameOf(user.email),
    role: user.role,
    preferredLocale: user.locale,
    theme: user.theme,
  };
}

/**
 * The three session calls, and nothing else.
 *
 * These endpoints are anonymous — none of them carries a bearer token, and the
 * refresh endpoint ignores the header entirely — so this client is the one
 * place in the application that talks to the API without a session behind it.
 * Sign-out in particular must work for the account that most needs it: one
 * whose access token expired while the machine was offline.
 */
@Injectable({ providedIn: 'root' })
export class AuthApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly delivery = inject(AUTH_TOKEN_DELIVERY);

  /**
   * Whether the browser has to carry the credential for us.
   *
   * On the cookie channel the refresh token never passes through this code:
   * the browser stores it and attaches it, which is the entire point, and it
   * only does so when the request opts into credentials.
   */
  private get withCredentials(): boolean {
    return this.delivery === 'COOKIE';
  }

  async login(email: string, password: string): Promise<AuthTokens> {
    try {
      const response = await firstValueFrom(
        this.http
          .post<WireAuthResponse>(
            `${this.baseUrl}/auth/login`,
            { email, password, token_delivery: this.delivery },
            { withCredentials: this.withCredentials },
          )
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return {
        accessToken: response.access_token,
        refreshToken: response.refresh_token,
        user: toSessionUser(response.user),
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  /**
   * Rotates the pair.
   *
   * The token is sent in the body only when this build holds one. Supplying it
   * in the body while the browser also attaches a cookie is refused by the
   * server as an ambiguous channel, so the two are mutually exclusive by
   * construction here rather than by hoping the caller keeps them apart.
   */
  async refresh(refreshToken: string | null): Promise<AuthTokens> {
    const body = refreshToken === null ? {} : { refresh_token: refreshToken };
    try {
      const response = await firstValueFrom(
        this.http
          .post<WireAuthResponse>(`${this.baseUrl}/auth/refresh`, body, {
            withCredentials: this.withCredentials,
          })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      return {
        accessToken: response.access_token,
        refreshToken: response.refresh_token,
        user: toSessionUser(response.user),
      };
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  /**
   * Revokes the presented token and its whole family.
   *
   * The response is the same whether the token was live, already revoked or
   * never existed, so there is nothing to read back and nothing to branch on.
   */
  async logout(refreshToken: string | null): Promise<void> {
    const body = refreshToken === null ? {} : { refresh_token: refreshToken };
    try {
      await firstValueFrom(
        this.http
          .post<void>(`${this.baseUrl}/auth/logout`, body, {
            withCredentials: this.withCredentials,
          })
          .pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}
