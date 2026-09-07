import { EMPTY, Observable } from 'rxjs';

import type {
  BatchHandle,
  BlogListQuery,
  BlogPost,
  BlogPostSummary,
  DeltaSummary,
  DownloadProgress,
  DownloadScope,
  Lesson,
  MindMap,
  Page,
  PendingProgress,
  PlatformCapabilities,
  Preferences,
  ProgressEntry,
  ProgressSyncResult,
  QueueEntry,
  RememberedSession,
  SyncBookkeeping,
  TrackDetail,
  TrackSummary,
} from '../app/core/platform/models';
import { PlatformError } from '../app/core/platform/errors';
import { PlatformService } from '../app/core/platform/platform.service';

/**
 * The platform service a component test is given.
 *
 * Component tests use this and never either real implementation: a test that
 * reached a real one would be asserting on a transport, and the whole point of
 * the abstraction is that a component cannot tell which transport it has.
 *
 * It extends the abstract class rather than implementing an interface, so a
 * member added to the contract breaks this file at compile time instead of at
 * the first test that happens to call it.
 */
export class FakePlatformService extends PlatformService {
  capabilities: PlatformCapabilities = { canDownload: false, hasLocalStore: false };

  tracks: TrackSummary[] = [];
  trackDetails = new Map<string, TrackDetail>();
  lessons = new Map<string, Lesson>();
  preferences: Preferences = { locale: 'en', theme: 'SYSTEM' };

  readonly preferenceWrites: Partial<Preferences>[] = [];
  readonly progressWrites: { lessonId: string; completed: boolean }[] = [];
  readonly enqueued: DownloadScope[] = [];
  readonly deletedScopes: DownloadScope[] = [];
  revealed = 0;

  pending: PendingProgress[] = [];
  readonly appliedResults: (readonly ProgressSyncResult[])[] = [];
  readonly absorbed: (readonly ProgressEntry[])[] = [];

  storedSession: RememberedSession | null = null;
  readonly sessionStores: RememberedSession[] = [];
  forgottenSessionCount = 0;

  syncState: SyncBookkeeping = { preferencesDirtyAt: null, lastSyncAt: null };
  readonly syncStateWrites: Partial<SyncBookkeeping>[] = [];

  blogList: Page<BlogPostSummary> = {
    items: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  };
  readonly blogListQueries: BlogListQuery[] = [];
  readonly blogPosts = new Map<string, BlogPost>();

  async listTracks(): Promise<TrackSummary[]> {
    return this.tracks;
  }

  async getTrack(trackSlug: string): Promise<TrackDetail> {
    const detail = this.trackDetails.get(trackSlug);
    if (!detail) {
      throw new Error(`No fake track for '${trackSlug}'.`);
    }
    return detail;
  }

  async getLesson(lessonSlug: string): Promise<Lesson> {
    const lesson = this.lessons.get(lessonSlug);
    if (!lesson) {
      throw new Error(`No fake lesson for '${lessonSlug}'.`);
    }
    return lesson;
  }

  async getMindMap(): Promise<MindMap> {
    throw new Error('Not used by these tests.');
  }

  async refreshLibrary(): Promise<DeltaSummary> {
    throw new Error('Not used by these tests.');
  }

  async enqueueDownload(scope: DownloadScope): Promise<BatchHandle> {
    this.enqueued.push(scope);
    return { batchId: 'batch', queuedEntities: 1, skippedEntities: 0, totalBytes: 0 };
  }

  async pauseDownloads(): Promise<void> {
    // Accepted and ignored: no test asserts on the queue.
  }

  async resumeDownloads(): Promise<void> {
    // Accepted and ignored: no test asserts on the queue.
  }

  async cancelDownload(): Promise<void> {
    // Accepted and ignored: no test asserts on the queue.
  }

  async retryDownload(): Promise<void> {
    // Accepted and ignored: no test asserts on the queue.
  }

  async queueState(): Promise<QueueEntry[]> {
    return [];
  }

  async deleteLocal(scope: DownloadScope): Promise<void> {
    this.deletedScopes.push(scope);
  }

  downloadProgress(): Observable<DownloadProgress> {
    return EMPTY;
  }

  async markProgress(lessonId: string, completed: boolean): Promise<void> {
    this.progressWrites.push({ lessonId, completed });
  }

  async listProgress(): Promise<ProgressEntry[]> {
    return [];
  }

  async pendingProgress(): Promise<PendingProgress[]> {
    return this.pending;
  }

  async applyProgressResults(results: readonly ProgressSyncResult[]): Promise<void> {
    this.appliedResults.push(results);
  }

  async absorbProgress(entries: readonly ProgressEntry[]): Promise<void> {
    this.absorbed.push(entries);
  }

  async loadSession(): Promise<RememberedSession | null> {
    return this.storedSession;
  }

  async storeSession(session: RememberedSession): Promise<void> {
    this.sessionStores.push(session);
    this.storedSession = session;
  }

  async forgetSession(): Promise<void> {
    this.forgottenSessionCount += 1;
    this.storedSession = null;
  }

  async getSyncState(): Promise<SyncBookkeeping> {
    return this.syncState;
  }

  async setSyncState(patch: Partial<SyncBookkeeping>): Promise<void> {
    this.syncStateWrites.push(patch);
    this.syncState = { ...this.syncState, ...patch };
  }

  async listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>> {
    this.blogListQueries.push(query);
    return this.blogList;
  }

  /**
   * Throws the code the public read endpoint returns for anything that is
   * not a published post, rather than a bare `Error`, so a screen's mapping
   * from a code to a message is exercised by the same path a real miss takes.
   */
  async getBlogPost(slug: string): Promise<BlogPost> {
    const post = this.blogPosts.get(slug);
    if (!post) {
      throw new PlatformError('BLOG_POST_NOT_FOUND', `No fake blog post for '${slug}'.`);
    }
    return post;
  }

  async getPreferences(): Promise<Preferences> {
    return this.preferences;
  }

  async setPreferences(patch: Partial<Preferences>): Promise<void> {
    this.preferenceWrites.push(patch);
    this.preferences = { ...this.preferences, ...patch };
  }

  async revealApplication(): Promise<void> {
    this.revealed += 1;
  }
}
