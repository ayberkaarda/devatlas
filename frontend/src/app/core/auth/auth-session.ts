import { Injectable, computed, inject, signal } from '@angular/core';

import { PlatformError } from '../platform/errors';
import { AuthApiClient, type AuthTokens } from './auth-api.client';
import { AUTH_TOKEN_DELIVERY, REFRESH_TOKEN_FAILURES, type SessionUser } from './auth-models';

/**
 * Who is signed in, and the tokens that prove it.
 *
 * The session lives here and nowhere else. Refresh tokens are single-use and
 * rotate on every exchange, so two holders of one session would invalidate
 * each other's copy and every concurrent refresh would look exactly like a
 * replayed stolen token — an intermittent, timing-dependent failure that
 * reaches users as random sign-outs. One owner removes the possibility.
 *
 * Nothing here is persisted. A restart asks for the password again on the
 * desktop; in the browser the refresh token is a cookie the server manages, so
 * a reload can restore the session without this class ever seeing the value.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly api = inject(AuthApiClient);
  private readonly delivery = inject(AUTH_TOKEN_DELIVERY);

  private readonly currentUser = signal<SessionUser | null>(null);

  /** Held in memory only, and read by exactly one caller: the interceptor. */
  private access: string | null = null;

  /**
   * The refresh token, when this build is the one holding it.
   *
   * Always null on the cookie channel — there the value exists but belongs to
   * the browser, and a null here means "ask the browser", not "no session".
   */
  private rotationToken: string | null = null;

  /** The rotation currently in flight, so a burst of 401s produces one call. */
  private rotation: Promise<string | null> | null = null;

  /** The startup restore attempt, so a guard can wait for it exactly once. */
  private restoreAttempt: Promise<void> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly role = computed(() => this.currentUser()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isEditor = computed(() => this.role() === 'EDITOR');
  readonly canAdminister = computed(() => this.isAdmin() || this.isEditor());

  async signIn(email: string, password: string): Promise<void> {
    this.adopt(await this.api.login(email, password));
  }

  /**
   * Ends the session locally first, then asks the server to revoke it.
   *
   * Local state goes first because the revoke call can fail — an unreachable
   * server is the ordinary case for a desktop client — and a sign-out that
   * leaves someone signed in because the network was down is the one outcome
   * this method must never produce.
   */
  async signOut(): Promise<void> {
    const token = this.rotationToken;
    this.clearSession();
    try {
      await this.api.logout(token);
    } catch {
      // Revocation is best-effort. The credential is already gone from this
      // process, and the token expires on its own.
    }
  }

  /**
   * Tries to pick a session back up without asking for a password.
   *
   * On the cookie channel this is a plain refresh call: if the browser still
   * holds the token the server answers with a new pair, and if it does not the
   * call fails and the application stays anonymous. On the body channel there
   * is nothing to present after a restart, so no request is made at all —
   * sending an empty body would burn the refresh rate limit to learn nothing.
   */
  async restore(): Promise<void> {
    this.restoreAttempt ??= this.attemptRestore();
    return this.restoreAttempt;
  }

  /**
   * Resolves once the startup restore has finished, or immediately if none was
   * started.
   *
   * A route guard that read the role before the restore landed would bounce a
   * signed-in reader to the sign-in screen on every reload of a deep link.
   */
  whenReady(): Promise<void> {
    return this.restoreAttempt ?? Promise.resolve();
  }

  /** The bearer value for the next request. Read only by the interceptor. */
  accessToken(): string | null {
    return this.access;
  }

  /**
   * Exchanges the refresh token for a new pair. Read only by the interceptor.
   *
   * Returns null when there is nothing to exchange, which is a fact about this
   * client rather than a failure and must not be reported as one. A rejection
   * means the server refused; only the three refresh-token codes end the
   * session, and everything else — a timeout, a 500, no network at all —
   * leaves it untouched.
   */
  refreshAccessToken(): Promise<string | null> {
    if (this.rotation !== null) {
      return this.rotation;
    }
    if (this.delivery === 'BODY' && this.rotationToken === null) {
      return Promise.resolve(null);
    }
    const attempt = this.rotate();
    this.rotation = attempt;
    void attempt.then(
      () => {
        this.rotation = null;
      },
      () => {
        this.rotation = null;
      },
    );
    return attempt;
  }

  /** Drops every trace of the session from this process. */
  clearSession(): void {
    this.access = null;
    this.rotationToken = null;
    this.currentUser.set(null);
  }

  private async attemptRestore(): Promise<void> {
    if (this.delivery === 'BODY' && this.rotationToken === null) {
      return;
    }
    try {
      this.adopt(await this.api.refresh(this.rotationToken));
    } catch {
      // No session to pick up. Anonymous is a valid state, not an error to
      // report on a screen nobody asked for.
    }
  }

  private async rotate(): Promise<string | null> {
    try {
      const tokens = await this.api.refresh(this.rotationToken);
      this.adopt(tokens);
      return tokens.accessToken;
    } catch (error) {
      if (error instanceof PlatformError && REFRESH_TOKEN_FAILURES.includes(error.code)) {
        this.clearSession();
      }
      throw error;
    }
  }

  private adopt(tokens: AuthTokens): void {
    this.access = tokens.accessToken;
    if (tokens.refreshToken !== null) {
      this.rotationToken = tokens.refreshToken;
    }
    this.currentUser.set(tokens.user);
  }
}
