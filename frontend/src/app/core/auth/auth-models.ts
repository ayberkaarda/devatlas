import { InjectionToken } from '@angular/core';

/**
 * Account role.
 *
 * `ADMIN` is broader than `EDITOR`, which is broader than `USER`, but nothing
 * here derives a permission from that ordering: every guarded route names the
 * roles it accepts. An ordinal comparison would silently hand a new role
 * everything a lower one had.
 */
export type Role = 'USER' | 'EDITOR' | 'ADMIN';

/**
 * The signed-in account, as every screen reads it.
 *
 * `displayName` is derived rather than transported: the account resource has
 * no display-name field, and the header needs something shorter than a full
 * address. `preferredLocale` and `theme` are the stored preferences, kept as
 * plain strings because this layer only carries them; the locale and theme
 * services own their own narrower types.
 */
export interface SessionUser {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: Role;
  readonly preferredLocale: string;
  readonly theme: string;
}

/**
 * Where the server puts the refresh token when it answers.
 *
 * `COOKIE` keeps it out of reach of any script — the browser attaches it and
 * nothing in this application can read it. `BODY` hands the value back to the
 * caller, which is the only workable channel for a client with no cookie jar
 * shared with the API.
 *
 * The choice is made once, at sign-in, and never sent on refresh or logout:
 * those answer on whichever channel the request arrived on. A refresh that
 * honoured a client-chosen channel would let injected script ask for the
 * body copy of a token the browser would attach automatically, turning a
 * cookie no script can read into one it can.
 */
export type TokenDelivery = 'COOKIE' | 'BODY';

/**
 * Which channel this build signs in on.
 *
 * Provided once, next to the platform service, from the single place that is
 * allowed to read the build target. No component and no feature injects it.
 */
export const AUTH_TOKEN_DELIVERY = new InjectionToken<TokenDelivery>('AUTH_TOKEN_DELIVERY');

/** Error codes that mean the access token needs replacing, not that the session ended. */
export const ACCESS_TOKEN_FAILURES: readonly string[] = [
  'ACCESS_TOKEN_EXPIRED',
  'ACCESS_TOKEN_INVALID',
];

/**
 * Error codes that end a session.
 *
 * These are the only conditions that clear local session state. A transport
 * failure is deliberately absent: being unable to reach the server says
 * nothing about whether the credentials are still good, and treating it as a
 * sign-out is how an application logs someone out for walking into a lift.
 */
export const REFRESH_TOKEN_FAILURES: readonly string[] = [
  'REFRESH_TOKEN_INVALID',
  'REFRESH_TOKEN_EXPIRED',
  'REFRESH_TOKEN_REUSED',
];
