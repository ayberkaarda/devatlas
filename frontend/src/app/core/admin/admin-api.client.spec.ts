import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../platform/api';
import { AdminApiClient, MANUAL_FETCH_TIMEOUT_MS } from './admin-api.client';

const BASE = 'https://api.example.test/api/v1';

function wireBlogPost(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'post-1',
    slug: 'spring-boot-released',
    title: 'Spring Boot Released',
    body_markdown: 'Body.',
    status: 'DRAFT',
    source: 'MANUAL',
    source_url: null,
    source_update_id: null,
    published_at: null,
    created_by: 'user-1',
    created_at: '2026-09-04T09:41:02.775Z',
    updated_at: '2026-09-04T09:41:02.775Z',
    version: 0,
    ...overrides,
  };
}

describe('AdminApiClient', () => {
  let client: AdminApiClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: BASE },
        AdminApiClient,
      ],
    });
    client = TestBed.inject(AdminApiClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends every blog list filter and maps the page envelope and fields to camelCase', async () => {
    const promise = client.listBlogPosts({
      status: 'PENDING_REVIEW',
      source: 'AUTO',
      q: 'spring',
      page: 1,
      size: 10,
      sort: ['created_at,desc'],
    });

    const request = http.expectOne((candidate) => candidate.url === `${BASE}/admin/blog/posts`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('status')).toBe('PENDING_REVIEW');
    expect(request.request.params.get('source')).toBe('AUTO');
    expect(request.request.params.get('q')).toBe('spring');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.getAll('sort')).toEqual(['created_at,desc']);

    request.flush({
      items: [wireBlogPost({ source: 'AUTO', status: 'PENDING_REVIEW' })],
      page: 1,
      size: 10,
      total_elements: 1,
      total_pages: 1,
    });

    const page = await promise;
    expect(page.totalElements).toBe(1);
    expect(page.items[0]).toMatchObject({
      id: 'post-1',
      bodyMarkdown: 'Body.',
      sourceUrl: null,
      sourceUpdateId: null,
      createdBy: 'user-1',
    });
  });

  it('never sends a sort parameter for the review queue, even though the caller cannot supply one', async () => {
    const promise = client.listReviewQueue({ source: 'AUTO', page: 0, size: 20 });

    const request = http.expectOne((candidate) => candidate.url === `${BASE}/admin/review-queue`);
    expect(request.request.params.has('sort')).toBe(false);
    expect(request.request.params.get('source')).toBe('AUTO');

    request.flush({ items: [], page: 0, size: 20, total_elements: 0, total_pages: 0 });
    await promise;
  });

  it.each([
    ['submit', 'submit'] as const,
    ['approve', 'approve'] as const,
    ['reject', 'reject'] as const,
    ['publish', 'publish'] as const,
    ['unpublish', 'unpublish'] as const,
  ])(
    'sends the %s transition to its own path with the expected_status/reason body',
    async (action, segment) => {
      const promise = client.transitionBlogPost('post-1', action, {
        expectedStatus: 'PENDING_REVIEW',
        reason: 'Verified against the release tag.',
      });

      const request = http.expectOne(`${BASE}/admin/blog/posts/post-1/${segment}`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        expected_status: 'PENDING_REVIEW',
        reason: 'Verified against the release tag.',
      });

      request.flush(wireBlogPost({ status: 'PUBLISHED' }));
      const post = await promise;
      expect(post.status).toBe('PUBLISHED');
    },
  );

  it('echoes the version on a blog post update', async () => {
    const promise = client.updateBlogPost('post-1', {
      title: 'New Title',
      version: 3,
    });

    const request = http.expectOne(`${BASE}/admin/blog/posts/post-1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      slug: undefined,
      title: 'New Title',
      body_markdown: undefined,
      source_url: undefined,
      version: 3,
    });

    request.flush(wireBlogPost({ title: 'New Title', version: 4 }));
    const post = await promise;
    expect(post.version).toBe(4);
  });

  it('pages the audit log and maps every field including a null actor for a machine step', async () => {
    const promise = client.listAuditLog('post-1', { page: 0, size: 20 });

    const request = http.expectOne(
      (candidate) => candidate.url === `${BASE}/admin/blog/posts/post-1/audit-log`,
    );
    request.flush({
      items: [
        {
          id: 'audit-1',
          step: 'FETCH',
          actor_user_id: null,
          from_status: null,
          to_status: null,
          reason: 'spring-blog feed item',
          occurred_at: '2026-08-20T11:58:42.310Z',
        },
      ],
      page: 0,
      size: 20,
      total_elements: 1,
      total_pages: 1,
    });

    const page = await promise;
    expect(page.items[0]).toEqual({
      id: 'audit-1',
      step: 'FETCH',
      actorUserId: null,
      fromStatus: null,
      toStatus: null,
      reason: 'spring-blog feed item',
      occurredAt: '2026-08-20T11:58:42.310Z',
    });
  });

  it('carries a null sourceUpdate through for a manually written post in the review queue', async () => {
    const promise = client.getReviewDetail('post-1');

    const request = http.expectOne(`${BASE}/admin/review-queue/post-1`);
    request.flush({ post: wireBlogPost({ status: 'PENDING_REVIEW' }), source_update: null });

    const detail = await promise;
    expect(detail.sourceUpdate).toBeNull();
    expect(detail.post.status).toBe('PENDING_REVIEW');
  });

  it('maps a populated source update, including the nested whitelist source reference', async () => {
    const promise = client.getReviewDetail('post-1');

    const request = http.expectOne(`${BASE}/admin/review-queue/post-1`);
    request.flush({
      post: wireBlogPost({ source: 'AUTO', status: 'PENDING_REVIEW' }),
      source_update: {
        id: 'update-1',
        whitelist_source: {
          id: 'source-1',
          name: 'Spring Blog',
          feed_url: 'https://spring.io/blog.atom',
        },
        version_string: '4.1.1',
        content_hash: 'abc123',
        fetched_at: '2026-08-20T11:58:42.310Z',
        verify_status: 'VERIFIED',
        verify_checks: [{ check: 'SOURCE_WHITELISTED', passed: true, detail: null }],
        raw_content: 'Spring Boot 4.1.1 has been released.',
      },
    });

    const detail = await promise;
    expect(detail.sourceUpdate).toMatchObject({
      id: 'update-1',
      versionString: '4.1.1',
      contentHash: 'abc123',
      whitelistSource: {
        id: 'source-1',
        name: 'Spring Blog',
        feedUrl: 'https://spring.io/blog.atom',
      },
    });
    expect(detail.sourceUpdate?.verifyChecks).toEqual([
      { check: 'SOURCE_WHITELISTED', passed: true, detail: null },
    ]);
  });

  it('echoes the version on a whitelist source create/patch round trip', async () => {
    const createPromise = client.createSource({
      name: 'Angular Releases',
      feedUrl: 'https://github.com/angular/angular/releases.atom',
      verifyUrlPattern: 'https://api.github.com/repos/angular/angular/releases/tags/{version}',
      enabled: true,
    });
    const createRequest = http.expectOne(`${BASE}/admin/whitelist-sources`);
    expect(createRequest.request.body).toEqual({
      name: 'Angular Releases',
      feed_url: 'https://github.com/angular/angular/releases.atom',
      verify_url_pattern: 'https://api.github.com/repos/angular/angular/releases/tags/{version}',
      enabled: true,
    });
    createRequest.flush({
      id: 'source-1',
      name: 'Angular Releases',
      feed_url: 'https://github.com/angular/angular/releases.atom',
      verify_url_pattern: 'https://api.github.com/repos/angular/angular/releases/tags/{version}',
      enabled: true,
      last_fetched_at: null,
      created_at: '2026-09-04T09:31:00.118Z',
      updated_at: '2026-09-04T09:31:00.118Z',
      version: 0,
    });
    const created = await createPromise;
    expect(created.version).toBe(0);

    const updatePromise = client.updateSource('source-1', { enabled: false, version: 0 });
    const updateRequest = http.expectOne(`${BASE}/admin/whitelist-sources/source-1`);
    expect(updateRequest.request.method).toBe('PATCH');
    expect(updateRequest.request.body).toMatchObject({ enabled: false, version: 0 });
    updateRequest.flush({
      id: 'source-1',
      name: 'Angular Releases',
      feed_url: 'https://github.com/angular/angular/releases.atom',
      verify_url_pattern: 'https://api.github.com/repos/angular/angular/releases/tags/{version}',
      enabled: false,
      last_fetched_at: null,
      created_at: '2026-09-04T09:31:00.118Z',
      updated_at: '2026-09-04T09:32:00.118Z',
      version: 1,
    });
    const updated = await updatePromise;
    expect(updated.enabled).toBe(false);
    expect(updated.version).toBe(1);
  });

  it('maps a manual fetch result, including one rejection', async () => {
    const promise = client.fetchSourceNow('source-1');

    const request = http.expectOne(`${BASE}/admin/whitelist-sources/source-1/fetch`);
    expect(request.request.method).toBe('POST');
    request.flush({
      whitelist_source_id: 'source-1',
      fetched: 12,
      created: 2,
      duplicates: 9,
      rejected: 1,
      created_source_update_ids: ['update-1', 'update-2'],
      rejections: [
        { version_string: '20.1.0-next.3', failed_check: 'VERSION_CONFIRMED', detail: 'HTTP 404' },
      ],
      duration_ms: 3184,
    });

    const result = await promise;
    expect(result.fetched).toBe(12);
    expect(result.rejections).toEqual([
      { versionString: '20.1.0-next.3', failedCheck: 'VERSION_CONFIRMED', detail: 'HTTP 404' },
    ]);
  });

  it('uses a longer timeout constant for the manual fetch than the general request timeout', () => {
    expect(MANUAL_FETCH_TIMEOUT_MS).toBe(60_000);
  });

  it('extracts the code from a server error body rather than surfacing the raw message', async () => {
    const promise = client.getBlogPost('missing');

    http
      .expectOne(`${BASE}/admin/blog/posts/missing`)
      .flush(
        { code: 'BLOG_POST_NOT_FOUND', message: "No blog post exists with id 'missing'." },
        { status: 404, statusText: 'Not Found' },
      );

    await expect(promise).rejects.toMatchObject({ code: 'BLOG_POST_NOT_FOUND' });
  });

  it('surfaces a version conflict on a whitelist source update the same way', async () => {
    const promise = client.updateSource('source-1', { version: 0 });

    http
      .expectOne(`${BASE}/admin/whitelist-sources/source-1`)
      .flush(
        { code: 'VERSION_CONFLICT', message: 'The resource was modified concurrently.' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(promise).rejects.toMatchObject({ code: 'VERSION_CONFLICT' });
  });
});
