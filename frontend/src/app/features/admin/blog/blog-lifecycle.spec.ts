import type { AdminBlogPost } from '../../../core/admin/admin-models';
import {
  availableActions,
  canEditBody,
  canEditSourceUrl,
  reasonRequired,
  type LifecycleAction,
} from './blog-lifecycle';

/** A minimal, fully-specified post; each test overrides only what it needs. */
function post(overrides: Partial<AdminBlogPost>): AdminBlogPost {
  return {
    id: 'post-1',
    slug: 'a-post',
    title: 'A Post',
    bodyMarkdown: 'Body.',
    status: 'DRAFT',
    source: 'MANUAL',
    sourceUrl: null,
    sourceUpdateId: null,
    publishedAt: null,
    createdBy: 'user-1',
    createdAt: '2026-09-01T00:00:00.000Z',
    updatedAt: '2026-09-01T00:00:00.000Z',
    version: 0,
    ...overrides,
  };
}

describe('availableActions', () => {
  it('offers submit and delete on a DRAFT manual post for an editor', () => {
    const actions = availableActions(post({ status: 'DRAFT', source: 'MANUAL' }), 'EDITOR');
    expect(actions).toEqual<LifecycleAction[]>(['submit', 'publish', 'delete']);
  });

  it('offers submit from REJECTED, the other status submit is legal from', () => {
    const actions = availableActions(post({ status: 'REJECTED', source: 'MANUAL' }), 'EDITOR');
    expect(actions).toContain('submit');
  });

  it('does not offer submit from PENDING_REVIEW', () => {
    const actions = availableActions(post({ status: 'PENDING_REVIEW' }), 'ADMIN');
    expect(actions).not.toContain('submit');
  });

  it('hides approve and reject from an editor on a PENDING_REVIEW post', () => {
    const actions = availableActions(post({ status: 'PENDING_REVIEW' }), 'EDITOR');
    expect(actions).not.toContain('approve');
    expect(actions).not.toContain('reject');
  });

  it('offers approve and reject to an admin on a PENDING_REVIEW post', () => {
    const actions = availableActions(post({ status: 'PENDING_REVIEW' }), 'ADMIN');
    expect(actions).toContain('approve');
    expect(actions).toContain('reject');
  });

  it('hides unpublish from an editor on a PUBLISHED post', () => {
    const actions = availableActions(post({ status: 'PUBLISHED' }), 'EDITOR');
    expect(actions).not.toContain('unpublish');
  });

  it('offers unpublish to an admin on a PUBLISHED post', () => {
    const actions = availableActions(post({ status: 'PUBLISHED' }), 'ADMIN');
    expect(actions).toContain('unpublish');
  });

  it('offers publish on a MANUAL post in DRAFT or PENDING_REVIEW, to either role', () => {
    expect(availableActions(post({ status: 'DRAFT', source: 'MANUAL' }), 'EDITOR')).toContain(
      'publish',
    );
    expect(
      availableActions(post({ status: 'PENDING_REVIEW', source: 'MANUAL' }), 'ADMIN'),
    ).toContain('publish');
  });

  it('never offers publish on an AUTO post, admin included', () => {
    const actions = availableActions(post({ status: 'PENDING_REVIEW', source: 'AUTO' }), 'ADMIN');
    expect(actions).not.toContain('publish');
  });

  it('never offers delete on a PUBLISHED post', () => {
    const actions = availableActions(post({ status: 'PUBLISHED' }), 'ADMIN');
    expect(actions).not.toContain('delete');
  });

  it('offers delete on every non-PUBLISHED status', () => {
    for (const status of ['DRAFT', 'PENDING_REVIEW', 'REJECTED'] as const) {
      expect(availableActions(post({ status }), 'EDITOR')).toContain('delete');
    }
  });

  it('offers nothing but delete on a REJECTED AUTO post reviewed by an editor other than submit', () => {
    const actions = availableActions(post({ status: 'REJECTED', source: 'AUTO' }), 'EDITOR');
    expect(actions).toEqual<LifecycleAction[]>(['submit', 'delete']);
  });
});

describe('canEditBody', () => {
  it('is false for an editor on an AUTO post', () => {
    expect(canEditBody(post({ source: 'AUTO' }), 'EDITOR')).toBe(false);
  });

  it('is true for an admin on an AUTO post', () => {
    expect(canEditBody(post({ source: 'AUTO' }), 'ADMIN')).toBe(true);
  });

  it('is true for an editor on a MANUAL post', () => {
    expect(canEditBody(post({ source: 'MANUAL' }), 'EDITOR')).toBe(true);
  });
});

describe('canEditSourceUrl', () => {
  it('is false for an AUTO post regardless of role', () => {
    expect(canEditSourceUrl(post({ source: 'AUTO' }))).toBe(false);
  });

  it('is true for a MANUAL post', () => {
    expect(canEditSourceUrl(post({ source: 'MANUAL' }))).toBe(true);
  });
});

describe('reasonRequired', () => {
  it('is true for reject and unpublish', () => {
    expect(reasonRequired('reject')).toBe(true);
    expect(reasonRequired('unpublish')).toBe(true);
  });

  it('is false for submit, approve, publish and delete', () => {
    expect(reasonRequired('submit')).toBe(false);
    expect(reasonRequired('approve')).toBe(false);
    expect(reasonRequired('publish')).toBe(false);
    expect(reasonRequired('delete')).toBe(false);
  });
});
