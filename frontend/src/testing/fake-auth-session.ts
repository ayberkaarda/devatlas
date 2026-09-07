import { computed, signal } from '@angular/core';

import type { Role, SessionUser } from '../app/core/auth/auth-models';
import { AuthSession } from '../app/core/auth/auth-session';

/**
 * Every member of the session a caller outside it can reach.
 *
 * Written as a mapped type so the fake below has to keep up: adding a member
 * to the real session breaks this file at compile time rather than at the
 * first test that happens to call it. The real class cannot simply be
 * subclassed — it resolves its dependencies through the injector at
 * construction, which is exactly what a test is avoiding.
 */
type PublicAuthSession = { [K in keyof AuthSession]: AuthSession[K] };

/** A signed-in account, filled in around whichever role a test asked for. */
export function fakeSessionUser(role: Role): SessionUser {
  return {
    id: `user-${role.toLowerCase()}`,
    email: `${role.toLowerCase()}@bytelore.test`,
    displayName: role.toLowerCase(),
    role,
    preferredLocale: 'en',
    theme: 'SYSTEM',
  };
}

/**
 * The session a component or guard test is given.
 *
 * It holds state and records calls; it never reaches a transport. A test that
 * wants a role sets one, and a test that wants to know whether something
 * signed the user out reads `clearCount` instead of inferring it from a
 * request that was never made.
 */
export class FakeAuthSession implements PublicAuthSession {
  readonly user = signal<SessionUser | null>(null);
  readonly userId = computed(() => this.user()?.id ?? this.rememberedUserId());
  readonly localOnly = signal(false);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isEditor = computed(() => this.role() === 'EDITOR');
  readonly canAdminister = computed(() => this.isAdmin() || this.isEditor());

  token: string | null = null;
  /** What the next refresh should hand back; null means "nothing to exchange". */
  nextAccessToken: string | null = null;

  /**
   * Who the device remembers when nobody is signed in, which is the state a
   * local-only session is in: the credential is gone, the identity behind the
   * pending progress rows is not.
   */
  readonly rememberedUserId = signal<string | null>(null);
  localOnlyCount = 0;

  readonly signIns: { email: string; password: string }[] = [];
  signOuts = 0;
  restores = 0;
  refreshes = 0;
  clearCount = 0;

  /** Signs in as the given role, or signs out when given null. */
  setRole(role: Role | null): void {
    this.user.set(role === null ? null : fakeSessionUser(role));
    this.localOnly.set(false);
  }

  async signIn(email: string, password: string): Promise<void> {
    this.signIns.push({ email, password });
  }

  async signOut(): Promise<void> {
    this.signOuts += 1;
    this.clearSession();
    this.rememberedUserId.set(null);
    this.localOnly.set(false);
  }

  async restore(): Promise<void> {
    this.restores += 1;
  }

  whenReady(): Promise<void> {
    return Promise.resolve();
  }

  accessToken(): string | null {
    return this.token;
  }

  refreshAccessToken(): Promise<string | null> {
    this.refreshes += 1;
    return Promise.resolve(this.nextAccessToken);
  }

  clearSession(): void {
    this.clearCount += 1;
    this.token = null;
    this.user.set(null);
  }

  /**
   * Ends the credential the way a refused refresh token does: the identity
   * the device remembers survives it, which is what a test asserting that
   * local content is untouched needs to see.
   */
  enterLocalOnly(): void {
    this.localOnlyCount += 1;
    this.rememberedUserId.set(this.user()?.id ?? this.rememberedUserId());
    this.clearSession();
    this.localOnly.set(true);
  }
}
