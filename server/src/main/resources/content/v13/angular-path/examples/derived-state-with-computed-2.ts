import { Injectable, computed, signal } from '@angular/core';

type Role = 'READER' | 'EDITOR' | 'ADMIN';

interface SessionUser {
  readonly id: string;
  readonly role: Role;
}

/**
 * A chain of derivations over one piece of stored state.
 *
 * Only `currentUser` is stored. Everything below it is a question asked of that
 * value, so there is no combination of writes that can leave `canAdminister`
 * disagreeing with `role`: the disagreement has nowhere to live.
 *
 * Each link is also a name. `canAdminister` says what a caller wants to know,
 * while `role() === 'ADMIN' || role() === 'EDITOR'` repeated at four call sites
 * says how the answer happens to be worked out this month.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly currentUser = signal<SessionUser | null>(null);

  readonly user = this.currentUser.asReadonly();

  readonly role = computed<Role | null>(() => this.currentUser()?.role ?? null);

  readonly isAdmin = computed(() => this.role() === 'ADMIN');

  readonly isEditor = computed(() => this.role() === 'EDITOR');

  readonly canAdminister = computed(() => this.isAdmin() || this.isEditor());

  signIn(user: SessionUser): void {
    this.currentUser.set(user);
  }

  signOut(): void {
    this.currentUser.set(null);
  }
}
