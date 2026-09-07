import { Injectable, computed, inject, signal } from '@angular/core';

import { PlatformError } from '../platform/errors';
import type { RememberedSession } from '../platform/models';
import { PlatformService } from '../platform/platform.service';
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
 * What survives a restart is delegated to the platform, which is the only
 * layer that knows where a durable value belongs: a local store on the
 * desktop, where the refresh token arrives as a value this application holds,
 * and web storage carrying nothing but a user identifier in the browser,
 * where the token is a cookie no script can read. Neither branch appears
 * here — this class hands over the same four fields either way and lets the
 * implementation drop what it must not keep.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly api = inject(AuthApiClient);
  private readonly platform = inject(PlatformService);
  private readonly delivery = inject(AUTH_TOKEN_DELIVERY);

  private readonly currentUser = signal<SessionUser | null>(null);
  private readonly currentUserId = signal<string | null>(null);
  private readonly localOnlyState = signal(false);

  /** Held in memory only, and read by exactly one caller: the interceptor. */
  private access: string | null = null;

  /** When that token stops being accepted, as the server dated it. */
  private accessExpiresAt: string | null = null;

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

  /**
   * Who this device believes it is signed in as, which outlives knowing
   * anything else about them.
   *
   * A remembered session carries a user identifier and no profile, so a
   * desktop client that starts with no network knows whose progress rows it
   * is holding before it knows their address or their role. Everything that
   * needs an identity rather than a display reads this; the header, which
   * needs a name to show, reads `user`.
   */
  readonly userId = this.currentUserId.asReadonly();

  /**
   * True once the server has refused the stored credential itself.
   *
   * This is local-only mode: reading and recording progress keep working
   * against what is on the device, nothing local is deleted, and the
   * interface offers a way back in rather than blocking. Only the three
   * refresh-token codes get here. A transport failure never does — being
   * unable to reach the server says nothing about whether the credentials are
   * still good, and treating it as a sign-out is how an application logs
   * someone out for walking into a lift.
   */
  readonly localOnly = this.localOnlyState.asReadonly();

  readonly role = computed(() => this.currentUser()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isEditor = computed(() => this.role() === 'EDITOR');
  readonly canAdminister = computed(() => this.isAdmin() || this.isEditor());

  async signIn(email: string, password: string): Promise<void> {
    this.adopt(await this.api.login(email, password));
    await this.remember();
  }

  /**
   * Ends the session locally first, then asks the server to revoke it.
   *
   * Local state goes first because the revoke call can fail — an unreachable
   * server is the ordinary case for a desktop client — and a sign-out that
   * leaves someone signed in because the network was down is the one outcome
   * this method must never produce.
   *
   * This is also the one act that forgets what the device remembers. A
   * rejected refresh clears memory and leaves the remembered row alone,
   * because a re-login is still possible from it and the rows it keys are
   * still that person's; signing out is the deliberate opposite.
   */
  async signOut(): Promise<void> {
    const token = this.rotationToken;
    this.clearSession();
    this.currentUserId.set(null);
    this.localOnlyState.set(false);
    await this.forget();
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
   * It starts from what the device remembers, and that row is what decides
   * whether a request is made at all. A browser that has never signed anyone
   * in has nothing stored, so an anonymous visitor makes no call — without
   * that check every first page load would spend a refresh learning there was
   * never a session. On the body channel the row also carries the token to
   * present, and its absence means the same thing.
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
    this.accessExpiresAt = null;
    this.rotationToken = null;
    this.currentUser.set(null);
  }

  /**
   * Ends the credential without ending the application.
   *
   * Called when the server refuses the refresh token itself. The in-memory
   * credential goes; the remembered row, the local content and the local
   * progress all stay, and the interface shows a way to sign in again
   * alongside everything that still works.
   */
  enterLocalOnly(): void {
    const hadSession = this.currentUserId() !== null;
    this.clearSession();
    // Only a device that had a session can lose one. Without this guard a
    // stray refusal on a request nobody authenticated would raise a "sign in
    // to sync" offer in front of a visitor who never signed in, and who has
    // nothing local to sync.
    this.localOnlyState.set(hadSession);
  }

  private async attemptRestore(): Promise<void> {
    const remembered = await this.recall();
    if (remembered === null) {
      // Nobody has ever signed in here. Anonymous is a complete state, and
      // asking the server about a session that was never created is a request
      // that can only fail.
      return;
    }

    this.currentUserId.set(remembered.userId);
    this.access = remembered.accessToken;
    this.accessExpiresAt = remembered.accessTokenExpiresAt;
    this.rotationToken = remembered.refreshToken;

    if (this.delivery === 'BODY' && this.rotationToken === null) {
      // A remembered identity with no token to present. There is nothing to
      // exchange, and sending an empty body would burn the refresh rate limit
      // to learn that.
      return;
    }

    try {
      this.adopt(await this.api.refresh(this.rotationToken));
      await this.remember();
    } catch (error) {
      if (error instanceof PlatformError && REFRESH_TOKEN_FAILURES.includes(error.code)) {
        this.enterLocalOnly();
        return;
      }
      // Anything else — a timeout, a 500, no network at all — leaves the
      // adopted credential in place. The device stays as signed in as it was
      // before it tried, which is what makes a restart offline work.
    }
  }

  private async rotate(): Promise<string | null> {
    try {
      const tokens = await this.api.refresh(this.rotationToken);
      this.adopt(tokens);
      await this.remember();
      return tokens.accessToken;
    } catch (error) {
      if (error instanceof PlatformError && REFRESH_TOKEN_FAILURES.includes(error.code)) {
        this.enterLocalOnly();
      }
      throw error;
    }
  }

  private adopt(tokens: AuthTokens): void {
    this.access = tokens.accessToken;
    this.accessExpiresAt = tokens.accessTokenExpiresAt;
    if (tokens.refreshToken !== null) {
      this.rotationToken = tokens.refreshToken;
    }
    this.currentUser.set(tokens.user);
    this.currentUserId.set(tokens.user.id);
    this.localOnlyState.set(false);
  }

  /**
   * Hands the whole session to the platform, tokens included.
   *
   * No branch on the build: the web implementation writes the identifier and
   * discards the two token fields, which is where that decision belongs. A
   * store that refuses the write costs a session across the next restart and
   * nothing else, so it is not a reason to fail a sign-in that has already
   * succeeded.
   */
  private async remember(): Promise<void> {
    const user = this.currentUser();
    if (user === null) {
      return;
    }
    try {
      await this.platform.storeSession({
        userId: user.id,
        accessToken: this.access,
        refreshToken: this.rotationToken,
        accessTokenExpiresAt: this.accessExpiresAt,
      });
    } catch {
      // Remembering is best-effort, exactly as forgetting is below.
    }
  }

  private async recall(): Promise<RememberedSession | null> {
    try {
      return await this.platform.loadSession();
    } catch {
      // A store that cannot be read is indistinguishable from an empty one,
      // and the safe reading of both is that nobody is signed in.
      return null;
    }
  }

  private async forget(): Promise<void> {
    try {
      await this.platform.forgetSession();
    } catch {
      // A store that cannot be written also cannot be holding a stale row
      // that this call would have removed.
    }
  }
}
