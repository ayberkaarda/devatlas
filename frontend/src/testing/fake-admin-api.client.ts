import type {
  AdminBlogListQuery,
  AdminBlogPost,
  AdminBlogPostSummary,
  AuditLogItem,
  CreateBlogPostInput,
  CreateSourceInput,
  Page,
  PageQueryInput,
  ReviewDetail,
  ReviewQueueQuery,
  SourceFetchResult,
  SourceListQuery,
  SourceUpdateDetail,
  TransitionAction,
  TransitionInput,
  UpdateBlogPostInput,
  UpdateSourceInput,
  WhitelistSource,
} from '../app/core/admin/admin-models';

type Outcome<T> =
  | { readonly kind: 'value'; readonly value: T }
  | { readonly kind: 'error'; readonly error: unknown };

/**
 * One programmable method on the fake: every call is recorded with its
 * arguments, and answers come off a one-shot queue first, falling back to a
 * standing answer once the queue is empty. Neither is set by default, so a
 * component test that calls a method nobody armed gets a clear failure
 * instead of `undefined` silently flowing into a template.
 *
 * This is hand-rolled rather than built on a test-mocking library on
 * purpose: `FakeAdminApiClient` lives under `src/testing/`, which compiles
 * as ordinary application source (`tsconfig.app.json`/`tsconfig.typecheck.json`
 * cover every file under `src/`, not only what a build's entry point
 * happens to reach), and it must not gain a dependency the rest of the
 * codebase does not already carry there.
 */
class FakeMethod<Args extends readonly unknown[], T> {
  private readonly recordedCalls: Args[] = [];
  private readonly queue: Outcome<T>[] = [];
  private standing: Outcome<T> | null = null;

  /** Every call this method received, in order, for asserting call count and arguments. */
  get calls(): readonly Args[] {
    return this.recordedCalls;
  }

  /** The arguments of the most recent call, or `undefined` if it was never called. */
  get lastArgs(): Args | undefined {
    return this.recordedCalls.at(-1);
  }

  /** Queues one answer for the next call only. */
  mockResolvedValueOnce(value: T): void {
    this.queue.push({ kind: 'value', value });
  }

  /** Queues one failure for the next call only. */
  mockRejectedValueOnce(error: unknown): void {
    this.queue.push({ kind: 'error', error });
  }

  /** Sets the answer every call gets once the one-shot queue is empty. */
  mockResolvedValue(value: T): void {
    this.standing = { kind: 'value', value };
  }

  /** Sets the failure every call gets once the one-shot queue is empty. */
  mockRejectedValue(error: unknown): void {
    this.standing = { kind: 'error', error };
  }

  /** Records the call and resolves it against the queue, then the standing answer. */
  async resolveCall(...args: Args): Promise<T> {
    this.recordedCalls.push(args);
    const outcome = this.queue.shift() ?? this.standing;
    if (outcome === null) {
      throw new Error(
        'FakeAdminApiClient method called without a configured response ' +
          '(call mockResolvedValueOnce/mockResolvedValue or the *Rejected* counterpart first).',
      );
    }
    if (outcome.kind === 'error') {
      throw outcome.error;
    }
    return outcome.value;
  }
}

/**
 * A hand-built test double standing in for `AdminApiClient` in component
 * tests.
 *
 * A screen test injects one of these with `{ provide: AdminApiClient,
 * useValue: new FakeAdminApiClient() }`. Each `AdminApiClient` method has a
 * matching `FakeMethod` here, named with the same `...Calls` suffix
 * throughout: `fake.getBlogPostCalls.mockResolvedValueOnce(post)` programs
 * the answer, `fake.getBlogPost(id)` is what the component under test
 * actually calls, and `fake.getBlogPostCalls.calls` / `.lastArgs` is what the
 * test asserts on afterwards -- rather than standing up
 * `HttpTestingController` for a component that has nothing to say about
 * transport, which is what `admin-api.client.spec.ts` already covers on its
 * own.
 */
export class FakeAdminApiClient {
  // ---- Blog -------------------------------------------------------------------
  readonly listBlogPostsCalls = new FakeMethod<[AdminBlogListQuery], Page<AdminBlogPostSummary>>();
  readonly getBlogPostCalls = new FakeMethod<[string], AdminBlogPost>();
  readonly createBlogPostCalls = new FakeMethod<[CreateBlogPostInput], AdminBlogPost>();
  readonly updateBlogPostCalls = new FakeMethod<[string, UpdateBlogPostInput], AdminBlogPost>();
  readonly deleteBlogPostCalls = new FakeMethod<[string], void>();
  readonly transitionBlogPostCalls = new FakeMethod<
    [string, TransitionAction, TransitionInput],
    AdminBlogPost
  >();
  readonly listAuditLogCalls = new FakeMethod<[string, PageQueryInput], Page<AuditLogItem>>();

  // ---- Review -----------------------------------------------------------------
  readonly listReviewQueueCalls = new FakeMethod<[ReviewQueueQuery], Page<AdminBlogPostSummary>>();
  readonly getReviewDetailCalls = new FakeMethod<[string], ReviewDetail>();
  readonly getSourceUpdateCalls = new FakeMethod<[string], SourceUpdateDetail>();

  // ---- Whitelist sources --------------------------------------------------------
  readonly listSourcesCalls = new FakeMethod<[SourceListQuery], Page<WhitelistSource>>();
  readonly getSourceCalls = new FakeMethod<[string], WhitelistSource>();
  readonly createSourceCalls = new FakeMethod<[CreateSourceInput], WhitelistSource>();
  readonly updateSourceCalls = new FakeMethod<[string, UpdateSourceInput], WhitelistSource>();
  readonly deleteSourceCalls = new FakeMethod<[string], void>();
  readonly fetchSourceNowCalls = new FakeMethod<[string], SourceFetchResult>();

  // ---- The AdminApiClient surface a component actually calls ------------------

  listBlogPosts = (query: AdminBlogListQuery): Promise<Page<AdminBlogPostSummary>> =>
    this.listBlogPostsCalls.resolveCall(query);
  getBlogPost = (id: string): Promise<AdminBlogPost> => this.getBlogPostCalls.resolveCall(id);
  createBlogPost = (input: CreateBlogPostInput): Promise<AdminBlogPost> =>
    this.createBlogPostCalls.resolveCall(input);
  updateBlogPost = (id: string, input: UpdateBlogPostInput): Promise<AdminBlogPost> =>
    this.updateBlogPostCalls.resolveCall(id, input);
  deleteBlogPost = (id: string): Promise<void> => this.deleteBlogPostCalls.resolveCall(id);
  transitionBlogPost = (
    id: string,
    action: TransitionAction,
    input: TransitionInput,
  ): Promise<AdminBlogPost> => this.transitionBlogPostCalls.resolveCall(id, action, input);
  listAuditLog = (id: string, query: PageQueryInput): Promise<Page<AuditLogItem>> =>
    this.listAuditLogCalls.resolveCall(id, query);

  listReviewQueue = (query: ReviewQueueQuery): Promise<Page<AdminBlogPostSummary>> =>
    this.listReviewQueueCalls.resolveCall(query);
  getReviewDetail = (postId: string): Promise<ReviewDetail> =>
    this.getReviewDetailCalls.resolveCall(postId);
  getSourceUpdate = (id: string): Promise<SourceUpdateDetail> =>
    this.getSourceUpdateCalls.resolveCall(id);

  listSources = (query: SourceListQuery): Promise<Page<WhitelistSource>> =>
    this.listSourcesCalls.resolveCall(query);
  getSource = (id: string): Promise<WhitelistSource> => this.getSourceCalls.resolveCall(id);
  createSource = (input: CreateSourceInput): Promise<WhitelistSource> =>
    this.createSourceCalls.resolveCall(input);
  updateSource = (id: string, input: UpdateSourceInput): Promise<WhitelistSource> =>
    this.updateSourceCalls.resolveCall(id, input);
  deleteSource = (id: string): Promise<void> => this.deleteSourceCalls.resolveCall(id);
  fetchSourceNow = (id: string): Promise<SourceFetchResult> =>
    this.fetchSourceNowCalls.resolveCall(id);
}
