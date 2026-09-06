import type { AdminBlogPost, BlogStatus } from '../../../core/admin/admin-models';
import type { Role } from '../../../core/auth/auth-models';

/**
 * The actions a user can see on one blog post.
 *
 * Named apart from `TransitionAction` (`core/admin/admin-models.ts`) on
 * purpose: that type is the five HTTP transitions the server exposes as
 * `POST .../{action}` endpoints. This type adds `'delete'`, because deletion
 * is a decision a person makes about a post exactly like the other five, even
 * though on the wire it is a different verb on a different path
 * (`AdminApiClient.deleteBlogPost`, not `transitionBlogPost`). Keeping the
 * two types apart under different names means a caller that imports the
 * wrong one gets a compile error instead of quietly sending `'delete'` to an
 * endpoint that has never heard of it.
 */
export type LifecycleAction = 'submit' | 'approve' | 'reject' | 'publish' | 'unpublish' | 'delete';

/**
 * Authority for every rule below: `docs/protocol/rest-api.md` §5.5.1 and
 * §7.8, cross-checked against `AdminBlogPostService` and the request matcher
 * order in `SecurityConfig` (the three routes listed there before the general
 * `/api/v1/admin/**` rule are the only admin endpoints that are `ADMIN`-only;
 * everything else on that prefix accepts `EDITOR` too).
 */
const ADMIN_ONLY_ACTIONS: ReadonlySet<LifecycleAction> = new Set([
  'approve',
  'reject',
  'unpublish',
]);

/**
 * The statuses each transition is legal from, mirroring the `requireTransition`
 * call at the top of each `AdminBlogPostService` method. `delete` has no entry
 * here — its rule is "not `PUBLISHED`", the inverse shape of every other
 * action's "must be one of these", so it is checked separately.
 */
const FROM_STATUSES: Readonly<Record<Exclude<LifecycleAction, 'delete'>, ReadonlySet<BlogStatus>>> =
  {
    submit: new Set<BlogStatus>(['DRAFT', 'REJECTED']),
    approve: new Set<BlogStatus>(['PENDING_REVIEW']),
    reject: new Set<BlogStatus>(['PENDING_REVIEW']),
    publish: new Set<BlogStatus>(['DRAFT', 'PENDING_REVIEW']),
    unpublish: new Set<BlogStatus>(['PUBLISHED']),
  };

const TRANSITIONS_IN_ORDER: readonly Exclude<LifecycleAction, 'delete'>[] = [
  'submit',
  'approve',
  'reject',
  'publish',
  'unpublish',
];

/**
 * Which actions a user with `role` can see on `post` right now.
 *
 * This is a visibility rule, not merely a "would the server accept this"
 * check: an `EDITOR` looking at a `PENDING_REVIEW` post never sees `approve`
 * or `reject` in the returned list, because those endpoints are `ADMIN`-only
 * (`SecurityConfig`) — the caller renders nothing for an action absent from
 * this list, rather than rendering it disabled, so a role never sees a
 * control for a decision it cannot make.
 */
export function availableActions(post: AdminBlogPost, role: Role): readonly LifecycleAction[] {
  const actions: LifecycleAction[] = [];

  for (const action of TRANSITIONS_IN_ORDER) {
    if (ADMIN_ONLY_ACTIONS.has(action) && role !== 'ADMIN') {
      continue;
    }
    if (action === 'publish' && post.source !== 'MANUAL') {
      // AUTO_POST_APPROVAL_REQUIRED: an automated post can only reach
      // PUBLISHED through `approve`, never through `publish`.
      continue;
    }
    if (!FROM_STATUSES[action].has(post.status)) {
      continue;
    }
    actions.push(action);
  }

  if (post.status !== 'PUBLISHED') {
    // PUBLISHED_DELETE_BLOCKED: a published post must be unpublished first.
    actions.push('delete');
  }

  return actions;
}

/**
 * Whether `role` may change `post`'s slug, title or body.
 *
 * `AdminBlogPostService.update` computes `editsBody` from slug, title and
 * body together and rejects it for an `EDITOR` on an `AUTO` post with
 * `403 AUTO_POST_NOT_EDITABLE` — despite the name, the server groups all
 * three under one permission, not just the body text, so this function (and
 * every screen built on it) treats slug and title as gated by the same rule.
 * An `ADMIN` has no such restriction, on any source.
 */
export function canEditBody(post: AdminBlogPost, role: Role): boolean {
  return !(post.source === 'AUTO' && role === 'EDITOR');
}

/**
 * Whether `sourceUrl` may be changed at all.
 *
 * `AUTO_POST_NOT_EDITABLE` applies here unconditionally — the server refuses
 * this edit for every role, `ADMIN` included, because the link is the
 * provenance of automatically generated text (§5.5.1 rule 7).
 */
export function canEditSourceUrl(post: AdminBlogPost): boolean {
  return post.source !== 'AUTO';
}

/**
 * Whether `action` requires a non-empty `reason` before the server will
 * accept it. `requireReason` in `AdminBlogPostService` is called only from
 * `reject` and `unpublish`; every other transition takes `reason` as
 * optional context stored in the audit log.
 */
export function reasonRequired(action: LifecycleAction): boolean {
  return action === 'reject' || action === 'unpublish';
}
