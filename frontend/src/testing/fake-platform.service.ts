import { EMPTY, Observable } from 'rxjs';

import type {
  BatchHandle,
  BlogPost,
  BlogPostSummary,
  DeltaSummary,
  DownloadProgress,
  DownloadScope,
  Lesson,
  MindMap,
  Page,
  PlatformCapabilities,
  Preferences,
  ProgressEntry,
  QueueEntry,
  TrackDetail,
  TrackSummary,
} from '../app/core/platform/models';
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
  revealed = 0;

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

  async deleteLocal(): Promise<void> {
    // Accepted and ignored: no test asserts on the queue.
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

  async listBlogPosts(): Promise<Page<BlogPostSummary>> {
    return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  }

  async getBlogPost(): Promise<BlogPost> {
    throw new Error('Not used by these tests.');
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
